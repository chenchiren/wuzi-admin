package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierProduct;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BasicDataController {

    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final PriceTaxCalculator calculator;

    @GetMapping("/materials")
    public R<PageResult<Material>> pageMaterial(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Long categoryId) {
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<Material>()
                .eq(categoryId != null, Material::getCategoryId, categoryId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Material::getMaterialName, keyword)
                        .or().like(Material::getMaterialCode, keyword))
                .orderByDesc(Material::getId);
        Page<Material> p = materialMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @RequirePerm("base:material:create")
    @PostMapping("/materials")
    public R<Material> createMaterial(@RequestBody Material m) {
        if (m.getQrGranularity() == null) {
            m.setQrGranularity(m.getSerialManaged() != null && m.getSerialManaged() == 1 ? "SERIAL"
                    : (m.getBatchManaged() != null && m.getBatchManaged() == 1 ? "BATCH" : "SKU"));
        }
        if (m.getIsDeleted() == null) {
            m.setIsDeleted(0);
        }
        materialMapper.insert(m);
        return R.ok(m);
    }

    @GetMapping("/materials/{id}")
    public R<Material> getMaterial(@PathVariable Long id) {
        Material m = materialMapper.selectById(id);
        if (m == null) {
            throw new BizException("物资不存在");
        }
        return R.ok(m);
    }

    /** 编辑物资档案（含安全库存 / 最大库存） */
    @RequirePerm("base:material:update")
    @PutMapping("/materials/{id}")
    public R<Material> updateMaterial(@PathVariable Long id, @RequestBody Material req) {
        Material m = materialMapper.selectById(id);
        if (m == null) {
            throw new BizException("物资不存在");
        }
        if (req.getMaterialName() != null) m.setMaterialName(req.getMaterialName());
        if (req.getSpec() != null) m.setSpec(req.getSpec());
        if (req.getBrand() != null) m.setBrand(req.getBrand());
        if (req.getUnit() != null) m.setUnit(req.getUnit());
        // 允许清空分类（前端提交完整表单）
        m.setCategoryId(req.getCategoryId());
        if (req.getBarcode() != null) m.setBarcode(req.getBarcode());
        if (req.getBatchManaged() != null) m.setBatchManaged(req.getBatchManaged());
        if (req.getSerialManaged() != null) m.setSerialManaged(req.getSerialManaged());
        if (req.getQrGranularity() != null) m.setQrGranularity(req.getQrGranularity());
        if (req.getShelfLifeDays() != null) m.setShelfLifeDays(req.getShelfLifeDays());
        if (req.getSafetyStock() != null) {
            if (req.getSafetyStock().signum() < 0) throw new BizException("安全库存不能为负数");
            m.setSafetyStock(req.getSafetyStock());
        }
        if (req.getMaxStock() != null) {
            if (req.getMaxStock().signum() < 0) throw new BizException("最大库存不能为负数");
            m.setMaxStock(req.getMaxStock());
        }
        if (req.getRefPrice() != null) m.setRefPrice(req.getRefPrice());
        if (req.getRefTaxRate() != null) m.setRefTaxRate(req.getRefTaxRate());
        if (req.getStatus() != null) m.setStatus(req.getStatus());
        if (req.getRemark() != null) m.setRemark(req.getRemark());
        if (m.getSafetyStock() != null && m.getMaxStock() != null
                && m.getMaxStock().signum() > 0
                && m.getSafetyStock().compareTo(m.getMaxStock()) > 0) {
            throw new BizException("安全库存不能大于最大库存");
        }
        m.setUpdatedAt(java.time.LocalDateTime.now());
        materialMapper.updateById(m);
        return R.ok(m);
    }

    @GetMapping("/suppliers")
    public R<PageResult<Supplier>> pageSupplier(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Supplier> qw = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getIsDeleted, 0)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Supplier::getSupplierName, keyword)
                        .or().like(Supplier::getSupplierCode, keyword))
                .orderByDesc(Supplier::getId);
        Page<Supplier> p = supplierMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @RequirePerm("base:supplier:create")
    @PostMapping("/suppliers")
    public R<Supplier> createSupplier(@RequestBody Supplier s) {
        if (s.getSupplierCode() == null || s.getSupplierCode().isBlank()) {
            return R.fail("供应商编码不能为空");
        }
        if (s.getSupplierName() == null || s.getSupplierName().isBlank()) {
            return R.fail("供应商名称不能为空");
        }
        s.setSupplierCode(s.getSupplierCode().trim());
        s.setSupplierName(s.getSupplierName().trim());
        Long exists = supplierMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, s.getSupplierCode())
                .eq(Supplier::getIsDeleted, 0)
                .ne(s.getId() != null, Supplier::getId, s.getId()));
        if (exists != null && exists > 0) {
            return R.fail("供应商编码已存在：" + s.getSupplierCode() + "，请更换编码");
        }
        if (s.getIsDeleted() == null) {
            s.setIsDeleted(0);
        }
        if (s.getId() == null) {
            supplierMapper.insert(s);
        } else {
            supplierMapper.updateById(s);
        }
        return R.ok(s);
    }

    @GetMapping("/supplier-products")
    public R<PageResult<SupplierProduct>> pageSp(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Long supplierId,
                                                 @RequestParam(required = false) Long materialId,
                                                 @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SupplierProduct> qw = new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getIsDeleted, 0)
                .eq(supplierId != null, SupplierProduct::getSupplierId, supplierId)
                .eq(materialId != null, SupplierProduct::getMaterialId, materialId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SupplierProduct::getProductName, keyword)
                        .or().like(SupplierProduct::getSupplierProductCode, keyword)
                        .or().like(SupplierProduct::getSupplierSku, keyword))
                .orderByDesc(SupplierProduct::getId);
        Page<SupplierProduct> p = supplierProductMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class SaveSpReq {
        private Long id;
        private Long supplierId;
        private Long materialId;
        private String supplierProductCode;
        private String supplierSku;
        private String productName;
        private String spec;
        private String brand;
        private String unit;
        private String taxCode;
        private BigDecimal taxRate;
        private BigDecimal taxInclusivePrice;
        private String priceTaxMode;
        private BigDecimal moq;
        private BigDecimal minQty;
        private Integer leadTimeDays;
        private String packSpec;
        private Integer shelfLifeDays;
    }

    @RequirePerm("base:supplier-product:create")
    @PostMapping("/supplier-products")
    public R<SupplierProduct> saveSp(@RequestBody SaveSpReq req) {
        SupplierProduct sp = req.getId() == null ? new SupplierProduct() : supplierProductMapper.selectById(req.getId());
        if (sp == null) {
            throw new BizException("供应商产品不存在");
        }
        sp.setSupplierId(req.getSupplierId());
        sp.setMaterialId(req.getMaterialId());
        sp.setSupplierProductCode(req.getSupplierProductCode());
        sp.setSupplierSku(req.getSupplierSku());
        sp.setProductName(req.getProductName());
        sp.setSpec(req.getSpec());
        sp.setBrand(req.getBrand());
        sp.setUnit(req.getUnit());
        sp.setTaxCode(req.getTaxCode());
        sp.setMoq(req.getMoq());
        sp.setMinQty(req.getMinQty());
        sp.setLeadTimeDays(req.getLeadTimeDays());
        sp.setPackSpec(req.getPackSpec());
        sp.setShelfLifeDays(req.getShelfLifeDays());
        sp.setPriceTaxMode(req.getPriceTaxMode() == null ? "INCLUSIVE" : req.getPriceTaxMode());
        sp.setCurrency("CNY");
        sp.setStatus(1);
        if (sp.getIsDeleted() == null) {
            sp.setIsDeleted(0);
        }

        BigDecimal rate = req.getTaxRate() == null ? new BigDecimal("13.00") : req.getTaxRate();
        sp.setTaxRate(rate);
        if ("EXCLUSIVE".equalsIgnoreCase(sp.getPriceTaxMode()) && req.getTaxInclusivePrice() == null) {
            throw new BizException("请提供含税价");
        }
        PriceTaxCalculator.Line line = calculator.fromInclusive(req.getTaxInclusivePrice(), rate, BigDecimal.ONE);
        sp.setTaxInclusivePrice(line.taxInclusivePrice);
        sp.setTaxExclusivePrice(line.taxExclusivePrice);
        sp.setTaxUnitAmount(line.taxUnitAmount);

        if (sp.getId() == null) {
            supplierProductMapper.insert(sp);
        } else {
            supplierProductMapper.updateById(sp);
        }
        return R.ok(sp);
    }

    @RequirePerm("base:supplier-product:delete")
    @DeleteMapping("/supplier-products/{id}")
    public R<Void> deleteSp(@PathVariable Long id) {
        SupplierProduct sp = supplierProductMapper.selectById(id);
        if (sp == null) {
            throw new BizException("供应商产品不存在");
        }
        // 逻辑删除字段需显式 SET，updateById 不会写入 isDeleted
        supplierProductMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SupplierProduct>()
                .eq(SupplierProduct::getId, id)
                .set(SupplierProduct::getIsDeleted, 1)
                .set(SupplierProduct::getUpdatedAt, java.time.LocalDateTime.now()));
        return R.ok(null);
    }

    /** 软删除供应商，并一并下架其商品 */
    @RequirePerm("base:supplier:delete")
    @DeleteMapping("/suppliers/{id}")
    public R<Void> deleteSupplier(@PathVariable Long id) {
        Supplier s = supplierMapper.selectById(id);
        if (s == null) {
            throw new BizException("供应商不存在");
        }
        supplierMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Supplier>()
                .eq(Supplier::getId, id)
                .set(Supplier::getIsDeleted, 1)
                .set(Supplier::getUpdatedAt, java.time.LocalDateTime.now()));

        supplierProductMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, id)
                .eq(SupplierProduct::getIsDeleted, 0)
                .set(SupplierProduct::getIsDeleted, 1)
                .set(SupplierProduct::getUpdatedAt, java.time.LocalDateTime.now()));
        return R.ok(null);
    }
}
