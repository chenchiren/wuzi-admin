package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.TraceQrCodeMapper;
import com.relex.wuzi.service.TraceScanService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
public class TraceQrController {

    private final TraceQrCodeMapper qrMapper;
    private final MaterialMapper materialMapper;
    private final TraceScanService traceScanService;

    @Data
    public static class GenerateReq {
        private String qrType; // SKU / BATCH / SERIAL
        private Long materialId;
        private String batchNo;
        private String serialNo;
        private String materialCode;
        private String materialName;
        private String spec;
        private String unit;
    }

    @RequirePerm("trace:qr:generate")
    @PostMapping("/qr/generate")
    public R<TraceQrCode> generate(@RequestBody GenerateReq req) {
        String type = req.getQrType() == null || req.getQrType().isBlank()
                ? "SKU" : req.getQrType().toUpperCase(Locale.ROOT);
        if (!List.of("SKU", "BATCH", "SERIAL").contains(type)) {
            throw new BizException("qrType 仅支持 SKU/BATCH/SERIAL");
        }
        if (req.getMaterialId() == null || req.getMaterialId() <= 0) {
            throw new BizException("请选择物资");
        }
        if ("BATCH".equals(type) && (req.getBatchNo() == null || req.getBatchNo().isBlank())) {
            throw new BizException("批码必须提供批次号");
        }
        if ("SERIAL".equals(type) && (req.getSerialNo() == null || req.getSerialNo().isBlank())) {
            throw new BizException("件码必须提供序列号");
        }

        // 从物资档案补齐名称/编码/规格，避免手填不一致
        Material material = materialMapper.selectById(req.getMaterialId());
        if (material == null) {
            throw new BizException("物资不存在: " + req.getMaterialId());
        }
        String materialCode = firstNonBlank(req.getMaterialCode(), material.getMaterialCode(),
                String.valueOf(req.getMaterialId()));
        String materialName = firstNonBlank(req.getMaterialName(), material.getMaterialName(), "");
        String spec = firstNonBlank(req.getSpec(), material.getSpec(), "");
        String unit = firstNonBlank(req.getUnit(), material.getUnit(), "");
        String batchNo = req.getBatchNo() == null || req.getBatchNo().isBlank() ? null : req.getBatchNo().trim();
        String serialNo = req.getSerialNo() == null || req.getSerialNo().isBlank() ? null : req.getSerialNo().trim();

        // 唯一约束预检（sku_uk / batch_uk / serial_uk），给出可读错误
        requireNotExists(type, req.getMaterialId(), batchNo, serialNo);

        TraceQrCode entity = new TraceQrCode();
        entity.setQrType(type);
        entity.setMaterialId(req.getMaterialId());
        entity.setBatchNo(batchNo);
        entity.setSerialNo(serialNo);
        entity.setMaterialCode(materialCode);
        entity.setMaterialName(materialName);
        entity.setSpec(spec);
        entity.setUnit(unit);
        entity.setQrGranularity(type);
        entity.setStatus("CREATED");
        entity.setPrintCount(0);
        entity.setScanCount(0);
        entity.setLabelVersion(1);
        entity.setIsEnabled(1);
        entity.setIsDeleted(0);

        String content = switch (type) {
            case "BATCH" -> "MAT:" + materialCode + "|BATCH:" + batchNo;
            case "SERIAL" -> "MAT:" + materialCode + "|SN:" + serialNo;
            default -> "MAT:" + materialCode;
        };
        entity.setQrContent(content);
        entity.setQrCode(type + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setTraceToken(randomToken());
        entity.setContentMode("TOKEN");

        try {
            qrMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw friendlyDuplicate(type, e);
        } catch (RuntimeException e) {
            if (isDuplicate(e)) {
                throw friendlyDuplicate(type, e);
            }
            throw e;
        }
        return R.ok(entity);
    }

    private void requireNotExists(String type, Long materialId, String batchNo, String serialNo) {
        switch (type) {
            case "SKU" -> {
                Long c = qrMapper.selectCount(new LambdaQueryWrapper<TraceQrCode>()
                        .eq(TraceQrCode::getQrType, "SKU")
                        .eq(TraceQrCode::getMaterialId, materialId));
                if (c != null && c > 0) {
                    throw new BizException("该物资已存在品码，请勿重复生成");
                }
            }
            case "BATCH" -> {
                Long c = qrMapper.selectCount(new LambdaQueryWrapper<TraceQrCode>()
                        .eq(TraceQrCode::getQrType, "BATCH")
                        .eq(TraceQrCode::getMaterialId, materialId)
                        .eq(TraceQrCode::getBatchNo, batchNo));
                if (c != null && c > 0) {
                    throw new BizException("该物资批次已存在批码: " + batchNo);
                }
            }
            case "SERIAL" -> {
                Long c = qrMapper.selectCount(new LambdaQueryWrapper<TraceQrCode>()
                        .eq(TraceQrCode::getQrType, "SERIAL")
                        .eq(TraceQrCode::getSerialNo, serialNo));
                if (c != null && c > 0) {
                    throw new BizException("序列号已存在件码: " + serialNo);
                }
            }
            default -> { }
        }
    }

    private BizException friendlyDuplicate(String type, Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("uk_serial") || "SERIAL".equals(type)) {
            return new BizException("序列号已被占用，请更换序列号");
        }
        if (raw.contains("uk_batch") || "BATCH".equals(type)) {
            return new BizException("该批次码已存在，请更换批次或物资");
        }
        return new BizException("该物资品码已存在，无需重复生成");
    }

