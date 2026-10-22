package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.CsvExport;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 列表数据 CSV 导出：物资 / 库存 / 库存流水 / 入库 / 出库 / 采购订单 / 领用。
 * 查询条件与对应列表接口保持一致，导出上限 5000 行。
 */
@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ListExportController {

    private static final int MAX_ROWS = 5000;

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final WarehouseMapper warehouseMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final MaterialUseApplyMapper materialUseApplyMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserMapper userMapper;

    @GetMapping("/materials")
    public ResponseEntity<byte[]> exportMaterials(@RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<Material>()
                .eq(categoryId != null, Material::getCategoryId, categoryId)
                .eq(status != null, Material::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Material::getMaterialCode, keyword.trim())
                        .or().like(Material::getMaterialName, keyword.trim())
                        .or().like(Material::getSpec, keyword.trim()))
                .orderByAsc(Material::getMaterialCode)
                .last("LIMIT " + MAX_ROWS);
        List<Material> list = materialMapper.selectList(qw);
        Map<Long, String> catMap = categoryMap();
        List<String> headers = List.of("ID", "编码", "名称", "分类", "规格", "品牌", "单位", "条码",
                "批次管理", "序列管理", "安全库存", "最高库存", "参考价", "状态", "备注");
        List<List<String>> rows = list.stream().map(m -> List.of(
                CsvExport.str(m.getId()), str(m.getMaterialCode()), str(m.getMaterialName()),
                str(catMap.get(m.getCategoryId())), str(m.getSpec()), str(m.getBrand()), str(m.getUnit()),
                str(m.getBarcode()), bool(m.getBatchManaged()), bool(m.getSerialManaged()),
                CsvExport.str(m.getSafetyStock()), CsvExport.str(m.getMaxStock()), CsvExport.str(m.getRefPrice()),
                statusText(m.getStatus()), str(m.getRemark())
        )).collect(Collectors.toList());
        return CsvExport.toResponse("materials", headers, rows);
    }

    @GetMapping("/stock")
    public ResponseEntity<byte[]> exportStock(@RequestParam(required = false) Long warehouseId,
                                              @RequestParam(required = false) Long materialId,
                                              @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<Stock> qw = new LambdaQueryWrapper<Stock>()
                .eq(warehouseId != null, Stock::getWarehouseId, warehouseId)
                .eq(materialId != null, Stock::getMaterialId, materialId)
                .eq(batchNo != null && !batchNo.isBlank(), Stock::getBatchNo, batchNo)
                .orderByDesc(Stock::getId)
                .last("LIMIT " + MAX_ROWS);
        List<Stock> list = stockMapper.selectList(qw);
        Map<Long, Material> matMap = materialMap(list.stream().map(Stock::getMaterialId).toList());
        Map<Long, String> whMap = warehouseMap();
        List<String> headers = List.of("ID", "仓库", "物资编码", "物资名称", "批次", "数量", "冻结量",
                "成本含税", "成本不含税", "最近税率", "最近含税价", "更新时间");
        List<List<String>> rows = list.stream().map(s -> {
            Material m = matMap.get(s.getMaterialId());
            return List.of(CsvExport.str(s.getId()), str(whMap.get(s.getWarehouseId())),
                    m == null ? "" : str(m.getMaterialCode()), m == null ? "" : str(m.getMaterialName()),
                    str(s.getBatchNo()), CsvExport.str(s.getQty()), CsvExport.str(s.getFrozenQty()),
                    CsvExport.str(s.getCostTaxIncl()), CsvExport.str(s.getCostTaxExcl()),
                    CsvExport.str(s.getLastTaxRate()), CsvExport.str(s.getLastTaxInclPrice()),
                    CsvExport.str(s.getUpdatedAt()));
        }).collect(Collectors.toList());
        return CsvExport.toResponse("stock", headers, rows);
    }

    @GetMapping("/stock-records")
    public ResponseEntity<byte[]> exportStockRecords(@RequestParam(required = false) Long warehouseId,
                                                     @RequestParam(required = false) Long materialId,
                                                     @RequestParam(required = false) String bizType,
                                                     @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<StockRecord> qw = new LambdaQueryWrapper<StockRecord>()
                .eq(warehouseId != null, StockRecord::getWarehouseId, warehouseId)
                .eq(materialId != null, StockRecord::getMaterialId, materialId)
                .eq(bizType != null && !bizType.isBlank(), StockRecord::getBizType, bizType)
                .eq(batchNo != null && !batchNo.isBlank(), StockRecord::getBatchNo, batchNo)
                .orderByDesc(StockRecord::getId)
                .last("LIMIT " + MAX_ROWS);
        List<StockRecord> list = stockRecordMapper.selectList(qw);
        Map<Long, Material> matMap = materialMap(list.stream().map(StockRecord::getMaterialId).toList());
        Map<Long, String> whMap = warehouseMap();
        List<String> headers = List.of("ID", "时间", "仓库", "物资编码", "物资名称", "批次", "业务类型", "业务单号",
                "变动量", "变动前", "变动后", "方向", "操作人", "备注");
        List<List<String>> rows = list.stream().map(r -> {
            Material m = matMap.get(r.getMaterialId());
            return List.of(CsvExport.str(r.getId()), CsvExport.str(r.getCreatedAt()),
                    str(whMap.get(r.getWarehouseId())),
                    m == null ? "" : str(m.getMaterialCode()), m == null ? "" : str(m.getMaterialName()),
                    str(r.getBatchNo()), str(r.getBizType()), str(r.getBizNo()),
                    CsvExport.str(r.getChangeQty()), CsvExport.str(r.getBeforeQty()), CsvExport.str(r.getAfterQty()),
                    CsvExport.str(r.getAmountSign()), CsvExport.str(r.getOperatorId()), str(r.getRemark()));
        }).collect(Collectors.toList());
        return CsvExport.toResponse("stock-records", headers, rows);
    }

    @GetMapping("/inbounds")
    public ResponseEntity<byte[]> exportInbounds(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long warehouseId,
                                                 @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<InboundOrder> qw = new LambdaQueryWrapper<InboundOrder>()
                .eq(status != null && !status.isBlank(), InboundOrder::getStatus, status)
                .eq(warehouseId != null, InboundOrder::getWarehouseId, warehouseId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(InboundOrder::getInboundNo, keyword.trim())
                        .or().like(InboundOrder::getSourceNo, keyword.trim()))
                .orderByDesc(InboundOrder::getId)
                .last("LIMIT " + MAX_ROWS);
        List<InboundOrder> list = inboundOrderMapper.selectList(qw);
        Map<Long, String> whMap = warehouseMap();
        Map<Long, String> supMap = supplierMap();
        List<String> headers = List.of("ID", "入库单号", "类型", "仓库", "供应商", "来源单号", "入库时间", "状态", "备注");
        List<List<String>> rows = list.stream().map(o -> List.of(
                CsvExport.str(o.getId()), str(o.getInboundNo()), str(o.getInboundType()),
                str(whMap.get(o.getWarehouseId())), str(supMap.get(o.getSupplierId())),
                str(o.getSourceNo()), CsvExport.str(o.getInboundTime()), str(o.getStatus()), str(o.getRemark())
        )).collect(Collectors.toList());
        return CsvExport.toResponse("inbounds", headers, rows);
    }

    @GetMapping("/outbounds")
    public ResponseEntity<byte[]> exportOutbounds(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long warehouseId,
                                                  @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<OutboundOrder> qw = new LambdaQueryWrapper<OutboundOrder>()
                .eq(status != null && !status.isBlank(), OutboundOrder::getStatus, status)
                .eq(warehouseId != null, OutboundOrder::getWarehouseId, warehouseId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(OutboundOrder::getOutboundNo, keyword.trim()))
                .orderByDesc(OutboundOrder::getId)
                .last("LIMIT " + MAX_ROWS);
        List<OutboundOrder> list = outboundOrderMapper.selectList(qw);
        Map<Long, String> whMap = warehouseMap();
        Map<Long, String> userMap = userMap();
        List<String> headers = List.of("ID", "出库单号", "类型", "仓库", "领用人", "操作人", "出库时间", "状态", "备注");
        List<List<String>> rows = list.stream().map(o -> List.of(
                CsvExport.str(o.getId()), str(o.getOutboundNo()), str(o.getOutboundType()),
                str(whMap.get(o.getWarehouseId())), str(userMap.get(o.getUserId())),
                str(userMap.get(o.getOperatorId())), CsvExport.str(o.getOutboundTime()),
                str(o.getStatus()), str(o.getRemark())
        )).collect(Collectors.toList());
        return CsvExport.toResponse("outbounds", headers, rows);
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<byte[]> exportPurchaseOrders(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long supplierId,
                                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<PurchaseOrder> qw = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(PurchaseOrder::getOrderNo, keyword.trim())
                        .or().like(PurchaseOrder::getRemark, keyword.trim()))
                .orderByDesc(PurchaseOrder::getId)
                .last("LIMIT " + MAX_ROWS);
        List<PurchaseOrder> list = purchaseOrderMapper.selectList(qw);
        Map<Long, String> supMap = supplierMap();
        List<String> headers = List.of("ID", "订单号", "供应商", "订单日期", "期望到货", "含税总额", "不含税总额", "税额", "状态", "备注");
        List<List<String>> rows = list.stream().map(o -> List.of(
                CsvExport.str(o.getId()), str(o.getOrderNo()), str(supMap.get(o.getSupplierId())),
                CsvExport.str(o.getOrderDate()), CsvExport.str(o.getExpectArrivalDate()),
                CsvExport.str(o.getTaxInclusiveAmount()), CsvExport.str(o.getTaxExclusiveAmount()),
                CsvExport.str(o.getTaxAmount()), str(o.getStatus()), str(o.getRemark())
        )).collect(Collectors.toList());
        return CsvExport.toResponse("purchase-orders", headers, rows);
    }

    @GetMapping("/material-use")
    public ResponseEntity<byte[]> exportMaterialUse(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long deptId,
                                                    @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<MaterialUseApply> qw = new LambdaQueryWrapper<MaterialUseApply>()
                .eq(status != null && !status.isBlank(), MaterialUseApply::getStatus, status)
                .eq(deptId != null, MaterialUseApply::getDeptId, deptId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MaterialUseApply::getApplyNo, keyword.trim())
                        .or().like(MaterialUseApply::getProjectName, keyword.trim()))
                .orderByDesc(MaterialUseApply::getId)
                .last("LIMIT " + MAX_ROWS);
        List<MaterialUseApply> list = materialUseApplyMapper.selectList(qw);
        Map<Long, String> userMap = userMap();
        List<String> headers = List.of("ID", "申请单号", "申请人", "部门ID", "领用类型", "项目", "用途", "需求日期", "状态");
        List<List<String>> rows = list.stream().map(a -> List.of(
                CsvExport.str(a.getId()), str(a.getApplyNo()), str(userMap.get(a.getApplyUserId())),
                CsvExport.str(a.getDeptId()), str(a.getUseType()), str(a.getProjectName()),
                str(a.getPurpose()), CsvExport.str(a.getNeedDate()), str(a.getStatus())
        )).collect(Collectors.toList());
        return CsvExport.toResponse("material-use", headers, rows);
    }

    // ---------- helpers ----------

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String bool(Integer v) {
        return v != null && v == 1 ? "是" : "否";
    }

    private String statusText(Integer v) {
        if (v == null) {
            return "";
        }
        return v == 1 ? "启用" : "停用";
    }

    private Map<Long, String> categoryMap() {
        return categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(MaterialCategory::getId,
                        c -> c.getCategoryName() == null ? "" : c.getCategoryName(),
                        (a, b) -> a));
    }

    private Map<Long, Material> materialMap(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> warehouseMap() {
        return warehouseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Warehouse::getId,
                        w -> w.getWarehouseName() == null ? "" : w.getWarehouseName(),
                        (a, b) -> a));
    }

    private Map<Long, String> supplierMap() {
        return supplierMapper.selectList(null).stream()
                .collect(Collectors.toMap(Supplier::getId,
                        s -> s.getSupplierName() == null ? "" : s.getSupplierName(),
                        (a, b) -> a));
    }

    private Map<Long, String> userMap() {
        return userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() == null ? u.getUsername() : u.getRealName(),
                        (a, b) -> a));
    }
}
