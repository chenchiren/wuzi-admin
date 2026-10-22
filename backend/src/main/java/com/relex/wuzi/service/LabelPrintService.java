package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.mapper.LabelTemplateMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.TraceQrCodeMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 标签打印：生成 Zebra ZPL 与可打印 HTML 标签页。
 * 模板来源：库表 label_template（按 templateCode）优先，未指定时回退 wuzi.label.* 配置默认值。
 */
@Service
@RequiredArgsConstructor
public class LabelPrintService {

    private final TraceQrCodeMapper qrMapper;
    private final LabelTemplate labelTemplate;
    private final LabelTemplateMapper labelTemplateMapper;
    private final SupplierMapper supplierMapper;

    /**
     * 打印参数视图：统一承载「配置默认」与「库表模板」两种来源。
     */
    @Data
    public static class PrintSpec {
        private String templateCode = "DEFAULT_50X30";
        private String templateName = "默认 50×30";
        private double widthMm = 50;
        private double heightMm = 30;
        private int dpi = 203;
        private String title = "物资标签";
        private int qrModuleSize = 5;
        private boolean showBatch = true;
        private boolean showSerial = true;
        private boolean showSupplier = false;
        private boolean showPrice = false;
        private String htmlBody;

        public int widthDots() {
            return mmToDots(widthMm);
        }

        public int heightDots() {
            return mmToDots(heightMm);
        }

        public int mmToDots(double mm) {
            return (int) Math.round(mm / 25.4 * dpi);
        }
    }

    public TraceQrCode requireQr(Long qrId) {
        if (qrId == null || qrId <= 0) {
            throw new BizException("qrId 无效");
        }
        TraceQrCode qr = qrMapper.selectById(qrId);
        if (qr == null) {
            throw new BizException("码不存在：" + qrId);
        }
        return qr;
    }

    /** 兼容旧接口：返回 yml 配置默认模板 */
    public LabelTemplate template() {
        return labelTemplate;
    }

    /** 配置默认 → PrintSpec */
    public PrintSpec defaultSpec() {
        PrintSpec spec = new PrintSpec();
        spec.setTemplateCode(labelTemplate.getTemplateCode());
        spec.setTemplateName(labelTemplate.getTemplateCode());
        spec.setWidthMm(labelTemplate.getWidthMm());
        spec.setHeightMm(labelTemplate.getHeightMm());
        spec.setDpi(labelTemplate.getDpi());
        spec.setTitle(labelTemplate.getTitle());
        spec.setQrModuleSize(labelTemplate.getQrMagnification());
        return spec;
    }

