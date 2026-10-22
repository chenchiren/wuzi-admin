package com.relex.wuzi.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.ImportBatchBiz;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.MaterialCategory;
import com.relex.wuzi.domain.Stock;
import com.relex.wuzi.domain.StockRecord;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierProduct;
import com.relex.wuzi.domain.SysOperationLog;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.mapper.ImportBatchBizMapper;
import com.relex.wuzi.mapper.MaterialCategoryMapper;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.StockMapper;
import com.relex.wuzi.mapper.StockRecordMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import com.relex.wuzi.mapper.WarehouseMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供应商 / 供应商产品 Excel 导入：解析 → 校验 → 预览 → 确认导入。
 * 模板列见设计说明 8.2 / 8.3（含税价 + 税率，见 12A.11）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    public static final String TYPE_SUPPLIER = "SUPPLIER";
    public static final String TYPE_SUPPLIER_PRODUCT = "SUPPLIER_PRODUCT";
    public static final String TYPE_MATERIAL = "MATERIAL";
    public static final String TYPE_OPENING_STOCK = "OPENING_STOCK";
    public static final String LOG_MODULE = "ExcelImport";

    private static final long BATCH_TTL_MINUTES = 30;
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.01");

    private final SupplierMapper supplierMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper materialCategoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final ImportBatchBizMapper importBatchBizMapper;
    private final PriceTaxCalculator calculator;
    private final OperationLogService operationLogService;
    private final StockService stockService;

    private final Map<String, ImportBatch> batches = new ConcurrentHashMap<>();
    /** 已确认导入的结果（含错误明细），供导入中心查询/回滚 */
    private final Map<String, ImportResult> completedResults = new ConcurrentHashMap<>();
    /** 已回滚（标记失败）的批次 */
    private final Map<String, LocalDateTime> rolledBackBatches = new ConcurrentHashMap<>();

    // ---------- Excel 模板行（设计说明 8.2 / 8.3） ----------

    @Data
    public static class SupplierExcelRow {
        @ExcelProperty(value = "供应商编码", index = 0)
        private String supplierCode;
        @ExcelProperty(value = "供应商名称", index = 1)
        private String supplierName;
        @ExcelProperty(value = "联系人", index = 2)
        private String contactName;
        @ExcelProperty(value = "联系电话", index = 3)
        private String contactPhone;
        @ExcelProperty(value = "地址", index = 4)
        private String address;
        @ExcelProperty(value = "税率", index = 5)
        private String taxRate;
        @ExcelProperty(value = "结算方式", index = 6)
        private String settleType;
    }

    @Data
    public static class SupplierProductExcelRow {
        @ExcelProperty(value = "供应商编码", index = 0)
        private String supplierCode;
        @ExcelProperty(value = "产品编码", index = 1)
        private String productCode;
        @ExcelProperty(value = "产品名称", index = 2)
        private String productName;
        @ExcelProperty(value = "规格型号", index = 3)
        private String spec;
        @ExcelProperty(value = "品牌", index = 4)
        private String brand;
        @ExcelProperty(value = "单位", index = 5)
        private String unit;
        @ExcelProperty(value = "含税采购价", index = 6)
        private String taxInclusivePrice;
        @ExcelProperty(value = "税率", index = 7)
        private String taxRate;
        @ExcelProperty(value = "不含税价（可选）", index = 8)
        private String taxExclusivePrice;
    }

    /** 物资档案导入行 */
    @Data
    public static class MaterialExcelRow {
        @ExcelProperty(value = "物资编码", index = 0)
        private String materialCode;
        @ExcelProperty(value = "物资名称", index = 1)
        private String materialName;
        @ExcelProperty(value = "分类编码", index = 2)
        private String categoryCode;
        @ExcelProperty(value = "规格型号", index = 3)
        private String spec;
        @ExcelProperty(value = "品牌", index = 4)
        private String brand;
        @ExcelProperty(value = "单位", index = 5)
        private String unit;
        @ExcelProperty(value = "条码", index = 6)
        private String barcode;
        @ExcelProperty(value = "安全库存", index = 7)
        private String safetyStock;
        @ExcelProperty(value = "参考价", index = 8)
        private String refPrice;
        @ExcelProperty(value = "参考税率", index = 9)
        private String refTaxRate;
        @ExcelProperty(value = "备注", index = 10)
        private String remark;
    }

    /** 期初库存导入行 */
    @Data
    public static class OpeningStockExcelRow {
        @ExcelProperty(value = "仓库编码", index = 0)
        private String warehouseCode;
        @ExcelProperty(value = "物资编码", index = 1)
        private String materialCode;
        @ExcelProperty(value = "批次号", index = 2)
        private String batchNo;
        @ExcelProperty(value = "数量", index = 3)
        private String qty;
        @ExcelProperty(value = "含税单价", index = 4)
        private String taxInclPrice;
        @ExcelProperty(value = "税率", index = 5)
        private String taxRate;
        @ExcelProperty(value = "备注", index = 6)
        private String remark;
    }

    // ---------- 预览 / 批次模型 ----------

    @Data
    public static class RowResult {
        private int rowIndex;
        private boolean valid;
        private String message;
        private Map<String, Object> data = new LinkedHashMap<>();
    }

    @Data
    public static class ImportBatch {
        private String batchId;
        private String dataType;
        private String fileName;
        private int total;
        private int validCount;
        private int errorCount;
        private List<RowResult> rows = new ArrayList<>();
        private List<Object> payloads = new ArrayList<>();
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Data
    public static class ImportResult {
        private String batchId;
        private String dataType;
        private int total;
        private int success;
        private int failed;
        private List<String> errors = new ArrayList<>();
    }

    @Data
    public static class ImportRecordView {
        private Long id;
        private Long operatorId;
        private String operatorName;
        private String module;
        private String action;
        private String requestUri;
        private String params;
        private String result;
        private String ip;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ErrorRow {
        private Integer rowIndex;
        private String message;
        private Map<String, Object> data;
    }

    // ---------- 供应商导入 ----------

    public ImportBatch previewSuppliers(MultipartFile file) {
        List<SupplierExcelRow> rows = readExcel(file, SupplierExcelRow.class);
        ImportBatch batch = newBatch(TYPE_SUPPLIER, file.getOriginalFilename());
        int excelRow = 1;
        for (SupplierExcelRow row : rows) {
            excelRow++;
            RowResult rr = new RowResult();
            rr.setRowIndex(excelRow);
            if (isBlankRow(row.getSupplierCode(), row.getSupplierName())) {
                continue;
            }
            try {
                Supplier s = buildSupplier(row);
                rr.setValid(true);
                rr.setMessage("可导入");
                rr.getData().put("supplierCode", s.getSupplierCode());
                rr.getData().put("supplierName", s.getSupplierName());
                rr.getData().put("contactName", s.getContactName());
                rr.getData().put("contactPhone", s.getContactPhone());
                rr.getData().put("address", s.getAddress());
                rr.getData().put("defaultTaxRate", s.getDefaultTaxRate());
                rr.getData().put("settleType", s.getSettleType());
                batch.getRows().add(rr);
                batch.getPayloads().add(s);
            } catch (BizException e) {
                rr.setValid(false);
                rr.setMessage("第 " + excelRow + " 行：" + e.getMessage());
                rr.getData().put("supplierCode", row.getSupplierCode());
                rr.getData().put("supplierName", row.getSupplierName());
                batch.getRows().add(rr);
            }
        }
        recount(batch);
        batches.put(batch.getBatchId(), batch);
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmSuppliers(String batchId, HttpServletRequest request,
                                         Long operatorId, String operatorName) {
        ImportBatch batch = requireBatch(batchId, TYPE_SUPPLIER);
        ImportResult result = new ImportResult();
        result.setBatchId(batchId);
        result.setDataType(TYPE_SUPPLIER);
        result.setTotal(batch.getTotal());
        int success = 0;
        List<String> errors = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < batch.getRows().size(); i++) {
            RowResult rr = batch.getRows().get(i);
            if (!rr.isValid()) {
                errors.add(rr.getMessage());
                continue;
            }
            Supplier s = (Supplier) batch.getPayloads().get(i);
            try {
                Long exists = supplierMapper.selectCount(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getSupplierCode, s.getSupplierCode()));
                if (exists != null && exists > 0) {
                    throw new BizException("供应商编码 " + s.getSupplierCode() + " 已存在");
                }
                s.setIsDeleted(0);
                s.setCreatedAt(LocalDateTime.now());
                supplierMapper.insert(s);
                recordBatchBiz(batchId, TYPE_SUPPLIER, "supplier", s.getId(), null, ++seq);
                success++;
            } catch (Exception e) {
                errors.add("第 " + rr.getRowIndex() + " 行：" + e.getMessage());
            }
        }
        result.setSuccess(success);
        result.setFailed(result.getTotal() - success);
        result.setErrors(errors);
        completedResults.put(batchId, result);
        batches.remove(batchId);
        operationLogService.record(request, operatorId, operatorName, LOG_MODULE,
                TYPE_SUPPLIER + "_IMPORT",
                "batchId=" + batchId + ", total=" + result.getTotal() + ", success=" + success
                        + ", failed=" + result.getFailed(),
                success == result.getTotal() ? "SUCCESS" : "PARTIAL");
        return result;
    }

    private Supplier buildSupplier(SupplierExcelRow row) {
        if (isBlank(row.getSupplierCode())) {
            throw new BizException("供应商编码为空");
        }
        if (isBlank(row.getSupplierName())) {
            throw new BizException("供应商名称为空");
        }
        Supplier s = new Supplier();
        s.setSupplierCode(row.getSupplierCode().trim());
        s.setSupplierName(row.getSupplierName().trim());
        s.setContactName(trim(row.getContactName()));
        s.setContactPhone(trim(row.getContactPhone()));
        s.setAddress(trim(row.getAddress()));
        BigDecimal rate = parseTaxRate(row.getTaxRate());
        s.setDefaultTaxRate(rate == null ? new BigDecimal("13.00") : rate);
        s.setDefaultTaxCode("TAX-13");
        s.setSettleType(trim(row.getSettleType()));
        s.setCoopStatus("COOPERATING");
        s.setTaxQualification("GENERAL");
        s.setInvoiceType("VAT_SPECIAL");
        s.setSettlePriceIncl(1);
        return s;
    }

    // ---------- 供应商产品导入 ----------

    public ImportBatch previewSupplierProducts(MultipartFile file) {
        List<SupplierProductExcelRow> rows = readExcel(file, SupplierProductExcelRow.class);
        ImportBatch batch = newBatch(TYPE_SUPPLIER_PRODUCT, file.getOriginalFilename());
        int excelRow = 1;
        for (SupplierProductExcelRow row : rows) {
            excelRow++;
            RowResult rr = new RowResult();
            rr.setRowIndex(excelRow);
            if (isBlankRow(row.getSupplierCode(), row.getProductCode(), row.getProductName())) {
                continue;
            }
            try {
                ProductDraft draft = buildProductDraft(row);
                rr.setValid(true);
                rr.setMessage("可导入");
                rr.getData().put("supplierCode", draft.getSupplierCode());
                rr.getData().put("supplierProductCode", draft.getSupplierProductCode());
                rr.getData().put("productName", draft.getProductName());
                rr.getData().put("spec", draft.getSpec());
                rr.getData().put("brand", draft.getBrand());
                rr.getData().put("unit", draft.getUnit());
                rr.getData().put("taxInclusivePrice", draft.getTaxInclusivePrice());
                rr.getData().put("taxRate", draft.getTaxRate());
                rr.getData().put("taxExclusivePrice", draft.getTaxExclusivePrice());
                batch.getRows().add(rr);
                batch.getPayloads().add(draft);
            } catch (BizException e) {
                rr.setValid(false);
                rr.setMessage("第 " + excelRow + " 行：" + e.getMessage());
                rr.getData().put("supplierCode", row.getSupplierCode());
                rr.getData().put("productCode", row.getProductCode());
                rr.getData().put("productName", row.getProductName());
                batch.getRows().add(rr);
            }
        }
        recount(batch);
        batches.put(batch.getBatchId(), batch);
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmSupplierProducts(String batchId, HttpServletRequest request,
                                                Long operatorId, String operatorName) {
        ImportBatch batch = requireBatch(batchId, TYPE_SUPPLIER_PRODUCT);
        ImportResult result = new ImportResult();
        result.setBatchId(batchId);
        result.setDataType(TYPE_SUPPLIER_PRODUCT);
        result.setTotal(batch.getTotal());
        int success = 0;
        List<String> errors = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < batch.getRows().size(); i++) {
            RowResult rr = batch.getRows().get(i);
            if (!rr.isValid()) {
                errors.add(rr.getMessage());
                continue;
            }
            ProductDraft draft = (ProductDraft) batch.getPayloads().get(i);
            try {
                Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getSupplierCode, draft.getSupplierCode()));
                if (supplier == null) {
                    throw new BizException("产品所属供应商不存在：" + draft.getSupplierCode());
                }
                Long exists = supplierProductMapper.selectCount(new LambdaQueryWrapper<SupplierProduct>()
                        .eq(SupplierProduct::getSupplierId, supplier.getId())
                        .eq(SupplierProduct::getSupplierProductCode, draft.getSupplierProductCode()));
                if (exists != null && exists > 0) {
                    throw new BizException("供应商产品编码已存在：" + draft.getSupplierProductCode());
                }
                SupplierProduct sp = new SupplierProduct();
                sp.setSupplierId(supplier.getId());
                sp.setMaterialId(matchMaterialId(draft));
                sp.setSupplierProductCode(draft.getSupplierProductCode());
                sp.setProductName(draft.getProductName());
                sp.setSpec(draft.getSpec());
                sp.setBrand(draft.getBrand());
                sp.setUnit(draft.getUnit() == null ? "个" : draft.getUnit());
                sp.setPriceTaxMode("INCLUSIVE");
                sp.setCurrency("CNY");
                sp.setStatus(1);
                sp.setIsDeleted(0);
                sp.setCreatedAt(LocalDateTime.now());
                sp.setTaxRate(draft.getTaxRate());
                PriceTaxCalculator.Line line = calculator.fromInclusive(
                        draft.getTaxInclusivePrice(), draft.getTaxRate(), BigDecimal.ONE);
                sp.setTaxInclusivePrice(line.taxInclusivePrice);
                sp.setTaxExclusivePrice(line.taxExclusivePrice);
                sp.setTaxUnitAmount(line.taxUnitAmount);
                supplierProductMapper.insert(sp);
                recordBatchBiz(batchId, TYPE_SUPPLIER_PRODUCT, "supplier_product", sp.getId(), null, ++seq);
                success++;
            } catch (Exception e) {
                errors.add("第 " + rr.getRowIndex() + " 行：" + e.getMessage());
            }
        }
        result.setSuccess(success);
        result.setFailed(result.getTotal() - success);
        result.setErrors(errors);
        completedResults.put(batchId, result);
        batches.remove(batchId);
        operationLogService.record(request, operatorId, operatorName, LOG_MODULE,
                TYPE_SUPPLIER_PRODUCT + "_IMPORT",
                "batchId=" + batchId + ", total=" + result.getTotal() + ", success=" + success
                        + ", failed=" + result.getFailed(),
                success == result.getTotal() ? "SUCCESS" : "PARTIAL");
        return result;
    }

    @Data
    public static class ProductDraft {
        private String supplierCode;
        private String supplierProductCode;
        private String productName;
        private String spec;
        private String brand;
        private String unit;
        private BigDecimal taxInclusivePrice;
        private BigDecimal taxRate;
        private BigDecimal taxExclusivePrice;
    }

    private ProductDraft buildProductDraft(SupplierProductExcelRow row) {
        if (isBlank(row.getSupplierCode())) {
            throw new BizException("供应商编码为空");
        }
        if (isBlank(row.getProductCode())) {
            throw new BizException("产品编码为空");
        }
        if (isBlank(row.getProductName())) {
            throw new BizException("产品名称为空");
        }
        BigDecimal incl = parseDecimal(row.getTaxInclusivePrice(), "含税采购价");
        if (incl == null || incl.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("含税采购价必须大于0");
        }
        BigDecimal rate = parseTaxRate(row.getTaxRate());
        if (rate == null) {
            throw new BizException("税率格式错误");
        }
        ProductDraft d = new ProductDraft();
        d.setSupplierCode(row.getSupplierCode().trim());
        d.setSupplierProductCode(row.getProductCode().trim());
        d.setProductName(row.getProductName().trim());
        d.setSpec(trim(row.getSpec()));
        d.setBrand(trim(row.getBrand()));
        d.setUnit(trim(row.getUnit()));
        d.setTaxInclusivePrice(incl);
        d.setTaxRate(rate);

        PriceTaxCalculator.Line line = calculator.fromInclusive(incl, rate, BigDecimal.ONE);
        if (!isBlank(row.getTaxExclusivePrice())) {
            BigDecimal excl = parseDecimal(row.getTaxExclusivePrice(), "不含税价");
            d.setTaxExclusivePrice(excl);
            if (line.taxExclusivePrice.subtract(excl).abs().compareTo(PRICE_TOLERANCE) > 0) {
                throw new BizException("不含税价与含税价换算不一致");
            }
        } else {
            d.setTaxExclusivePrice(line.taxExclusivePrice);
        }
        return d;
    }

    // ---------- 物资档案导入 ----------

    public ImportBatch previewMaterials(MultipartFile file) {
        List<MaterialExcelRow> rows = readExcel(file, MaterialExcelRow.class);
        ImportBatch batch = newBatch(TYPE_MATERIAL, file.getOriginalFilename());
        int excelRow = 1;
        for (MaterialExcelRow row : rows) {
            excelRow++;
            RowResult rr = new RowResult();
            rr.setRowIndex(excelRow);
            if (isBlankRow(row.getMaterialCode(), row.getMaterialName())) {
                continue;
            }
            try {
                Material m = buildMaterial(row);
                rr.setValid(true);
                rr.setMessage("可导入");
                rr.getData().put("materialCode", m.getMaterialCode());
                rr.getData().put("materialName", m.getMaterialName());
                rr.getData().put("spec", m.getSpec());
                rr.getData().put("brand", m.getBrand());
                rr.getData().put("unit", m.getUnit());
                rr.getData().put("categoryCode", row.getCategoryCode());
                rr.getData().put("refPrice", m.getRefPrice());
                rr.getData().put("refTaxRate", m.getRefTaxRate());
                batch.getRows().add(rr);
                batch.getPayloads().add(m);
            } catch (BizException e) {
                rr.setValid(false);
                rr.setMessage("第 " + excelRow + " 行：" + e.getMessage());
                rr.getData().put("materialCode", row.getMaterialCode());
                rr.getData().put("materialName", row.getMaterialName());
                batch.getRows().add(rr);
            }
        }
        recount(batch);
        batches.put(batch.getBatchId(), batch);
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmMaterials(String batchId, HttpServletRequest request,
                                         Long operatorId, String operatorName) {
        ImportBatch batch = requireBatch(batchId, TYPE_MATERIAL);
        ImportResult result = new ImportResult();
        result.setBatchId(batchId);
        result.setDataType(TYPE_MATERIAL);
        result.setTotal(batch.getTotal());
        int success = 0;
        List<String> errors = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < batch.getRows().size(); i++) {
            RowResult rr = batch.getRows().get(i);
            if (!rr.isValid()) {
                errors.add(rr.getMessage());
                continue;
            }
            Material m = (Material) batch.getPayloads().get(i);
            try {
                Long exists = materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, m.getMaterialCode()));
                if (exists != null && exists > 0) {
                    throw new BizException("物资编码 " + m.getMaterialCode() + " 已存在");
                }
                // 分类编码可选：匹配不到置空
                MaterialCategory cat = findCategory(batch.getRows().get(i).getData().get("categoryCode"));
                m.setCategoryId(cat == null ? null : cat.getId());
                m.setIsDeleted(0);
                m.setCreatedAt(LocalDateTime.now());
                materialMapper.insert(m);
                recordBatchBiz(batchId, TYPE_MATERIAL, "material", m.getId(), null, ++seq);
                success++;
            } catch (Exception e) {
                errors.add("第 " + rr.getRowIndex() + " 行：" + e.getMessage());
            }
        }
        result.setSuccess(success);
        result.setFailed(result.getTotal() - success);
        result.setErrors(errors);
        completedResults.put(batchId, result);
        batches.remove(batchId);
        operationLogService.record(request, operatorId, operatorName, LOG_MODULE,
                TYPE_MATERIAL + "_IMPORT",
                "batchId=" + batchId + ", total=" + result.getTotal() + ", success=" + success
                        + ", failed=" + result.getFailed(),
                success == result.getTotal() ? "SUCCESS" : "PARTIAL");
        return result;
    }

    private Material buildMaterial(MaterialExcelRow row) {
        if (isBlank(row.getMaterialCode())) {
            throw new BizException("物资编码为空");
        }
        if (isBlank(row.getMaterialName())) {
            throw new BizException("物资名称为空");
        }
        Material m = new Material();
        m.setMaterialCode(row.getMaterialCode().trim());
        m.setMaterialName(row.getMaterialName().trim());
        m.setSpec(trim(row.getSpec()));
        m.setBrand(trim(row.getBrand()));
        m.setUnit(isBlank(row.getUnit()) ? "个" : row.getUnit().trim());
        m.setBarcode(trim(row.getBarcode()));
        m.setBatchManaged(1);
        m.setSerialManaged(0);
        m.setStatus(1);
        m.setRemark(trim(row.getRemark()));
        BigDecimal safety = parseDecimal(row.getSafetyStock(), "安全库存");
        if (safety != null && safety.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("安全库存不能为负");
        }
        m.setSafetyStock(safety);
        BigDecimal price = parseDecimal(row.getRefPrice(), "参考价");
        if (price != null && price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("参考价不能为负");
        }
        m.setRefPrice(price);
        BigDecimal rate = parseTaxRate(row.getRefTaxRate());
        if (rate == null) {
            rate = new BigDecimal("13.00");
        }
        m.setRefTaxRate(rate);
        return m;
    }

    private MaterialCategory findCategory(Object codeObj) {
        if (codeObj == null) {
            return null;
        }
        String code = String.valueOf(codeObj).trim();
        if (code.isEmpty()) {
            return null;
        }
        return materialCategoryMapper.selectOne(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCategoryCode, code)
                .last("LIMIT 1"));
    }

    // ---------- 期初库存导入 ----------

    public ImportBatch previewOpeningStocks(MultipartFile file) {
        List<OpeningStockExcelRow> rows = readExcel(file, OpeningStockExcelRow.class);
        ImportBatch batch = newBatch(TYPE_OPENING_STOCK, file.getOriginalFilename());
        int excelRow = 1;
        for (OpeningStockExcelRow row : rows) {
            excelRow++;
            RowResult rr = new RowResult();
            rr.setRowIndex(excelRow);
            if (isBlankRow(row.getWarehouseCode(), row.getMaterialCode(), row.getQty())) {
                continue;
            }
            try {
                OpeningStockDraft d = buildOpeningStockDraft(row);
                rr.setValid(true);
                rr.setMessage("可导入");
                rr.getData().put("warehouseCode", d.getWarehouseCode());
                rr.getData().put("materialCode", d.getMaterialCode());
                rr.getData().put("batchNo", d.getBatchNo());
                rr.getData().put("qty", d.getQty());
                rr.getData().put("taxInclPrice", d.getTaxInclPrice());
                rr.getData().put("taxRate", d.getTaxRate());
                batch.getRows().add(rr);
                batch.getPayloads().add(d);
            } catch (BizException e) {
                rr.setValid(false);
                rr.setMessage("第 " + excelRow + " 行：" + e.getMessage());
                rr.getData().put("warehouseCode", row.getWarehouseCode());
                rr.getData().put("materialCode", row.getMaterialCode());
                rr.getData().put("qty", row.getQty());
                batch.getRows().add(rr);
            }
        }
        recount(batch);
        batches.put(batch.getBatchId(), batch);
        return batch;
    }

    /**
     * 确认期初库存导入：直接增库存并写 stock_record（bizType=OPENING_STOCK）。
     * 关联主键写入 import_batch_biz，供真回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult confirmOpeningStocks(String batchId, HttpServletRequest request,
                                             Long operatorId, String operatorName) {
        ImportBatch batch = requireBatch(batchId, TYPE_OPENING_STOCK);
        ImportResult result = new ImportResult();
        result.setBatchId(batchId);
        result.setDataType(TYPE_OPENING_STOCK);
        result.setTotal(batch.getTotal());
        int success = 0;
        List<String> errors = new ArrayList<>();
        int seq = 0;
        for (int i = 0; i < batch.getRows().size(); i++) {
            RowResult rr = batch.getRows().get(i);
            if (!rr.isValid()) {
                errors.add(rr.getMessage());
                continue;
            }
            OpeningStockDraft d = (OpeningStockDraft) batch.getPayloads().get(i);
            try {
                Warehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getWarehouseCode, d.getWarehouseCode())
                        .last("LIMIT 1"));
                if (wh == null) {
                    throw new BizException("仓库编码不存在：" + d.getWarehouseCode());
                }
                Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, d.getMaterialCode())
                        .last("LIMIT 1"));
                if (material == null) {
                    throw new BizException("物资编码不存在：" + d.getMaterialCode());
                }
                StockRecord rec = applyOpeningStock(wh, material, d, operatorId, batchId);
                recordBatchBiz(batchId, TYPE_OPENING_STOCK, "stock_record", rec.getId(),
                        rec.getWarehouseId(), ++seq);
                success++;
            } catch (Exception e) {
                errors.add("第 " + rr.getRowIndex() + " 行：" + e.getMessage());
            }
        }
        result.setSuccess(success);
        result.setFailed(result.getTotal() - success);
        result.setErrors(errors);
        completedResults.put(batchId, result);
        batches.remove(batchId);
        operationLogService.record(request, operatorId, operatorName, LOG_MODULE,
                TYPE_OPENING_STOCK + "_IMPORT",
                "batchId=" + batchId + ", total=" + result.getTotal() + ", success=" + success
                        + ", failed=" + result.getFailed(),
                success == result.getTotal() ? "SUCCESS" : "PARTIAL");
        return result;
    }

    /** 期初入库：统一走 StockService，保证乐观锁/流水一致 */
    private StockRecord applyOpeningStock(Warehouse wh, Material material,
                                          OpeningStockDraft d, Long operatorId, String batchId) {
        String batchNo = d.getBatchNo() == null ? "" : d.getBatchNo().trim();
        BigDecimal costIncl = d.getTaxInclPrice().multiply(d.getQty());
        StockService.StockChange cmd = new StockService.StockChange();
        cmd.setBizType("OPENING_STOCK");
        cmd.setBizNo(batchId);
        cmd.setWarehouseId(wh.getId());
        cmd.setMaterialId(material.getId());
        cmd.setBatchNo(batchNo);
        cmd.setChangeQty(d.getQty());
        cmd.setTaxInclAmount(costIncl);
        cmd.setCostTaxIncl(costIncl);
        cmd.setCostTaxExcl(BigDecimal.ZERO);
        cmd.setTaxRate(d.getTaxRate());
        cmd.setTaxInclPrice(d.getTaxInclPrice());
        cmd.setOperatorId(operatorId);
        cmd.setRemark(d.getRemark() != null ? d.getRemark() : "期初库存导入");
        return stockService.increase(cmd);
    }

    @Data
    public static class OpeningStockDraft {
        private String warehouseCode;
        private String materialCode;
        private String batchNo;
        private BigDecimal qty;
        private BigDecimal taxInclPrice;
        private BigDecimal taxRate;
        private String remark;
    }

    private OpeningStockDraft buildOpeningStockDraft(OpeningStockExcelRow row) {
        if (isBlank(row.getWarehouseCode())) {
            throw new BizException("仓库编码为空");
        }
        if (isBlank(row.getMaterialCode())) {
            throw new BizException("物资编码为空");
        }
        BigDecimal qty = parseDecimal(row.getQty(), "数量");
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("数量必须大于0");
        }
        BigDecimal price = parseDecimal(row.getTaxInclPrice(), "含税单价");
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("含税单价不能为空且不能为负");
        }
        BigDecimal rate = parseTaxRate(row.getTaxRate());
        if (rate == null) {
            rate = new BigDecimal("13.00");
        }
        OpeningStockDraft d = new OpeningStockDraft();
        d.setWarehouseCode(row.getWarehouseCode().trim());
        d.setMaterialCode(row.getMaterialCode().trim());
        d.setBatchNo(trim(row.getBatchNo()));
        d.setQty(qty);
        d.setTaxInclPrice(price);
        d.setTaxRate(rate);
        d.setRemark(trim(row.getRemark()));
        return d;
    }

    // ---------- 导入批次业务主键（真回滚支撑） ----------

    private void recordBatchBiz(String batchId, String dataType, String bizTable,
                                Long bizId, Long refId, int seq) {
        ImportBatchBiz rec = new ImportBatchBiz();
        rec.setBatchId(batchId);
        rec.setDataType(dataType);
        rec.setBizTable(bizTable);
        rec.setBizId(bizId);
        rec.setRefId(refId);
        rec.setSeq(seq);
        rec.setCreatedAt(LocalDateTime.now());
        importBatchBizMapper.insert(rec);
    }

    // ---------- 导入中心：错误明细 / 回滚（简化） ----------

    /** 查询批次错误行：预览批次优先，其次已确认结果中的错误文案。 */
    public List<ErrorRow> getBatchErrors(String batchId) {
        purgeExpired();
        List<ErrorRow> out = new ArrayList<>();
        ImportBatch batch = batches.get(batchId);
        if (batch != null) {
            for (RowResult r : batch.getRows()) {
                if (r.isValid()) {
                    continue;
                }
                ErrorRow row = new ErrorRow();
                row.setRowIndex(r.getRowIndex());
                row.setMessage(r.getMessage());
                row.setData(r.getData());
                out.add(row);
            }
            return out;
        }
        ImportResult done = completedResults.get(batchId);
        if (done != null) {
            for (String msg : done.getErrors()) {
                ErrorRow row = new ErrorRow();
                row.setMessage(msg);
                out.add(row);
            }
        }
        return out;
    }

    /** 丢弃预览批次（未确认导入时作废）。 */
    public boolean discardBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return false;
        }
        purgeExpired();
        return batches.remove(batchId) != null;
    }

    /**
     * 真回滚：按 import_batch_biz 逆序删除/作废本批次创建的业务数据。
     * MATERIAL → 逻辑删物资；OPENING_STOCK → 删 stock_record 并回退 stock/stock_record（期初）。
     * 已回滚批次重复回滚返回 false。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markRolledBack(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return false;
        }
        if (rolledBackBatches.containsKey(batchId)) {
            return false;
        }
        ImportResult done = completedResults.get(batchId);
        List<ImportBatchBiz> bizList = importBatchBizMapper.selectList(
                new LambdaQueryWrapper<ImportBatchBiz>()
                        .eq(ImportBatchBiz::getBatchId, batchId)
                        .orderByDesc(ImportBatchBiz::getSeq)
                        .orderByDesc(ImportBatchBiz::getId));
        if (done == null && bizList.isEmpty()) {
            return false;
        }
        int reverted = 0;
        for (ImportBatchBiz biz : bizList) {
            try {
                reverted += revertBiz(biz);
            } catch (Exception e) {
                log.warn("[import] 回滚失败 batchId={} table={} id={} err={}",
                        batchId, biz.getBizTable(), biz.getBizId(), e.getMessage());
            }
        }
        if (done != null) {
            done.setFailed(done.getTotal());
            done.setSuccess(0);
        }
        rolledBackBatches.put(batchId, LocalDateTime.now());
        log.info("[import] 批次已回滚 batchId={} 关联业务={} 已撤销={}", batchId, bizList.size(), reverted);
        return true;
    }

    /** 撤销单条导入业务主键；返回是否实际撤销 */
    private int revertBiz(ImportBatchBiz biz) {
        String table = biz.getBizTable() == null ? "" : biz.getBizTable().toLowerCase();
        Long bizId = biz.getBizId();
        if (bizId == null) {
            return 0;
        }
        switch (table) {
            case "material": {
                Material m = materialMapper.selectById(bizId);
                if (m == null) {
                    return 0;
                }
                // 已产生库存的物资不允许物理删，仅作废
                Long stockCnt = stockMapper.selectCount(new LambdaQueryWrapper<Stock>()
                        .eq(Stock::getMaterialId, bizId));
                if (stockCnt != null && stockCnt > 0) {
                    m.setIsDeleted(1);
                    m.setUpdatedAt(LocalDateTime.now());
                    materialMapper.updateById(m);
                } else {
                    materialMapper.deleteById(bizId);
                }
                return 1;
            }
            case "stock_record": {
                StockRecord rec = stockRecordMapper.selectById(bizId);
                if (rec == null) {
                    return 0;
                }
                // 回退期初导入：统一走 StockService 反向变更（乐观锁 + 流水）
                if (rec.getChangeQty() != null && rec.getChangeQty().compareTo(BigDecimal.ZERO) != 0) {
                    StockService.StockChange revert = new StockService.StockChange();
                    revert.setBizType("OPENING_STOCK_REVERT");
                    revert.setBizNo(rec.getBizNo());
                    revert.setBizId(rec.getId());
                    revert.setWarehouseId(rec.getWarehouseId());
                    revert.setLocationId(rec.getLocationId());
                    revert.setMaterialId(rec.getMaterialId());
                    revert.setBatchNo(rec.getBatchNo());
                    revert.setChangeQty(rec.getChangeQty().negate());
                    revert.setOperatorId(rec.getOperatorId());
                    revert.setRemark("期初导入回滚");
                    revert.setAmountSign(StockService.SIGN_NEGATIVE);
                    stockService.redFlush(revert);
                }
                stockRecordMapper.deleteById(bizId);
                return 1;
            }
            case "supplier": {
                Supplier s = supplierMapper.selectById(bizId);
                if (s == null) {
                    return 0;
                }
                supplierMapper.deleteById(bizId);
                return 1;
            }
            case "supplier_product": {
                SupplierProduct sp = supplierProductMapper.selectById(bizId);
                if (sp == null) {
                    return 0;
                }
                supplierProductMapper.deleteById(bizId);
                return 1;
            }
            default:
                log.debug("[import] 未知 biz_table={}，跳过", table);
                return 0;
        }
    }

    public boolean isRolledBack(String batchId) {
        return batchId != null && rolledBackBatches.containsKey(batchId);
    }

    // ---------- 导入记录 ----------

    public PageResult<ImportRecordView> pageRecords(long page, long size, String dataType) {
        String action = null;
        if (TYPE_SUPPLIER.equalsIgnoreCase(dataType)) {
            action = TYPE_SUPPLIER + "_IMPORT";
        } else if (TYPE_SUPPLIER_PRODUCT.equalsIgnoreCase(dataType)) {
            action = TYPE_SUPPLIER_PRODUCT + "_IMPORT";
        } else if (TYPE_MATERIAL.equalsIgnoreCase(dataType)) {
            action = TYPE_MATERIAL + "_IMPORT";
        } else if (TYPE_OPENING_STOCK.equalsIgnoreCase(dataType)) {
            action = TYPE_OPENING_STOCK + "_IMPORT";
        }
        PageResult<SysOperationLog> logs = operationLogService.page(
                page, size, null, LOG_MODULE, action, null);
        List<ImportRecordView> views = new ArrayList<>();
        for (SysOperationLog log : logs.getRecords()) {
            ImportRecordView v = new ImportRecordView();
            v.setId(log.getId());
            v.setOperatorId(log.getOperatorId());
            v.setOperatorName(log.getOperatorName());
            v.setModule(log.getModule());
            v.setAction(log.getAction());
            v.setRequestUri(log.getRequestUri());
            v.setParams(log.getParams());
            v.setResult(log.getResult());
            v.setIp(log.getIp());
            v.setCreatedAt(log.getCreatedAt());
            views.add(v);
        }
        return PageResult.of(logs.getTotal(), views);
    }

    public List<Map<String, Object>> templateHeaders(String dataType) {
        if (TYPE_SUPPLIER_PRODUCT.equalsIgnoreCase(dataType)) {
            return List.of(
                    Map.of("name", "供应商编码", "required", true),
                    Map.of("name", "产品编码", "required", true),
                    Map.of("name", "产品名称", "required", true),
                    Map.of("name", "规格型号", "required", false),
                    Map.of("name", "品牌", "required", false),
                    Map.of("name", "单位", "required", false),
                    Map.of("name", "含税采购价", "required", true),
                    Map.of("name", "税率", "required", true),
                    Map.of("name", "不含税价（可选）", "required", false));
        }
        if (TYPE_MATERIAL.equalsIgnoreCase(dataType)) {
            return List.of(
                    Map.of("name", "物资编码", "required", true),
                    Map.of("name", "物资名称", "required", true),
                    Map.of("name", "分类编码", "required", false),
                    Map.of("name", "规格型号", "required", false),
                    Map.of("name", "品牌", "required", false),
                    Map.of("name", "单位", "required", false),
                    Map.of("name", "条码", "required", false),
                    Map.of("name", "安全库存", "required", false),
                    Map.of("name", "参考价", "required", false),
                    Map.of("name", "参考税率", "required", false),
                    Map.of("name", "备注", "required", false));
        }
        if (TYPE_OPENING_STOCK.equalsIgnoreCase(dataType)) {
            return List.of(
                    Map.of("name", "仓库编码", "required", true),
                    Map.of("name", "物资编码", "required", true),
                    Map.of("name", "批次号", "required", false),
                    Map.of("name", "数量", "required", true),
                    Map.of("name", "含税单价", "required", true),
                    Map.of("name", "税率", "required", false),
                    Map.of("name", "备注", "required", false));
        }
        return List.of(
                Map.of("name", "供应商编码", "required", true),
                Map.of("name", "供应商名称", "required", true),
                Map.of("name", "联系人", "required", false),
                Map.of("name", "联系电话", "required", false),
                Map.of("name", "地址", "required", false),
                Map.of("name", "税率", "required", false),
                Map.of("name", "结算方式", "required", false));
    }

    public void writeTemplate(String dataType, java.io.OutputStream out) {
        if (TYPE_SUPPLIER_PRODUCT.equalsIgnoreCase(dataType)) {
            EasyExcel.write(out, SupplierProductExcelRow.class).sheet("供应商产品").doWrite(List.of());
        } else if (TYPE_MATERIAL.equalsIgnoreCase(dataType)) {
            EasyExcel.write(out, MaterialExcelRow.class).sheet("物资档案").doWrite(List.of());
        } else if (TYPE_OPENING_STOCK.equalsIgnoreCase(dataType)) {
            EasyExcel.write(out, OpeningStockExcelRow.class).sheet("期初库存").doWrite(List.of());
        } else {
            EasyExcel.write(out, SupplierExcelRow.class).sheet("供应商").doWrite(List.of());
        }
    }

    // ---------- 内部工具 ----------

    private <T> List<T> readExcel(MultipartFile file, Class<T> head) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请上传 Excel 文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls") && !name.endsWith(".csv")) {
            throw new BizException("仅支持 .xlsx / .xls 文件");
        }
        try {
            List<T> rows = EasyExcel.read(file.getInputStream())
                    .head(head)
                    .sheet(0)
                    .doReadSync();
            return rows == null ? List.of() : rows;
        } catch (IOException e) {
            throw new BizException("Excel 读取失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BizException("Excel 解析失败，请检查表头与格式：" + e.getMessage());
        }
    }

    private ImportBatch newBatch(String dataType, String fileName) {
        ImportBatch batch = new ImportBatch();
        batch.setBatchId(UUID.randomUUID().toString().replace("-", ""));
        batch.setDataType(dataType);
        batch.setFileName(fileName);
        purgeExpired();
        return batch;
    }

    private ImportBatch requireBatch(String batchId, String expectedType) {
        if (batchId == null || batchId.isBlank()) {
            throw new BizException("缺少 batchId，请先上传预览");
        }
        purgeExpired();
        ImportBatch batch = batches.get(batchId);
        if (batch == null) {
            throw new BizException("预览批次不存在或已过期，请重新上传");
        }
        if (!expectedType.equals(batch.getDataType())) {
            throw new BizException("批次类型不匹配");
        }
        return batch;
    }

    private void purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(BATCH_TTL_MINUTES);
        batches.values().removeIf(b -> b.getCreatedAt().isBefore(cutoff));
    }

    private void recount(ImportBatch batch) {
        batch.setTotal(batch.getRows().size());
        int valid = 0;
        for (RowResult r : batch.getRows()) {
            if (r.isValid()) {
                valid++;
            }
        }
        batch.setValidCount(valid);
        batch.setErrorCount(batch.getTotal() - valid);
    }

    private boolean isBlankRow(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private BigDecimal parseDecimal(String raw, String field) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", "").replace("￥", "").replace("¥", ""));
        } catch (NumberFormatException e) {
            throw new BizException(field + "格式错误");
        }
    }

    /**
     * 税率支持 13%、13、0.13，统一归一成两位小数百分数值（如 13.00）。
     */
    public static BigDecimal parseTaxRate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String original = raw.trim();
        boolean hasPercent = original.contains("%") || original.contains("％");
        String s = original.replace("％", "%").replace("%", "").replace(",", "").trim();
        BigDecimal v;
        try {
            v = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BizException("税率格式错误");
        }
        if (v.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("税率格式错误");
        }
        // 0.13 → 13.00（未带 % 且小于 1 时按小数百分比）
        if (!hasPercent && v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(BigDecimal.ONE) < 0) {
            v = v.multiply(new BigDecimal("100"));
        }
        if (v.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("税率格式错误");
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 尝试按产品编码 / 名称匹配系统物资档案（设计说明 8.5）。 */
    private Long matchMaterialId(ProductDraft draft) {
        var byCode = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, draft.getSupplierProductCode())
                .last("LIMIT 1"));
        if (byCode != null) {
            return byCode.getId();
        }
        if (draft.getProductName() != null) {
            var byName = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                    .eq(Material::getMaterialName, draft.getProductName().trim())
                    .last("LIMIT 1"));
            if (byName != null) {
                return byName.getId();
            }
        }
        return null;
    }
}
