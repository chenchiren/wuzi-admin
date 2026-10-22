package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierContact;
import com.relex.wuzi.mapper.SupplierContactMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商联系人：
 * GET    /api/suppliers/{supplierId}/contacts
 * POST   /api/supplier-contacts
 * PUT    /api/supplier-contacts/{id}
 * DELETE /api/supplier-contacts/{id}
 */
@RestController
@RequiredArgsConstructor
public class SupplierContactController {

    private final SupplierContactMapper contactMapper;
    private final SupplierMapper supplierMapper;
    private final OperationLogService operationLogService;

    @GetMapping("/api/suppliers/{supplierId}/contacts")
    public R<List<SupplierContact>> listBySupplier(@PathVariable Long supplierId) {
        if (supplierId == null || supplierId <= 0) {
            throw new BizException("supplierId 无效");
        }
        return R.ok(contactMapper.selectList(new LambdaQueryWrapper<SupplierContact>()
                .eq(SupplierContact::getSupplierId, supplierId)
                .orderByDesc(SupplierContact::getIsPrimary)
                .orderByAsc(SupplierContact::getId)));
    }

    @Data
    public static class SaveReq {
        private Long supplierId;
        private String contactName;
        private String phone;
        private String email;
        private String position;
        private Integer isPrimary;
        private String remark;
    }

    @RequirePerm("base:supplier-contact:create")
    @PostMapping("/api/supplier-contacts")
    @Transactional(rollbackFor = Exception.class)
    public R<SupplierContact> create(@RequestBody SaveReq req,
                                     @RequestAttribute(value = "userId", required = false) Long opId,
                                     @RequestAttribute(value = "username", required = false) String opName,
                                     HttpServletRequest request) {
        if (req.getSupplierId() == null || req.getSupplierId() <= 0) {
            throw new BizException("请选择供应商");
        }
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) {
            throw new BizException("供应商不存在");
        }
        if (req.getContactName() == null || req.getContactName().isBlank()) {
            throw new BizException("联系人姓名不能为空");
        }
        SupplierContact c = new SupplierContact();
        c.setSupplierId(req.getSupplierId());
        c.setContactName(req.getContactName().trim());
        c.setPhone(req.getPhone());
        c.setEmail(req.getEmail());
        c.setPosition(req.getPosition());
        c.setIsPrimary(req.getIsPrimary() != null && req.getIsPrimary() == 1 ? 1 : 0);
        c.setRemark(req.getRemark());
        c.setIsDeleted(0);
        c.setCreatedAt(LocalDateTime.now());
        contactMapper.insert(c);
        if (c.getIsPrimary() == 1) {
            clearOtherPrimary(c.getSupplierId(), c.getId());
        }
        operationLogService.record(request, opId, opName, "SUPPLIER_CONTACT", "CREATE",
                "supplierId=" + c.getSupplierId() + ",name=" + c.getContactName(), "SUCCESS");
        return R.ok(c);
    }

    @RequirePerm("base:supplier-contact:update")
    @PutMapping("/api/supplier-contacts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<SupplierContact> update(@PathVariable Long id, @RequestBody SaveReq req,
                                     @RequestAttribute(value = "userId", required = false) Long opId,
                                     @RequestAttribute(value = "username", required = false) String opName,
                                     HttpServletRequest request) {
        SupplierContact c = contactMapper.selectById(id);
        if (c == null) {
            return R.fail("联系人不存在");
        }
        if (req.getContactName() != null && !req.getContactName().isBlank()) {
            c.setContactName(req.getContactName().trim());
        }
        if (req.getPhone() != null) {
            c.setPhone(req.getPhone());
        }
        if (req.getEmail() != null) {
            c.setEmail(req.getEmail());
        }
        if (req.getPosition() != null) {
            c.setPosition(req.getPosition());
        }
        if (req.getRemark() != null) {
            c.setRemark(req.getRemark());
        }
        if (req.getIsPrimary() != null) {
            c.setIsPrimary(req.getIsPrimary() == 1 ? 1 : 0);
        }
        c.setUpdatedAt(LocalDateTime.now());
        contactMapper.updateById(c);
        if (c.getIsPrimary() == 1) {
            clearOtherPrimary(c.getSupplierId(), c.getId());
        }
        operationLogService.record(request, opId, opName, "SUPPLIER_CONTACT", "UPDATE",
                "contactId=" + id, "SUCCESS");
        return R.ok(c);
    }

    @RequirePerm("base:supplier-contact:delete")
    @DeleteMapping("/api/supplier-contacts/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute(value = "userId", required = false) Long opId,
                          @RequestAttribute(value = "username", required = false) String opName,
                          HttpServletRequest request) {
        SupplierContact c = contactMapper.selectById(id);
        if (c == null) {
            return R.fail("联系人不存在");
        }
        contactMapper.deleteById(id);
        operationLogService.record(request, opId, opName, "SUPPLIER_CONTACT", "DELETE",
                "contactId=" + id, "SUCCESS");
        return R.ok();
    }

    /** 同一供应商仅保留一个主联系人 */
    private void clearOtherPrimary(Long supplierId, Long keepId) {
        if (supplierId == null) {
            return;
        }
        List<SupplierContact> primaries = contactMapper.selectList(new LambdaQueryWrapper<SupplierContact>()
                .eq(SupplierContact::getSupplierId, supplierId)
                .eq(SupplierContact::getIsPrimary, 1));
        for (SupplierContact other : primaries) {
            if (keepId != null && keepId.equals(other.getId())) {
                continue;
            }
            other.setIsPrimary(0);
            other.setUpdatedAt(LocalDateTime.now());
            contactMapper.updateById(other);
        }
    }
}