    /**
     * 按模板编码取库表模板；空编码回退配置默认。
     */
    public PrintSpec resolveSpec(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return defaultSpec();
        }
        com.relex.wuzi.domain.LabelTemplate tpl = labelTemplateMapper.selectOne(
                new LambdaQueryWrapper<com.relex.wuzi.domain.LabelTemplate>()
                        .eq(com.relex.wuzi.domain.LabelTemplate::getTemplateCode, templateCode.trim())
                        .last("LIMIT 1"));
        if (tpl == null) {
            throw new BizException("标签模板不存在：" + templateCode);
        }
        return toSpec(tpl);
    }

    public PrintSpec toSpec(com.relex.wuzi.domain.LabelTemplate tpl) {
        PrintSpec spec = new PrintSpec();
        spec.setTemplateCode(tpl.getTemplateCode());
        spec.setTemplateName(tpl.getTemplateName());
        if (tpl.getWidthMm() != null) {
            spec.setWidthMm(tpl.getWidthMm().doubleValue());
        }
        if (tpl.getHeightMm() != null) {
            spec.setHeightMm(tpl.getHeightMm().doubleValue());
        }
        spec.setDpi(tpl.getDpi() == null ? 203 : tpl.getDpi());
        spec.setTitle(tpl.getTitle() == null || tpl.getTitle().isBlank() ? "物资标签" : tpl.getTitle());
        spec.setQrModuleSize(tpl.getQrModuleSize() == null ? 5 : tpl.getQrModuleSize());
        spec.setShowBatch(tpl.getShowBatch() == null || tpl.getShowBatch() == 1);
        spec.setShowSerial(tpl.getShowSerial() == null || tpl.getShowSerial() == 1);
        spec.setShowSupplier(tpl.getShowSupplier() != null && tpl.getShowSupplier() == 1);
        spec.setShowPrice(tpl.getShowPrice() != null && tpl.getShowPrice() == 1);
        spec.setHtmlBody(tpl.getHtmlBody());
        return spec;
    }

    // ---------- ZPL ----------

    public String buildZpl(Long qrId) {
        return buildZpl(requireQr(qrId), defaultSpec());
    }

    public String buildZpl(Long qrId, String templateCode) {
        return buildZpl(requireQr(qrId), resolveSpec(templateCode));
    }

    public String buildZpl(TraceQrCode qr) {
        return buildZpl(qr, defaultSpec());
    }

    public String buildZpl(TraceQrCode qr, PrintSpec spec) {
        String code = nvl(qr.getQrCode());
        String name = nvl(qr.getMaterialName());
        String batch = nvl(qr.getBatchNo());
        String serial = nvl(qr.getSerialNo());
        String content = nvl(qr.getQrContent());
        if (content.isBlank()) {
            content = code;
        }
        int w = spec.widthDots();
        int h = spec.heightDots();
        int mag = Math.max(2, Math.min(10, spec.getQrModuleSize()));
        int qrBox = 20 + mag * 20;

        StringBuilder sb = new StringBuilder();
        sb.append("^XA\n");
        sb.append("^CI28\n");
        sb.append("^PW").append(w).append("\n");
        sb.append("^LL").append(h).append("\n");
        sb.append("^LH0,0\n");
        // 二维码（左侧）
        sb.append("^FO16,20\n");
        sb.append("^BQN,2,").append(mag).append("\n");
        sb.append("^FDQA,").append(escapeZpl(content)).append("^FS\n");
        // 名称
        sb.append("^FO").append(qrBox).append(",24\n");
        sb.append("^A0N,28,28\n");
        sb.append("^FD").append(escapeZpl(name)).append("^FS\n");
        // 码值
        sb.append("^FO").append(qrBox).append(",62\n");
        sb.append("^A0N,22,22\n");
        sb.append("^FD").append(escapeZpl(code)).append("^FS\n");
        int y = 96;
        if (spec.isShowBatch()) {
            sb.append("^FO").append(qrBox).append(",").append(y).append("\n");
            sb.append("^A0N,22,22\n");
            sb.append("^FD").append(escapeZpl(batch.isEmpty() ? "-" : batch)).append("^FS\n");
            y += 28;
        }
        if (spec.isShowSerial() && !serial.isEmpty()) {
            sb.append("^FO").append(qrBox).append(",").append(y).append("\n");
            sb.append("^A0N,18,18\n");
            sb.append("^FD").append(escapeZpl(serial)).append("^FS\n");
        }
        // 底部模板信息
        sb.append("^FO16,").append(Math.max(10, h - 28)).append("\n");
        sb.append("^A0N,18,18\n");
        sb.append("^FD").append(escapeZpl(spec.getTemplateCode())).append("^FS\n");
        sb.append("^PQ1\n");
        sb.append("^XZ");
        return sb.toString();
    }

    // ---------- HTML ----------

    public String buildHtml(Long qrId) {
        return buildHtml(requireQr(qrId), defaultSpec());
    }

    public String buildHtml(Long qrId, String templateCode) {
        return buildHtml(requireQr(qrId), resolveSpec(templateCode));
    }

    public String buildHtml(TraceQrCode qr) {
        return buildHtml(qr, defaultSpec());
    }

    public String buildHtml(TraceQrCode qr, PrintSpec spec) {
        String code = nvl(qr.getQrCode());
        String name = nvl(qr.getMaterialName());
        String batch = nvl(qr.getBatchNo());
        String serial = nvl(qr.getSerialNo());
        String specStr = nvl(qr.getSpec());
        String content = nvl(qr.getQrContent());
        if (content.isBlank()) {
            content = code;
        }
        String qrSvg = com.relex.wuzi.common.QrSvg.encode(content, "100%");
        double w = spec.getWidthMm();
        double h = spec.getHeightMm();
        String title = nvl(spec.getTitle());

        String bodyInner;
        String custom = spec.getHtmlBody();
        if (custom != null && !custom.isBlank()) {
            // 自定义 HTML 模板：替换占位符（自定义内容由模板管理员维护，此处仅做字段替换）
            bodyInner = custom
                    .replace("{{qrSvg}}", qrSvg)
                    .replace("{{title}}", esc(title))
                    .replace("{{materialName}}", esc(name))
                    .replace("{{code}}", esc(code))
                    .replace("{{batch}}", esc(batch.isEmpty() ? "-" : batch))
                    .replace("{{serial}}", esc(serial.isEmpty() ? "-" : serial))
                    .replace("{{spec}}", esc(specStr))
                    .replace("{{templateCode}}", esc(spec.getTemplateCode()));
        } else {
            StringBuilder info = new StringBuilder();
            info.append("<div class=\"title\">").append(esc(title)).append("</div>");
            info.append("<div class=\"name\">").append(esc(name)).append("</div>");
            info.append("<div class=\"code\">码值：").append(esc(code)).append("</div>");
            if (spec.isShowBatch()) {
                info.append("<div class=\"batch\">批次：").append(esc(batch.isEmpty() ? "-" : batch)).append("</div>");
            }
            if (spec.isShowSerial()) {
                info.append("<div class=\"batch\">序列：").append(esc(serial.isEmpty() ? "-" : serial)).append("</div>");
            }
            if (!specStr.isEmpty()) {
                info.append("<div class=\"spec\">规格：").append(esc(specStr)).append("</div>");
            }
            if (spec.isShowSupplier()) {
                info.append("<div class=\"spec\">供应商：").append(esc(supplierName(qr.getSupplierId()))).append("</div>");
            }
            if (spec.isShowPrice()) {
                info.append("<div class=\"spec\">价格：—</div>");
            }
            bodyInner = "<div class=\"label\"><div class=\"qr\">" + qrSvg + "</div><div class=\"info\">" + info + "</div></div>";
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="UTF-8" />
                <title>%s</title>
                <style>
                  * { box-sizing: border-box; margin: 0; padding: 0; }
                  body { font-family: "Microsoft YaHei", Arial, sans-serif; background: #f0f2f5; padding: 16px; }
                  .toolbar { margin-bottom: 12px; text-align: center; }
                  .toolbar button { padding: 6px 16px; border: 1px solid #2f6bff; background: #2f6bff; color: #fff; border-radius: 4px; cursor: pointer; margin: 0 4px; }
                  .toolbar button.secondary { background: #fff; color: #2f6bff; }
                  .wrap { display: flex; justify-content: center; }
                  .label {
                    width: %smm; height: %smm; background: #fff; border: 1px solid #333;
                    padding: 1.5mm; display: flex; gap: 1.5mm; overflow: hidden;
                  }
                  .qr {
                    width: 26mm; height: 26mm; flex-shrink: 0;
                    display: flex; align-items: center; justify-content: center;
                    background: #fff;
                  }
                  .qr svg { width: 100%%; height: 100%%; display: block; }
                  .info { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 0.6mm; }
                  .info .title { font-size: 8px; color: #888; }
                  .info .name { font-size: 11px; font-weight: 700; line-height: 1.15; word-break: break-all; }
                  .info .code { font-size: 8px; font-family: Consolas, monospace; word-break: break-all; }
                  .info .batch { font-size: 8px; word-break: break-all; }
                  .info .spec { font-size: 7px; color: #666; }
                  @media print {
                    body { background: #fff; padding: 0; }
                    .toolbar { display: none; }
                    .label { border-color: #000; }
                  }
                  @page { size: %smm %smm; margin: 1mm; }
                </style>
                </head>
                <body>
                  <div class="toolbar">
                    <button onclick="window.print()">打印标签</button>
                    <button class="secondary" onclick="window.close()">关闭</button>
                  </div>
                  <div class="wrap">
                    %s
                  </div>
                </body>
                </html>
                """.formatted(esc(title), w, h, w, h, bodyInner);
    }

    /** 供前端预览的标签数据 */
    public Map<String, Object> labelData(TraceQrCode qr) {
        return labelData(qr, defaultSpec());
    }

    public Map<String, Object> labelData(TraceQrCode qr, PrintSpec spec) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("qrId", qr.getId());
        data.put("qrCode", qr.getQrCode());
        data.put("qrContent", qr.getQrContent());
        data.put("materialName", qr.getMaterialName());
        data.put("materialCode", qr.getMaterialCode());
        data.put("batchNo", qr.getBatchNo());
        data.put("serialNo", qr.getSerialNo());
        data.put("spec", qr.getSpec());
        data.put("unit", qr.getUnit());
        data.put("template", spec);
        return data;
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) {
            return "-";
        }
        Supplier sp = supplierMapper.selectById(supplierId);
        return sp == null || sp.getSupplierName() == null ? ("#" + supplierId) : sp.getSupplierName();
    }

    private String escapeZpl(String s) {
        if (s == null) {
            return "";
        }
        // ZPL 字段中 ^ 与 \ 需转义
        return s.replace("\\", "\\\\").replace("^", "\\^");
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
