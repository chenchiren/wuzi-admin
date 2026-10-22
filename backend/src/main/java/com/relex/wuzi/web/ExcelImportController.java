package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.service.ExcelImportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Excel 导入中心：上传预览 → 确认导入。
 * POST /api/import/suppliers | /supplier-products | /materials | /opening-stocks
 * POST .../confirm
 * GET  /api/import/records
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    @RequirePerm("sys:import:create")
    @PostMapping("/suppliers")
    public R<ExcelImportService.ImportBatch> previewSuppliers(@RequestParam("file") MultipartFile file) {
        return R.ok(excelImportService.previewSuppliers(file));
    }

    @Data
    public static class ConfirmReq {
        private String batchId;
    }

    @RequirePerm("sys:import:confirm")
    @PostMapping("/suppliers/confirm")
    public R<ExcelImportService.ImportResult> confirmSuppliers(@RequestBody ConfirmReq req,
                                                               @RequestAttribute(value = "userId", required = false) Long userId,
                                                               @RequestAttribute(value = "username", required = false) String username,
                                                               HttpServletRequest request) {
        return R.ok(excelImportService.confirmSuppliers(req.getBatchId(), request, userId, username));
    }

    @RequirePerm("sys:import:create")
    @PostMapping("/supplier-products")
    public R<ExcelImportService.ImportBatch> previewSupplierProducts(@RequestParam("file") MultipartFile file) {
        return R.ok(excelImportService.previewSupplierProducts(file));
    }

    @RequirePerm("sys:import:confirm")
    @PostMapping("/supplier-products/confirm")
    public R<ExcelImportService.ImportResult> confirmSupplierProducts(@RequestBody ConfirmReq req,
                                                                      @RequestAttribute(value = "userId", required = false) Long userId,
                                                                      @RequestAttribute(value = "username", required = false) String username,
                                                                      HttpServletRequest request) {
        return R.ok(excelImportService.confirmSupplierProducts(req.getBatchId(), request, userId, username));
    }

    /** 物资档案导入 */
    @RequirePerm("sys:import:create")
    @PostMapping("/materials")
    public R<ExcelImportService.ImportBatch> previewMaterials(@RequestParam("file") MultipartFile file) {
        return R.ok(excelImportService.previewMaterials(file));
    }

    @RequirePerm("sys:import:confirm")
    @PostMapping("/materials/confirm")
    public R<ExcelImportService.ImportResult> confirmMaterials(@RequestBody ConfirmReq req,
                                                               @RequestAttribute(value = "userId", required = false) Long userId,
                                                               @RequestAttribute(value = "username", required = false) String username,
                                                               HttpServletRequest request) {
        return R.ok(excelImportService.confirmMaterials(req.getBatchId(), request, userId, username));
    }

    /** 期初库存导入 */
    @RequirePerm("sys:import:create")
    @PostMapping("/opening-stocks")
    public R<ExcelImportService.ImportBatch> previewOpeningStocks(@RequestParam("file") MultipartFile file) {
        return R.ok(excelImportService.previewOpeningStocks(file));
    }

    @RequirePerm("sys:import:confirm")
    @PostMapping("/opening-stocks/confirm")
    public R<ExcelImportService.ImportResult> confirmOpeningStocks(@RequestBody ConfirmReq req,
                                                                   @RequestAttribute(value = "userId", required = false) Long userId,
                                                                   @RequestAttribute(value = "username", required = false) String username,
                                                                   HttpServletRequest request) {
        return R.ok(excelImportService.confirmOpeningStocks(req.getBatchId(), request, userId, username));
    }

    @GetMapping("/records")
    public R<PageResult<ExcelImportService.ImportRecordView>> records(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String dataType) {
        return R.ok(excelImportService.pageRecords(page, size, dataType));
    }

    @GetMapping("/templates")
    public R<List<Map<String, Object>>> templateHeaders(@RequestParam(defaultValue = "SUPPLIER") String dataType) {
        return R.ok(excelImportService.templateHeaders(dataType));
    }

    @GetMapping("/templates/download")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam(defaultValue = "SUPPLIER") String dataType)
            throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        excelImportService.writeTemplate(dataType, out);
        String filename = templateFilename(dataType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(out.toByteArray());
    }

    private String templateFilename(String dataType) {
        if (ExcelImportService.TYPE_SUPPLIER_PRODUCT.equalsIgnoreCase(dataType)) {
            return "supplier-product-import-template.xlsx";
        }
        if (ExcelImportService.TYPE_MATERIAL.equalsIgnoreCase(dataType)) {
            return "material-import-template.xlsx";
        }
        if (ExcelImportService.TYPE_OPENING_STOCK.equalsIgnoreCase(dataType)) {
            return "opening-stock-import-template.xlsx";
        }
        return "supplier-import-template.xlsx";
    }
}
