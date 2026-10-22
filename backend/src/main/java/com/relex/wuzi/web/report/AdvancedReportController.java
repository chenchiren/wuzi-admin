package com.relex.wuzi.web.report;

import com.relex.wuzi.common.R;
import com.relex.wuzi.mapper.AdvancedReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 高级报表：趋势 / 占比 / 排行 + CSV 导出。
 * /api/reports/*
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class AdvancedReportController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AdvancedReportMapper reportMapper;

    /** 月度采购金额趋势 */
    @GetMapping("/purchase-monthly")
    public R<List<Map<String, Object>>> purchaseMonthly(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate[] range = resolveRange(months, start, end);
        Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : reportMapper.purchaseMonthly(range[0], range[1])) {
            byMonth.put(String.valueOf(row.get("month")), toDecimal(row.get("amount")));
        }
        List<Map<String, Object>> data = fillMonths(range[0], range[1]);
        for (Map<String, Object> row : data) {
            row.put("amount", byMonth.getOrDefault(String.valueOf(row.get("month")), BigDecimal.ZERO));
        }
        return R.ok(data);
    }

    /** 入出库趋势（月度） */
    @GetMapping("/inout-monthly")
    public R<List<Map<String, Object>>> inoutMonthly(
            @RequestParam(defaultValue = "12") int months,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate[] range = resolveRange(months, start, end);
        Map<String, Map<String, Object>> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : reportMapper.inoutMonthly(range[0].atStartOfDay(), range[1].atStartOfDay())) {
            String month = String.valueOf(row.get("month"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("inQty", toDecimal(row.get("inQty")));
            m.put("outQty", toDecimal(row.get("outQty")));
            byMonth.put(month, m);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : fillMonths(range[0], range[1])) {
            String month = String.valueOf(row.get("month"));
            Map<String, Object> m = byMonth.get(month);
            if (m == null) {
                m = new LinkedHashMap<>();
                m.put("month", month);
                m.put("inQty", BigDecimal.ZERO);
                m.put("outQty", BigDecimal.ZERO);
            }
            data.add(m);
        }
        return R.ok(data);
    }

    /** 物资分类库存占比 */
    @GetMapping("/category-stock")
    public R<List<Map<String, Object>>> categoryStock() {
        return R.ok(withRatio(reportMapper.categoryStock(), "amount"));
    }

    /** 仓库库存占比 */
    @GetMapping("/warehouse-stock")
    public R<List<Map<String, Object>>> warehouseStock() {
        return R.ok(withRatio(reportMapper.warehouseStock(), "amount"));
    }

    /** 部门领用排行 */
    @GetMapping("/dept-use")
    public R<List<Map<String, Object>>> deptUse(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(reportMapper.deptUseRank(clampLimit(limit)));
    }

    /** 供应商采购排行 */
    @GetMapping("/supplier-rank")
    public R<List<Map<String, Object>>> supplierRank(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(reportMapper.supplierRank(clampLimit(limit)));
    }

    /** 导出 CSV：type = purchase-monthly | inout-monthly | category-stock | warehouse-stock | dept-use | supplier-rank */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String type,
                                         @RequestParam(defaultValue = "12") int months,
                                         @RequestParam(defaultValue = "10") int limit,
                                         @RequestParam(required = false) String start,
                                         @RequestParam(required = false) String end) {
        String key = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        List<String> headers;
        List<List<String>> bodyRows;
        switch (key) {
            case "purchase-monthly", "purchase_monthly" -> {
                headers = List.of("月份", "采购金额");
                bodyRows = purchaseMonthly(months, start, end).getData().stream()
                        .map(row -> List.of(str(row.get("month")), str(row.get("amount"))))
                        .toList();
            }
            case "inout-monthly", "inout_monthly" -> {
                headers = List.of("月份", "入库量", "出库量");
                bodyRows = inoutMonthly(months, start, end).getData().stream()
                        .map(row -> List.of(str(row.get("month")), str(row.get("inQty")), str(row.get("outQty"))))
                        .toList();
            }
            case "category-stock", "category_stock" -> {
                headers = List.of("分类", "数量", "金额", "占比%");
                bodyRows = categoryStock().getData().stream()
                        .map(row -> List.of(str(row.get("name")), str(row.get("qty")), str(row.get("amount")), str(row.get("ratio"))))
                        .toList();
            }
            case "warehouse-stock", "warehouse_stock" -> {
                headers = List.of("仓库", "数量", "金额", "占比%");
                bodyRows = warehouseStock().getData().stream()
                        .map(row -> List.of(str(row.get("name")), str(row.get("qty")), str(row.get("amount")), str(row.get("ratio"))))
                        .toList();
            }
            case "dept-use", "dept_use" -> {
                headers = List.of("部门", "领用数量", "申请单数");
                bodyRows = deptUse(limit).getData().stream()
                        .map(row -> List.of(str(row.get("name")), str(row.get("qty")), str(row.get("applyCount"))))
                        .toList();
            }
            case "supplier-rank", "supplier_rank" -> {
                headers = List.of("供应商", "编码", "采购金额", "订单数");
                bodyRows = supplierRank(limit).getData().stream()
                        .map(row -> List.of(str(row.get("name")), str(row.get("code")), str(row.get("amount")), str(row.get("orderCount"))))
                        .toList();
            }
            default -> throw new com.relex.wuzi.common.BizException("不支持的导出类型：" + type);
        }
        String csv = toCsv(headers, bodyRows);
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        String filename = "report-" + key + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    // ---------- 内部 ----------

    private String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(this::csvEscape).toList())).append("\r\n");
        for (List<String> cells : rows) {
            sb.append(String.join(",", cells.stream().map(this::csvEscape).toList())).append("\r\n");
        }
        return sb.toString();
    }

    private String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        String v = s;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private LocalDate[] resolveRange(int months, String start, String end) {
        if (start != null && !start.isBlank() && end != null && !end.isBlank()) {
            LocalDate s = YearMonth.parse(start).atDay(1);
            LocalDate e = YearMonth.parse(end).plusMonths(1).atDay(1);
            return new LocalDate[]{s, e};
        }
        int n = Math.max(1, Math.min(months, 36));
        YearMonth current = YearMonth.now();
        YearMonth from = current.minusMonths(n - 1);
        return new LocalDate[]{from.atDay(1), current.plusMonths(1).atDay(1)};
    }

    private List<Map<String, Object>> fillMonths(LocalDate start, LocalDate endExclusive) {
        List<Map<String, Object>> list = new ArrayList<>();
        YearMonth cur = YearMonth.from(start);
        YearMonth end = YearMonth.from(endExclusive.minusDays(1));
        while (!cur.isAfter(end)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", cur.format(MONTH_FMT));
            list.add(row);
            cur = cur.plusMonths(1);
        }
        return list;
    }

    private List<Map<String, Object>> withRatio(List<Map<String, Object>> rows, String amountKey) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            total = total.add(toDecimal(row.get(amountKey)));
        }
        for (Map<String, Object> row : rows) {
            BigDecimal amount = toDecimal(row.get(amountKey));
            BigDecimal ratio = total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : amount.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP);
            row.put("ratio", ratio);
        }
        return rows;
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
