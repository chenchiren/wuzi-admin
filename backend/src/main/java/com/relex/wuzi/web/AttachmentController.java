package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Attachment;
import com.relex.wuzi.mapper.AttachmentMapper;
import com.relex.wuzi.service.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 附件管理：存储走 StorageService 抽象（local / minio），元数据入 attachment 表，业务关联 bizType/bizId。
 * 默认目录 {user.dir}/data/attachments，可用配置 wuzi.attachment.dir 覆盖。
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentMapper attachmentMapper;
    private final StorageService storageService;

    @Value("${wuzi.attachment.max-size-mb:20}")
    private long maxSizeMb;
    @Value("${wuzi.attachment.allow-ext:jpg,jpeg,png,gif,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip}")
    private String allowExt;

    @GetMapping
    public R<List<Attachment>> list(@RequestParam String bizType,
                                    @RequestParam(required = false) Long bizId) {
        LambdaQueryWrapper<Attachment> qw = new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getBizType, bizType)
                .eq(bizId != null, Attachment::getBizId, bizId)
                .eq(Attachment::getIsDeleted, 0)
                .orderByDesc(Attachment::getId);
        return R.ok(attachmentMapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public R<Attachment> get(@PathVariable Long id) {
        Attachment att = require(id);
        return R.ok(att);
    }

    /** 上传附件 */
    @RequirePerm("sys:attachment:upload")
    @PostMapping
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam String bizType,
                                         @RequestParam(required = false) Long bizId,
                                         @RequestAttribute("userId") Long userId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (bizType == null || bizType.isBlank()) {
            throw new BizException("bizType 不能为空");
        }
        long maxMb = maxMb();
        if (file.getSize() > maxMb * 1024L * 1024L) {
            throw new BizException("附件超过大小限制 " + maxMb + "MB");
        }
        String origin = safeName(file.getOriginalFilename());
        String ext = origin.contains(".") ? origin.substring(origin.lastIndexOf('.') + 1).toLowerCase() : "";
        if (ext.isEmpty() || !allowedExts().contains(ext)) {
            throw new BizException("不支持的文件类型：" + ext);
        }
        String stored = UUID.randomUUID().toString().replace("-", "") + "_" + origin;
        LocalDate today = LocalDate.now();
        String key = today.toString().replace("-", "") + "/" + stored;
        String savedPath;
        try (var in = file.getInputStream()) {
            savedPath = storageService.save(key, in, file.getContentType());
        }

        Attachment att = new Attachment();
        att.setBizType(bizType.trim());
        att.setBizId(bizId);
        att.setFileName(stored);
        att.setOriginName(origin);
        att.setFilePath(savedPath);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        att.setMd5(md5(savedPath));
        att.setUploadedBy(userId);
        att.setIsDeleted(0);
        attachmentMapper.insert(att);

        Map<String, Object> data = new HashMap<>();
        data.put("attachment", att);
        return R.ok(data);
    }

    /** 下载附件 */
    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Attachment att = require(id);
        if (!storageService.exists(att.getFilePath())) {
            throw new BizException("附件文件不存在: " + att.getFileName());
        }
        String downloadName = att.getOriginName() != null ? att.getOriginName() : att.getFileName();
        response.setContentType(att.getContentType() != null ? att.getContentType() : "application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + java.net.URLEncoder.encode(downloadName, java.nio.charset.StandardCharsets.UTF_8) + "\"");
        long size = att.getFileSize() != null ? att.getFileSize() : storageService.size(att.getFilePath());
        if (size >= 0) {
            response.setContentLengthLong(size);
        }
        try (OutputStream out = response.getOutputStream()) {
            storageService.copyTo(att.getFilePath(), out);
        }
    }

    /** 逻辑删除附件 */
    @RequirePerm("sys:attachment:delete")
    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        Attachment att = require(id);
        att.setIsDeleted(1);
        attachmentMapper.updateById(att);
        return R.ok();
    }

    private Attachment require(Long id) {
        Attachment att = attachmentMapper.selectById(id);
        if (att == null || (att.getIsDeleted() != null && att.getIsDeleted() == 1)) {
            throw new BizException("附件不存在");
        }
        return att;
    }

    private long maxMb() {
        return maxSizeMb <= 0 ? 20 : maxSizeMb;
    }

    private java.util.Set<String> allowedExts() {
        if (allowExt == null || allowExt.isBlank()) {
            return java.util.Set.of("jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "csv", "zip");
        }
        return java.util.Arrays.stream(allowExt.split(","))
                .map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file.bin";
        }
        String n = name.replace("\\", "/");
        int idx = n.lastIndexOf('/');
        if (idx >= 0) {
            n = n.substring(idx + 1);
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_");
        return n.isBlank() ? "file.bin" : n;
    }

    private String md5(String savedPath) throws Exception {
        Path path = Paths.get(savedPath);
        if (!Files.exists(path)) {
            return null;
        }
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