    private boolean isDuplicate(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof DuplicateKeyException) {
                return true;
            }
            String m = t.getMessage();
            if (m != null && (m.contains("Duplicate entry") || m.contains("uk_"))) {
                return true;
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        return false;
    }

    /**
     * 对外免登录公开溯源（JwtAuthFilter 白名单 /api/trace/public）。
     * 返回脱敏基础信息 + 正向/反向链路；带轻量 IP/token 频次限制。
     */
    @GetMapping("/public/{traceToken}")
    public R<Map<String, Object>> publicTrace(@PathVariable String traceToken, HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        if (!traceScanService.allowPublicQuery(clientIp, traceToken)) {
            throw new BizException(429, "查询过于频繁，请稍后再试");
        }
        TraceQrCode qr = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                .eq(TraceQrCode::getTraceToken, traceToken));
        if (qr == null) {
            throw new BizException(404, "溯源码不存在");
        }
        qr.setScanCount((qr.getScanCount() == null ? 0 : qr.getScanCount()) + 1);
        qr.setLastScanAt(LocalDateTime.now());
        if (qr.getFirstScanAt() == null) {
            qr.setFirstScanAt(LocalDateTime.now());
        }
        qrMapper.updateById(qr);
        return R.ok(traceScanService.buildPublicView(qr));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/qr/{id}")
    public R<TraceQrCode> get(@PathVariable Long id) {
        TraceQrCode qr = qrMapper.selectById(id);
        if (qr == null) {
            throw new BizException("码不存在");
        }
        return R.ok(qr);
    }

    @GetMapping("/qr")
    public R<PageResult<TraceQrCode>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String qrType,
                                           @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<TraceQrCode> qw = new LambdaQueryWrapper<TraceQrCode>()
                .eq(qrType != null && !qrType.isBlank(), TraceQrCode::getQrType, qrType)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(TraceQrCode::getQrCode, keyword)
                        .or().like(TraceQrCode::getMaterialName, keyword)
                        .or().like(TraceQrCode::getBatchNo, keyword))
                .orderByDesc(TraceQrCode::getId);
        Page<TraceQrCode> p = qrMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @RequirePerm("trace:qr:activate")
    @PostMapping("/qr/{id}/activate")
    public R<TraceQrCode> activate(@PathVariable Long id) {
        TraceQrCode qr = qrMapper.selectById(id);
        if (qr == null) {
            throw new BizException("码不存在");
        }
        qr.setStatus("ACTIVE");
        qr.setActivatedAt(LocalDateTime.now());
        qrMapper.updateById(qr);
        return R.ok(qr);
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        new Random().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
