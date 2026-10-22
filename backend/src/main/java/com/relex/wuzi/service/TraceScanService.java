package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.StockRecord;
import com.relex.wuzi.domain.TraceQrCode;
import com.relex.wuzi.domain.TraceScanLog;
import com.relex.wuzi.mapper.StockRecordMapper;
import com.relex.wuzi.mapper.TraceQrCodeMapper;
import com.relex.wuzi.mapper.TraceScanLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手机端码解析与扫码日志：支持 URL token / 结构化码 / qr_code / 序列号 / 批号。
 * 含公开溯源页的轻量频次限制（内存计数，生产可换 Redis）。
 */
@Service
@RequiredArgsConstructor
public class TraceScanService {

    public static final String CHANNEL_H5 = "H5";
    public static final String CHANNEL_PUBLIC = "PUBLIC";

    /** 同一 IP+token 在窗口内最多查询次数 */
    public static final int PUBLIC_RATE_LIMIT = 30;
    /** 窗口 60 秒 */
    public static final long PUBLIC_RATE_WINDOW_MS = 60_000L;

    private final TraceQrCodeMapper qrMapper;
    private final TraceScanLogMapper scanLogMapper;
    private final StockRecordMapper stockRecordMapper;

    /** 频次窗口：key = ip|token → (窗口起点, 计数)。生产建议 Redis INCR+EXPIRE 替换。 */
    private final Map<String, long[]> rateBuckets = new ConcurrentHashMap<>();

    @Data
    public static class Resolved {
        private boolean success;
        private String codeType;
        private String rawCode;
        private String failReason;
        private TraceQrCode qr;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String batchNo;
        private String serialNo;
        private String spec;
        private String unit;
        private Map<String, Object> extra = new LinkedHashMap<>();
    }

    @Data
    public static class ScanContext {
        private Long operatorId;
        private String operatorName;
        private String channel = CHANNEL_H5;
        private String scanType = "QUERY";
        private String bizType;
        private Long bizId;
        private Long warehouseId;
        private String clientIp;
    }

