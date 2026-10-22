package com.relex.wuzi.ai.tool.impl;

import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.mapper.AdvancedReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表摘要（复用 AdvancedReportMapper 的 4 个报表查询）。
 * 可见角色与 /api/reports 角色白名单一致。
 */
@Component
@RequiredArgsConstructor
public class ReportSummaryTool implements AiTool {

    private static final int MAX_ROWS = 20;

    private final AdvancedReportMapper reportMapper;

    @Override
    public String name() {
        return "report_summary";
    }

    @Override
    public String label() {
        return "报表摘要";
    }

    @Override
    public String description() {
        return "查报表数据：report=dept-use 部门领用排行、supplier-rank 供应商采购排行、purchase-monthly 月度采购金额、inout-monthly 月度出入库量。用户问「排行/趋势/多少金额」类汇总问题时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("report", Map.of("type", "string", "enum",
                List.of("dept-use", "supplier-rank", "purchase-monthly", "inout-monthly"),
                "description", "报表类型"));
        properties.put("months", Map.of("type", "integer", "description", "月度报表回看月数，默认6"));
        properties.put("limit", Map.of("type", "integer", "description", "排行条数，默认10，最大20"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("report"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return ctx.hasRole("FINANCE") || ctx.hasRole("DEPT_MANAGER")
                || ctx.hasRole("PURCHASER") || ctx.hasRole("WAREHOUSE");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String report = QueryStockTool.str(args, "report");
        int months = Math.min(24, Math.max(1, QueryStockTool.intArg(args, "months", 6)));
        int limit = Math.min(MAX_ROWS, Math.max(1, QueryStockTool.intArg(args, "limit", 10)));
        String r = report == null ? "" : report.trim().toLowerCase();

        List<Map<String, Object>> rows;
        String summary;
        switch (r) {
            case "dept-use" -> {
                rows = cap(reportMapper.deptUseRank(limit));
                summary = "部门领用排行前 " + rows.size() + " 名";
            }
            case "supplier-rank" -> {
                rows = cap(reportMapper.supplierRank(limit));
                summary = "供应商采购排行前 " + rows.size() + " 名";
            }
            case "purchase-monthly" -> {
                LocalDate start = LocalDate.now().minusMonths(months).withDayOfMonth(1);
                LocalDate end = LocalDate.now();
                rows = cap(reportMapper.purchaseMonthly(start, end));
                summary = "近 " + months + " 个月月度采购金额";
            }
            case "inout-monthly" -> {
                LocalDate start = LocalDate.now().minusMonths(months).withDayOfMonth(1);
                rows = cap(reportMapper.inoutMonthly(start.atStartOfDay(), endOfToday()));
                summary = "近 " + months + " 个月月度出入库量";
            }
            default -> {
                return ToolResult.of("未知报表类型: " + report
                        + "（支持 dept-use / supplier-rank / purchase-monthly / inout-monthly）", List.of(), "参数错误");
            }
        }
        String digest = summary + (rows.isEmpty() ? "" : "，末月/首位: " + firstDigest(rows));
        return ToolResult.of(summary, rows, digest);
    }

    private static LocalDateTime endOfToday() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    private static String firstDigest(List<Map<String, Object>> rows) {
        Map<String, Object> first = rows.get(0);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : first.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(e.getKey()).append("=").append(fmt(e.getValue()));
        }
        return sb.toString();
    }

    private static String fmt(Object v) {
        if (v instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return String.valueOf(v);
    }

    private static List<Map<String, Object>> cap(List<Map<String, Object>> in) {
        return new ArrayList<>(in.subList(0, Math.min(MAX_ROWS, in.size())));
    }
}
