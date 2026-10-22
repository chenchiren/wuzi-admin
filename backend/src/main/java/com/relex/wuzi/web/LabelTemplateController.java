package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.LabelTemplate;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.mapper.LabelTemplateMapper;
import com.relex.wuzi.mapper.TraceQrCodeMapper;
import com.relex.wuzi.service.LabelPrintService;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 标签模板在线编辑：
 * GET    /api/label-templates          启用模板列表（供选择）
 * GET    /api/label-templates/all      全部（含停用，供管理）
 * GET    /api/label-templates/{id}
 * POST   /api/label-templates
 * PUT    /api/label-templates/{id}
 * DELETE /api/label-templates/{id}
 * POST   /api/label-templates/{id}/preview
 */
@RestController
@RequestMapping("/api/label-templates")
@RequiredArgsConstructor
public class LabelTemplateController {

    private final LabelTemplateMapper templateMapper;
    private final TraceQrCodeMapper qrMapper;
    private final LabelPrintService labelPrintService;
    private final OperationLogService operationLogService;

    @GetMapping
    public R<List<LabelTemplate>> list() {
        return R.ok(templateMapper.selectList(new LambdaQueryWrapper<LabelTemplate>()
                .eq(LabelTemplate::getStatus, 1)
                .orderByAsc(LabelTemplate::getId)));
    }

    @GetMapping("/all")
    public R<List<LabelTemplate>> all() {
        return R.ok(templateMapper.selectList(new LambdaQueryWrapper<LabelTemplate>()
                .orderByAsc(LabelTemplate::getId)));
    }