    public Resolved resolve(String raw) {
        Resolved out = new Resolved();
        out.setRawCode(raw);
        if (raw == null || raw.isBlank()) {
            out.setFailReason("空码");
            return out;
        }
        String code = normalize(raw);
        out.setExtra(new LinkedHashMap<>());

        TraceQrCode qr = findQr(code);
        if (qr == null && code.contains("MAT:")) {
            qr = findStructured(code, out);
        }
        if (qr == null) {
            qr = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                    .eq(TraceQrCode::getSerialNo, code).last("LIMIT 1"));
            if (qr != null) {
                out.setCodeType("SERIAL_NO");
            }
        }
        if (qr == null) {
            qr = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                    .eq(TraceQrCode::getBatchNo, code).last("LIMIT 1"));
            if (qr != null) {
                out.setCodeType("BATCH_NO");
            }
        }
        if (qr == null && out.getFailReason() == null) {
            out.setFailReason("无法识别的码");
            return out;
        }
        if (qr == null) {
            return out;
        }

        out.setSuccess(true);
        out.setQr(qr);
        if (out.getCodeType() == null) {
            out.setCodeType(qr.getQrType() != null ? qr.getQrType() : "QR");
        }
        out.setMaterialId(qr.getMaterialId());
        out.setMaterialCode(qr.getMaterialCode());
        out.setMaterialName(qr.getMaterialName());
        out.setBatchNo(qr.getBatchNo());
        out.setSerialNo(qr.getSerialNo());
        out.setSpec(qr.getSpec());
        out.setUnit(qr.getUnit());

        qr.setScanCount(qr.getScanCount() == null ? 1 : qr.getScanCount() + 1);
        qr.setLastScanAt(LocalDateTime.now());
        if (qr.getFirstScanAt() == null) {
            qr.setFirstScanAt(LocalDateTime.now());
        }
        qrMapper.updateById(qr);
        return out;
    }

    public TraceScanLog writeLog(Resolved resolved, ScanContext ctx) {
        TraceScanLog log = new TraceScanLog();
        log.setQrId(resolved.getQr() != null ? resolved.getQr().getId() : null);
        log.setQrCode(resolved.getQr() != null ? resolved.getQr().getQrCode()
                : (resolved.getRawCode() != null ? truncate(resolved.getRawCode(), 64) : null));
        log.setScanType(ctx.getScanType() != null ? ctx.getScanType() : "QUERY");
        log.setBizType(ctx.getBizType());
        log.setBizId(ctx.getBizId());
        log.setOperatorId(ctx.getOperatorId());
        log.setOperatorName(ctx.getOperatorName());
        log.setChannel(ctx.getChannel() != null ? ctx.getChannel() : CHANNEL_H5);
        log.setWarehouseId(ctx.getWarehouseId());
        log.setClientIp(ctx.getClientIp());
        log.setResult(resolved.isSuccess() ? "SUCCESS" : "FAIL");
        log.setFailReason(resolved.isSuccess() ? null : truncate(resolved.getFailReason(), 128));
        log.setScanTime(LocalDateTime.now());
        scanLogMapper.insert(log);
        return log;
    }

    public Map<String, Object> toView(Resolved resolved, boolean hidePrice) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", resolved.isSuccess());
        map.put("codeType", resolved.getCodeType());
        map.put("rawCode", resolved.getRawCode());
        map.put("failReason", resolved.getFailReason());
        if (resolved.getQr() == null) {
            return map;
        }
        TraceQrCode qr = resolved.getQr();
        map.put("qrId", qr.getId());
        map.put("qrCode", qr.getQrCode());
        map.put("qrType", qr.getQrType());
        map.put("traceToken", qr.getTraceToken());
        map.put("materialId", qr.getMaterialId());
        map.put("materialCode", qr.getMaterialCode());
        map.put("materialName", qr.getMaterialName());
        map.put("spec", qr.getSpec());
        map.put("unit", qr.getUnit());
        map.put("batchNo", qr.getBatchNo());
        map.put("serialNo", qr.getSerialNo());
        map.put("status", qr.getStatus());
        map.put("expireDate", qr.getExpireDate());
        map.put("currentWarehouseId", qr.getCurrentWarehouseId());
        map.put("supplierId", qr.getSupplierId());
        map.put("purchaseOrderId", qr.getPurchaseOrderId());
        map.put("inboundOrderId", qr.getInboundOrderId());
        map.put("scanCount", qr.getScanCount());
        if (!hidePrice) {
            map.put("priceVisible", true);
        }
        return map;
    }

    public static boolean shouldHidePrice(Boolean hidePrice, java.util.List<String> roles) {
        if (hidePrice != null) {
            return hidePrice;
        }
        return roles == null || roles.isEmpty() || roles.contains("EMPLOYEE");
    }

    // ---------- 公开溯源：频次限制 ----------

    /**
     * 轻量频次限制：同一 IP+token 在 60s 窗口内超过阈值则拒绝，并写扫码日志留痕。
     * 内存计数即可满足演示/中小流量；生产可换 Redis（INCR + EXPIRE）做分布式限流。
     *
     * @return true=放行；false=超限（已写日志）
     */
    public boolean allowPublicQuery(String clientIp, String token) {
        String key = (clientIp == null ? "unknown" : clientIp) + "|" + (token == null ? "" : token);
        long now = System.currentTimeMillis();
        long[] bucket = rateBuckets.compute(key, (k, cur) -> {
            if (cur == null || now - cur[0] > PUBLIC_RATE_WINDOW_MS) {
                return new long[]{now, 1};
            }
            cur[1] = cur[1] + 1;
            return cur;
        });
        if (bucket[1] <= PUBLIC_RATE_LIMIT) {
            // 惰性清理，防止 map 无限膨胀
            if (rateBuckets.size() > 10_000) {
                rateBuckets.entrySet().removeIf(e -> now - e.getValue()[0] > PUBLIC_RATE_WINDOW_MS);
            }
            return true;
        }
        TraceScanLog log = new TraceScanLog();
        log.setQrCode(token != null && token.length() > 64 ? token.substring(0, 64) : token);
        log.setScanType("PUBLIC_RATE_LIMIT");
        log.setChannel(CHANNEL_PUBLIC);
        log.setClientIp(clientIp);
        log.setResult("RATE_LIMITED");
        log.setFailReason("同一 IP/token 短时间查询过频");
        log.setScanTime(LocalDateTime.now());
        scanLogMapper.insert(log);
        return false;
    }

    // ---------- 公开溯源：脱敏视图 + 时间轴链路 ----------

    /**
     * 对外脱敏视图：不含内部价 / 税率 / 供应商全称；含正向（来源→当前）与反向（当前→去向）链路。
     */
    public Map<String, Object> buildPublicView(TraceQrCode qr) {
        Map<String, Object> pub = new LinkedHashMap<>();
        pub.put("materialName", qr.getMaterialName());
        pub.put("materialCode", qr.getMaterialCode());
        pub.put("spec", qr.getSpec());
        pub.put("unit", qr.getUnit());
        pub.put("batchNo", qr.getBatchNo());
        pub.put("serialNo", qr.getSerialNo());
        pub.put("qrType", qr.getQrType());
        pub.put("status", qr.getStatus());
        pub.put("expireDate", qr.getExpireDate());
        pub.put("activatedAt", qr.getActivatedAt());
        pub.put("createdAt", qr.getCreatedAt());
        pub.put("scanCount", qr.getScanCount());
        pub.put("firstScan", qr.getFirstScanAt() != null);
        // 脱敏：只暴露是否有关联供应商，不暴露名称/ID/价格
        pub.put("supplierMasked", qr.getSupplierId() != null);
        pub.put("priceVisible", false);

        Map<String, Object> forward = new LinkedHashMap<>();
        Map<String, Object> reverse = new LinkedHashMap<>();
        List<Map<String, Object>> forwardNodes = new ArrayList<>();
        List<Map<String, Object>> reverseNodes = new ArrayList<>();

        forwardNodes.add(node("GENERATE", "生成溯源码", qr.getCreatedAt(), "码 " + nullToDash(qr.getQrCode())));
        if (qr.getPurchaseOrderId() != null) {
            forwardNodes.add(node("PURCHASE", "采购订单", null, "订单 #" + qr.getPurchaseOrderId()));
        }
        if (qr.getInboundOrderId() != null) {
            forwardNodes.add(node("INBOUND", "入库单", null, "单据 #" + qr.getInboundOrderId()));
        }
        if (qr.getActivatedAt() != null) {
            forwardNodes.add(node("ACTIVATE", "激活", qr.getActivatedAt(), "标签启用"));
        }

        List<StockRecord> records = stockRecordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getMaterialId, qr.getMaterialId())
                .and(qr.getBatchNo() != null && !qr.getBatchNo().isBlank(),
                        w -> w.eq(StockRecord::getBatchNo, qr.getBatchNo()))
                .and(qr.getSerialNo() != null && !qr.getSerialNo().isBlank(),
                        w -> w.eq(StockRecord::getSerialNo, qr.getSerialNo()))
                .orderByAsc(StockRecord::getCreatedAt)
                .orderByAsc(StockRecord::getId)
                .last("LIMIT 50"));

        for (StockRecord r : records) {
            Map<String, Object> n = node(r.getBizType(), bizLabel(r.getBizType()), r.getCreatedAt(),
                    "变动 " + (r.getChangeQty() == null ? "-" : r.getChangeQty().toPlainString())
                            + (r.getBizNo() == null ? "" : " · " + r.getBizNo()));
            // 正向=入库/增加；反向=出库/红冲/领用
            boolean positive = r.getAmountSign() == null || r.getAmountSign() >= 0;
            if (positive) {
                forwardNodes.add(n);
            } else {
                reverseNodes.add(n);
            }
        }
        if (qr.getCurrentWarehouseId() != null) {
            forwardNodes.add(node("STOCK", "当前库存", qr.getLastScanAt(), "仓库 #" + qr.getCurrentWarehouseId()));
        }
        if (qr.getScanCount() != null && qr.getScanCount() > 0) {
            reverseNodes.add(node("SCAN", "查验记录", qr.getLastScanAt(), "累计查验 " + qr.getScanCount() + " 次"));
        }
        if (qr.getRetiredAt() != null) {
            reverseNodes.add(node("RETIRED", "停用/退役", qr.getRetiredAt(), "码已停用"));
        }

        forward.put("direction", "FORWARD");
        forward.put("label", "正向链路（来源 → 当前）");
        forward.put("nodes", forwardNodes);
        reverse.put("direction", "REVERSE");
        reverse.put("label", "反向链路（当前 → 去向）");
        reverse.put("nodes", reverseNodes);
        pub.put("forwardChain", forward);
        pub.put("reverseChain", reverse);
        // 兼容手机端简单时间轴
        pub.put("timeline", forwardNodes);
        return pub;
    }

    private Map<String, Object> node(String type, String title, LocalDateTime time, String desc) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("type", type);
        n.put("title", title);
        n.put("time", time);
        n.put("desc", desc);
        return n;
    }

    private String bizLabel(String bizType) {
        if (bizType == null) {
            return "库存变动";
        }
        return switch (bizType) {
            case "INBOUND" -> "入库";
            case "OUTBOUND" -> "出库";
            case "INBOUND_RED" -> "入库红冲";
            case "OUTBOUND_RED" -> "出库红冲";
            case "TRANSFER_IN" -> "调拨入";
            case "TRANSFER_OUT" -> "调拨出";
            case "CHECK_GAIN" -> "盘盈";
            case "CHECK_LOSS" -> "盘亏";
            case "PURCHASE_RETURN" -> "采购退货";
            case "USE_RETURN" -> "领用退回";
            default -> bizType;
        };
    }

    private String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private TraceQrCode findQr(String code) {
        String token = extractToken(code);
        if (token != null && !token.isBlank()) {
            TraceQrCode byToken = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                    .eq(TraceQrCode::getTraceToken, token).last("LIMIT 1"));
            if (byToken != null) {
                return byToken;
            }
            code = token;
        }
        TraceQrCode byCode = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                .eq(TraceQrCode::getQrCode, code).last("LIMIT 1"));
        if (byCode != null) {
            return byCode;
        }
        return null;
    }

    private TraceQrCode findStructured(String code, Resolved out) {
        String matRaw = null;
        String batchRaw = null;
        String snRaw = null;
        for (String part : code.split("\\|")) {
            String p = part.trim();
            if (p.startsWith("MAT:")) {
                matRaw = p.substring(4);
            } else if (p.startsWith("BATCH:")) {
                batchRaw = p.substring(6);
            } else if (p.startsWith("SN:")) {
                snRaw = p.substring(3);
            }
        }
        final String mat = matRaw;
        final String batch = batchRaw;
        final String sn = snRaw;
        out.extra.put("materialKey", mat);
        out.extra.put("batchKey", batch);
        out.extra.put("serialKey", sn);
        if (sn != null && !sn.isBlank()) {
            out.setCodeType("STRUCTURED_SN");
            return qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                    .eq(TraceQrCode::getSerialNo, sn).last("LIMIT 1"));
        }
        if (mat != null && !mat.isBlank() && batch != null && !batch.isBlank()) {
            out.setCodeType("STRUCTURED_BATCH");
            final Long matId = tryLong(mat);
            LambdaQueryWrapper<TraceQrCode> qw = new LambdaQueryWrapper<TraceQrCode>()
                    .eq(TraceQrCode::getBatchNo, batch)
                    .and(w -> w.eq(TraceQrCode::getMaterialCode, mat).or().eq(TraceQrCode::getMaterialId, matId))
                    .last("LIMIT 1");
            return qrMapper.selectOne(qw);
        }
        if (mat != null && !mat.isBlank()) {
            out.setCodeType("STRUCTURED_MAT");
            final Long matId = tryLong(mat);
            LambdaQueryWrapper<TraceQrCode> qw = new LambdaQueryWrapper<TraceQrCode>()
                    .and(w -> w.eq(TraceQrCode::getMaterialCode, mat).or().eq(TraceQrCode::getMaterialId, matId))
                    .last("LIMIT 1");
            return qrMapper.selectOne(qw);
        }
        throw new BizException("结构化解析失败");
    }

    private String extractToken(String code) {
        String c = code.trim();
        int idx = c.toLowerCase(Locale.ROOT).indexOf("/trace/");
        if (idx >= 0) {
            String token = c.substring(idx + "/trace/".length());
            int cut = token.indexOf('?');
            if (cut >= 0) {
                token = token.substring(0, cut);
            }
            cut = token.indexOf('#');
            if (cut >= 0) {
                token = token.substring(0, cut);
            }
            return token.trim();
        }
        return c;
    }

    private String normalize(String raw) {
        return raw.trim();
    }

    private static Long tryLong(String v) {
        try {
            return Long.valueOf(v.trim());
        } catch (Exception e) {
            return -1L;
        }
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
