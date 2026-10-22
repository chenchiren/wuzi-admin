package com.relex.wuzi.web.mobile;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.TraceScanLog;
import com.relex.wuzi.mapper.TraceScanLogMapper;
import com.relex.wuzi.service.TraceScanService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机端全局扫码：解析 qr_code / trace_token / 结构化码，写 trace_scan_log（channel=H5）。
 */
@RestController
@RequestMapping("/api/m/scan")
@RequiredArgsConstructor
public class MobileScanController {

    private final TraceScanService scanService;
    private final TraceScanLogMapper scanLogMapper;

    @Data
    public static class ResolveReq {
        /** 原始扫码内容：URL / trace_token / qr_code / MAT:..|BATCH:..|SN:.. */
        private String code;
        private String scanType;
        private String bizType;
        private Long bizId;
        private Long warehouseId;
        private String channel;
    }

    @LoginOnly("扫码解析")
    @PostMapping("/resolve")
    public R<Map<String, Object>> resolve(@RequestBody ResolveReq req,
                                          @RequestAttribute(value = "userId", required = false) Long userId,
                                          @RequestAttribute(value = "username", required = false) String username,
                                          @RequestAttribute(value = "roles", required = false) List<String> roles,
                                          @RequestParam(required = false) Boolean hidePrice,
                                          jakarta.servlet.http.HttpServletRequest http) {
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);
        TraceScanService.Resolved resolved = scanService.resolve(req.getCode());

        TraceScanService.ScanContext ctx = new TraceScanService.ScanContext();
        ctx.setOperatorId(userId);
        ctx.setOperatorName(username);
        ctx.setChannel(req.getChannel() != null && !req.getChannel().isBlank()
                ? req.getChannel() : TraceScanService.CHANNEL_H5);
        ctx.setScanType(req.getScanType() != null && !req.getScanType().isBlank() ? req.getScanType() : "QUERY");
        ctx.setBizType(req.getBizType());
        ctx.setBizId(req.getBizId());
        ctx.setWarehouseId(req.getWarehouseId());
        ctx.setClientIp(clientIp(http));
        TraceScanLog log = scanService.writeLog(resolved, ctx);

        Map<String, Object> data = new LinkedHashMap<>(scanService.toView(resolved, hide));
        data.put("scanLogId", log.getId());
        data.put("channel", log.getChannel());
        return R.ok(data);
    }

    @GetMapping("/recent")
    public R<PageResult<Map<String, Object>>> recent(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestAttribute(value = "userId", required = false) Long userId,
                                                     @RequestParam(defaultValue = "false") boolean onlyMine) {
        LambdaQueryWrapper<TraceScanLog> qw = new LambdaQueryWrapper<TraceScanLog>()
                .eq(onlyMine && userId != null, TraceScanLog::getOperatorId, userId)
                .orderByDesc(TraceScanLog::getId);
        Page<TraceScanLog> p = scanLogMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = p.getRecords().stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("qrId", log.getQrId());
            row.put("qrCode", log.getQrCode());
            row.put("scanType", log.getScanType());
            row.put("bizType", log.getBizType());
            row.put("bizId", log.getBizId());
            row.put("channel", log.getChannel());
            row.put("result", log.getResult());
            row.put("failReason", log.getFailReason());
            row.put("operatorId", log.getOperatorId());
            row.put("operatorName", log.getOperatorName());
            row.put("scanTime", log.getScanTime());
            return row;
        }).toList();
        return R.ok(PageResult.of(p.getTotal(), rows));
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }
}
