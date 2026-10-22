package com.relex.wuzi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.ai.service.AiLookupService;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolRegistry;
import com.relex.wuzi.ai.tool.impl.QueryAlertsTool;
import com.relex.wuzi.ai.tool.impl.QueryBillTool;
import com.relex.wuzi.ai.tool.impl.QueryMyTodosTool;
import com.relex.wuzi.ai.tool.impl.QueryPriceTrendTool;
import com.relex.wuzi.ai.tool.impl.QueryStockBatchesTool;
import com.relex.wuzi.ai.tool.impl.QueryStockTool;
import com.relex.wuzi.ai.tool.impl.QuerySupplierTool;
import com.relex.wuzi.ai.tool.impl.ReportSummaryTool;
import com.relex.wuzi.mapper.AdvancedReportMapper;
import com.relex.wuzi.mapper.ApprovalInstanceMapper;
import com.relex.wuzi.mapper.BorrowOrderMapper;
import com.relex.wuzi.mapper.InboundOrderMapper;
import com.relex.wuzi.mapper.InventoryCheckMapper;
import com.relex.wuzi.mapper.MaterialUseApplyMapper;
import com.relex.wuzi.mapper.OutboundOrderMapper;
import com.relex.wuzi.mapper.PurchaseApplyMapper;
import com.relex.wuzi.mapper.PurchaseArrivalMapper;
import com.relex.wuzi.mapper.PurchaseContractMapper;
import com.relex.wuzi.mapper.PurchaseInquiryMapper;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.PurchaseReturnMapper;
import com.relex.wuzi.mapper.ReturnStoreOrderMapper;
import com.relex.wuzi.mapper.ScrapOrderMapper;
import com.relex.wuzi.mapper.StockBatchMapper;
import com.relex.wuzi.mapper.StockMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import com.relex.wuzi.mapper.SupplierProductPriceMapper;
import com.relex.wuzi.mapper.TransferOrderMapper;
import com.relex.wuzi.service.FlowApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 工具注册表：数量/schema 完整性 + 角色可见性矩阵 + 参数校验。
 */
class AiToolRegistryTest {

    private ToolRegistry registry;

    private static ToolContext ctx(String... roles) {
        return new ToolContext(1L, "tester", List.of(roles));
    }

    @BeforeEach
    void setUp() {
        AiLookupService lookup = mock(AiLookupService.class);
        List<com.relex.wuzi.ai.tool.AiTool> tools = List.of(
                new QueryStockTool(lookup, mock(StockMapper.class)),
                new QueryStockBatchesTool(lookup, mock(StockBatchMapper.class)),
                new QueryBillTool(new ObjectMapper(),
                        mock(PurchaseApplyMapper.class), mock(PurchaseOrderMapper.class),
                        mock(PurchaseInquiryMapper.class), mock(PurchaseArrivalMapper.class),
                        mock(PurchaseReturnMapper.class), mock(InboundOrderMapper.class),
                        mock(OutboundOrderMapper.class), mock(TransferOrderMapper.class),
                        mock(InventoryCheckMapper.class), mock(MaterialUseApplyMapper.class),
                        mock(BorrowOrderMapper.class), mock(ReturnStoreOrderMapper.class),
                        mock(ScrapOrderMapper.class), mock(PurchaseContractMapper.class)),
                new QueryMyTodosTool(mock(ApprovalInstanceMapper.class), mock(FlowApprovalService.class)),
                new QueryAlertsTool(mock(StockMapper.class), mock(StockBatchMapper.class)),
                new QueryPriceTrendTool(lookup, mock(SupplierProductPriceMapper.class),
                        mock(PurchaseOrderItemMapper.class), mock(PurchaseOrderMapper.class)),
                new ReportSummaryTool(mock(AdvancedReportMapper.class)),
                new QuerySupplierTool(lookup, mock(SupplierProductMapper.class),
                        mock(PurchaseOrderMapper.class), mock(PurchaseReturnMapper.class)));
        registry = new ToolRegistry(tools, new ObjectMapper());
    }

    @Test
    void allEightToolsRegistered() {
        assertEquals(8, registry.size());
        for (String name : List.of("query_stock", "query_stock_batches", "query_bill", "query_my_todos",
                "query_alerts", "query_price_trend", "report_summary", "query_supplier")) {
            assertNotNull(registry.byName(name), "missing tool " + name);
            assertNotNull(registry.byName(name).jsonSchema().get("properties"), name + " schema incomplete");
            assertNotNull(registry.byName(name).description());
        }
    }

    @Test
    void employeeSeesMinimalSet() {
        ToolContext employee = ctx("EMPLOYEE");
        assertTrue(registry.byName("query_stock").allowedFor(employee));
        assertTrue(registry.byName("query_alerts").allowedFor(employee));
        assertFalse(registry.byName("report_summary").allowedFor(employee));
        assertFalse(registry.byName("query_price_trend").allowedFor(employee));
        assertFalse(registry.byName("query_supplier").allowedFor(employee));
        assertFalse(registry.byName("query_my_todos").allowedFor(employee));
    }

    @Test
    void financeSeesEverything() {
        ToolContext finance = ctx("FINANCE");
        for (String name : List.of("query_stock", "report_summary", "query_price_trend",
                "query_supplier", "query_my_todos")) {
            assertTrue(registry.byName(name).allowedFor(finance), name + " should be visible to FINANCE");
        }
    }

    @Test
    void warehouseSeesReportsButNotSuppliers() {
        ToolContext wh = ctx("WAREHOUSE");
        assertTrue(registry.byName("report_summary").allowedFor(wh));
        assertFalse(registry.byName("query_supplier").allowedFor(wh));
    }

    @Test
    void adminAllowedForAll() {
        ToolContext admin = ctx("ADMIN");
        for (com.relex.wuzi.ai.tool.AiTool t : List.of(
                registry.byName("query_stock"), registry.byName("query_my_todos"),
                registry.byName("report_summary"), registry.byName("query_supplier"))) {
            assertTrue(t.allowedFor(admin));
        }
    }

    @Test
    void visibleDefsFilteredByRole() {
        List<Map<String, Object>> defs = registry.visibleDefs(ctx("EMPLOYEE"));
        List<String> names = defs.stream()
                .map(d -> String.valueOf(((Map<?, ?>) d.get("function")).get("name")))
                .toList();
        assertTrue(names.contains("query_stock"));
        assertFalse(names.contains("report_summary"));
        assertEquals(4, names.size()); // stock / batches / bill / alerts
    }

    @Test
    void validateArgsRejectsMissingRequired() {
        var tool = registry.byName("query_stock");
        assertThrows(IllegalArgumentException.class,
                () -> registry.validateArgs(tool, Map.of()));
    }

    @Test
    void validateArgsRejectsWrongType() {
        var tool = registry.byName("query_stock");
        assertThrows(IllegalArgumentException.class,
                () -> registry.validateArgs(tool, Map.of("materialName", "A4纸", "page", "abc")));
    }

    @Test
    void validateArgsAcceptsValid() {
        var tool = registry.byName("query_stock");
        registry.validateArgs(tool, Map.of("materialName", "A4纸", "page", 2));
    }

    @Test
    void parseArgsRejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> registry.parseArgs("{bad json"));
    }

    @Test
    void parseArgsAcceptsEmpty() {
        assertTrue(registry.parseArgs(null).isEmpty());
    }
}