    @GetMapping("/{id}")
    public R<LabelTemplate> get(@PathVariable Long id) {
        LabelTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            return R.fail("模板不存在");
        }
        return R.ok(tpl);
    }

    @Data
    public static class SaveReq {
        private String templateCode;
        private String templateName;
        private BigDecimal widthMm;
        private BigDecimal heightMm;
        private Integer dpi;
        private String title;
        private Integer qrModuleSize;
        private Integer showBatch;
        private Integer showSerial;
        private Integer showSupplier;
        private Integer showPrice;
        private String htmlBody;
        private Integer status;
    }

    @RequirePerm("label:template:create")
    @PostMapping
    public R<LabelTemplate> create(@RequestBody SaveReq req,
                                   @RequestAttribute(value = "userId", required = false) Long opId,
                                   @RequestAttribute(value = "username", required = false) String opName,
                                   HttpServletRequest request) {
        requireValid(req);
        Long exists = templateMapper.selectCount(new LambdaQueryWrapper<LabelTemplate>()
                .eq(LabelTemplate::getTemplateCode, req.getTemplateCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException("模板编码已存在");
        }
        LabelTemplate tpl = new LabelTemplate();
        apply(tpl, req, true);
        tpl.setIsDeleted(0);
        tpl.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(tpl);
        operationLogService.record(request, opId, opName, "LABEL_TEMPLATE", "CREATE",
                "templateCode=" + tpl.getTemplateCode(), "SUCCESS");
        return R.ok(tpl);
    }

    @RequirePerm("label:template:update")
    @PutMapping("/{id}")
    public R<LabelTemplate> update(@PathVariable Long id, @RequestBody SaveReq req,
                                   @RequestAttribute(value = "userId", required = false) Long opId,
                                   @RequestAttribute(value = "username", required = false) String opName,
                                   HttpServletRequest request) {
        LabelTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            return R.fail("模板不存在");
        }
        if (req.getTemplateCode() != null && !req.getTemplateCode().isBlank()) {
            Long exists = templateMapper.selectCount(new LambdaQueryWrapper<LabelTemplate>()
                    .eq(LabelTemplate::getTemplateCode, req.getTemplateCode().trim())
                    .ne(LabelTemplate::getId, id));
            if (exists != null && exists > 0) {
                throw new BizException("模板编码已存在");
            }
            tpl.setTemplateCode(req.getTemplateCode().trim());
        }
        apply(tpl, req, false);
        tpl.setUpdatedAt(LocalDateTime.now());
        templateMapper.updateById(tpl);
        operationLogService.record(request, opId, opName, "LABEL_TEMPLATE", "UPDATE",
                "templateId=" + id, "SUCCESS");
        return R.ok(tpl);
    }

    @RequirePerm("label:template:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        LabelTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            return R.fail("模板不存在");
        }
        templateMapper.deleteById(id);
        operationLogService.record(request, opId, opName, "LABEL_TEMPLATE", "DELETE",
                "templateId=" + id, "SUCCESS");
        return R.ok();
    }

    @Data
    public static class PreviewReq {
        /** 可选：用真实码预览；为空则用演示数据 */
        private Long qrId;
        /** html | zpl | data，默认 html */
        private String type;
    }

    /**
     * 预览：返回 HTML 文本、ZPL 文本或码数据（供前端渲染）。
     */
    @SuppressWarnings("rawtypes")
    @RequirePerm("label:template:preview")
    @PostMapping("/{id}/preview")
    public ResponseEntity preview(@PathVariable Long id, @RequestBody(required = false) PreviewReq req) {
        LabelTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw new BizException("模板不存在");
        }
        LabelPrintService.PrintSpec spec = labelPrintService.toSpec(tpl);
        String type = req == null || req.getType() == null || req.getType().isBlank()
                ? "html" : req.getType().trim().toLowerCase();

        TraceQrCode qr;
        if (req != null && req.getQrId() != null && req.getQrId() > 0) {
            qr = labelPrintService.requireQr(req.getQrId());
        } else {
            qr = demoQr(tpl);
        }

        if ("zpl".equals(type)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                    .body(labelPrintService.buildZpl(qr, spec));
        }
        if ("data".equals(type)) {
            Map<String, Object> data = labelPrintService.labelData(qr, spec);
            data.put("templateEntity", tpl);
            return ResponseEntity.ok(R.ok(data));
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(labelPrintService.buildHtml(qr, spec));
    }

    private TraceQrCode demoQr(LabelTemplate tpl) {
        TraceQrCode demo = new TraceQrCode();
        demo.setId(0L);
        demo.setQrCode("DEMO-" + (tpl.getTemplateCode() == null ? "TPL" : tpl.getTemplateCode()));
        demo.setQrContent("MAT:DEMO|BATCH:DEMO-001");
        demo.setMaterialName("示例物资");
        demo.setMaterialCode("DEMO-001");
        demo.setBatchNo("DEMO-001");
        demo.setSerialNo("SN-DEMO-0001");
        demo.setSpec("示例规格");
        demo.setUnit("个");
        return demo;
    }

    private void requireValid(SaveReq req) {
        if (req.getTemplateCode() == null || req.getTemplateCode().isBlank()) {
            throw new BizException("模板编码不能为空");
        }
        if (req.getTemplateName() == null || req.getTemplateName().isBlank()) {
            throw new BizException("模板名称不能为空");
        }
        if (req.getWidthMm() == null || req.getWidthMm().doubleValue() <= 0) {
            throw new BizException("宽度必须大于 0");
        }
        if (req.getHeightMm() == null || req.getHeightMm().doubleValue() <= 0) {
            throw new BizException("高度必须大于 0");
        }
    }

    private void apply(LabelTemplate tpl, SaveReq req, boolean isCreate) {
        if (isCreate) {
            tpl.setTemplateCode(req.getTemplateCode().trim());
        }
        if (req.getTemplateName() != null && !req.getTemplateName().isBlank()) {
            tpl.setTemplateName(req.getTemplateName().trim());
        }
        if (req.getWidthMm() != null) {
            tpl.setWidthMm(req.getWidthMm());
        }
        if (req.getHeightMm() != null) {
            tpl.setHeightMm(req.getHeightMm());
        }
        if (req.getDpi() != null) {
            tpl.setDpi(req.getDpi());
        } else if (isCreate) {
            tpl.setDpi(203);
        }
        if (req.getTitle() != null) {
            tpl.setTitle(req.getTitle());
        } else if (isCreate) {
            tpl.setTitle("物资标签");
        }
        if (req.getQrModuleSize() != null) {
            tpl.setQrModuleSize(req.getQrModuleSize());
        } else if (isCreate) {
            tpl.setQrModuleSize(5);
        }
        tpl.setShowBatch(req.getShowBatch() == null ? (isCreate ? 1 : tpl.getShowBatch()) : req.getShowBatch());
        tpl.setShowSerial(req.getShowSerial() == null ? (isCreate ? 1 : tpl.getShowSerial()) : req.getShowSerial());
        tpl.setShowSupplier(req.getShowSupplier() == null ? (isCreate ? 0 : tpl.getShowSupplier()) : req.getShowSupplier());
        tpl.setShowPrice(req.getShowPrice() == null ? (isCreate ? 0 : tpl.getShowPrice()) : req.getShowPrice());
        if (req.getHtmlBody() != null) {
            tpl.setHtmlBody(req.getHtmlBody());
        }
        if (req.getStatus() != null) {
            tpl.setStatus(req.getStatus());
        } else if (isCreate) {
            tpl.setStatus(1);
        }
    }
}
