package com.relex.wuzi.web;

import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.service.LabelPrintService;
import com.relex.wuzi.service.LabelTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 标签打印：ZPL 文本 + 可打印 HTML 标签页。
 * GET /api/labels/zpl/{qrId}?templateCode=DEFAULT_50X30
 * GET /api/labels/html/{qrId}?templateCode=DEFAULT_50X30
 * 不传 templateCode 时沿用 wuzi.label.* 配置默认模板（兼容旧调用）。
 */
@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
public class LabelPrintController {

    private final LabelPrintService labelPrintService;

    @GetMapping("/zpl/{qrId}")
    public ResponseEntity<String> zpl(@PathVariable Long qrId,
                                      @RequestParam(required = false) String templateCode) {
        String zpl = labelPrintService.buildZpl(qrId, templateCode);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(zpl);
    }

    @GetMapping("/html/{qrId}")
    public ResponseEntity<String> html(@PathVariable Long qrId,
                                       @RequestParam(required = false) String templateCode) {
        String html = labelPrintService.buildHtml(qrId, templateCode);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    @GetMapping("/template")
    public R<LabelTemplate> template() {
        return R.ok(labelPrintService.template());
    }

    @GetMapping("/data/{qrId}")
    public R<Map<String, Object>> data(@PathVariable Long qrId,
                                       @RequestParam(required = false) String templateCode) {
        TraceQrCode qr = labelPrintService.requireQr(qrId);
        LabelPrintService.PrintSpec spec = labelPrintService.resolveSpec(templateCode);
        return R.ok(labelPrintService.labelData(qr, spec));
    }
}
