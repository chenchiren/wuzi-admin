package com.relex.wuzi.web;

import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.aspect.DataScopeSupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.PurchaseApply;
import com.relex.wuzi.domain.PurchaseApplyItem;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.PurchaseApplyItemMapper;
import com.relex.wuzi.mapper.PurchaseApplyMapper;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采购申请：增删查、提交审批、明细行（含税参考价走 PriceTaxCalculator）
 */
@RestController
@RequestMapping("/api/purchase/applies")
@RequiredArgsConstructor
public class PurchaseApplyController {

    private final PurchaseApplyMapper applyMapper;
    private final PurchaseApplyItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final PriceTaxCalculator calculator;
    private final ApprovalService approvalService;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class ItemReq {
        private Long materialId;
        private BigDecimal qty;
        private String unit;
        /** 含税参考单价，缺省取物资 ref_price */
        private BigDecimal refPrice;
        /** 税种编码（字典）；手填时可空 */
        private String taxCode;
        private BigDecimal taxRate;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private LocalDate needDate;
        private String urgency;
        private String purpose;
        private String remark;
        private List<ItemReq> items;
    }

    @Data
    public static class SubmitReq {
        private String remark;
    }

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "apply_user_id")
    public R<PageResult<PurchaseApply>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PurchaseApply> qw = new LambdaQueryWrapper<PurchaseApply>()
                .eq(status != null && !status.isBlank(), PurchaseApply::getStatus, status)
                .orderByDesc(PurchaseApply::getId);
        DataScopeSupport.apply(qw);
        Page<PurchaseApply> p = applyMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseApply apply = require(id);
        List<PurchaseApplyItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                .eq(PurchaseApplyItem::getApplyId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("apply", apply);
        map.put("items", items);
        return R.ok(map);
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseApplyItem>> items(@PathVariable Long id) {
        require(id);
        return R.ok(itemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                .eq(PurchaseApplyItem::getApplyId, id)));
    }

    @PostMapping
    @RequirePerm("po:apply:create")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("申请明细不能为空");
        }
        PurchaseApply apply = new PurchaseApply();
        apply.setApplyNo(BillNoUtil.next("PA"));
        apply.setApplyUserId(userId);
        apply.setApplyTime(LocalDateTime.now());
        apply.setNeedDate(req.getNeedDate());
        apply.setUrgency(req.getUrgency() != null ? req.getUrgency() : "NORMAL");
        apply.setPurpose(req.getPurpose());
        apply.setStatus(ApprovalService.ST_DRAFT);
        apply.setRemark(req.getRemark());
        apply.setTotalTaxInclAmount(BigDecimal.ZERO);
        applyMapper.insert(apply);

        List<PurchaseApplyItem> items = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemReq it : req.getItems()) {
            PurchaseApplyItem item = buildItem(apply.getId(), it);
            itemMapper.insert(item);
            items.add(item);
            sum = sum.add(item.getTaxInclAmount());
        }
        apply.setTotalTaxInclAmount(sum);
        applyMapper.updateById(apply);

        Map<String, Object> result = new HashMap<>();
        result.put("apply", apply);
        result.put("items", items);
        return R.ok(result);
    }

    /**
     * 整单修改（仅 DRAFT / REJECTED）：覆盖主表与明细，驳回后可改再重新发起。
     */
    @RequirePerm("po:apply:update")
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody CreateReq req) {
        PurchaseApply apply = requireEditable(id);
        apply.setNeedDate(req.getNeedDate());
        if (req.getUrgency() != null) {
            apply.setUrgency(req.getUrgency());
        }
        apply.setPurpose(req.getPurpose());
        apply.setRemark(req.getRemark());
        applyMapper.updateById(apply);

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            itemMapper.delete(new LambdaQueryWrapper<PurchaseApplyItem>()
                    .eq(PurchaseApplyItem::getApplyId, id));
            List<PurchaseApplyItem> items = new ArrayList<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (ItemReq it : req.getItems()) {
                PurchaseApplyItem item = buildItem(id, it);
                itemMapper.insert(item);
                items.add(item);
                sum = sum.add(item.getTaxInclAmount());
            }
            apply.setTotalTaxInclAmount(sum);
            applyMapper.updateById(apply);
            Map<String, Object> result = new HashMap<>();
            result.put("apply", apply);
            result.put("items", items);
            return R.ok(result);
        }
        List<PurchaseApplyItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                .eq(PurchaseApplyItem::getApplyId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("apply", apply);
        result.put("items", items);
        return R.ok(result);
    }

    /** 新增明细行（含税参考价） */
    @RequirePerm("po:apply:update")
    @PostMapping("/{id}/items")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseApplyItem> addItem(@PathVariable Long id, @RequestBody ItemReq req) {
        PurchaseApply apply = requireEditable(id);
        PurchaseApplyItem item = buildItem(id, req);
        itemMapper.insert(item);
        apply.setTotalTaxInclAmount(nz(apply.getTotalTaxInclAmount()).add(item.getTaxInclAmount()));
        applyMapper.updateById(apply);
        return R.ok(item);
    }

    /** 删除明细行 */
    @RequirePerm("po:apply:delete")
    @DeleteMapping("/{id}/items/{itemId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        PurchaseApply apply = requireEditable(id);
        PurchaseApplyItem item = itemMapper.selectById(itemId);
        if (item == null || !id.equals(item.getApplyId())) {
            throw new BizException("明细行不存在");
        }
        itemMapper.deleteById(itemId);
        apply.setTotalTaxInclAmount(nz(apply.getTotalTaxInclAmount()).subtract(nz(item.getTaxInclAmount())));
        applyMapper.updateById(apply);
        return R.ok();
    }

    /** 更新明细行数量/参考价，重算含税金额 */
    @RequirePerm("po:apply:update")
    @PutMapping("/{id}/items/{itemId}")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseApplyItem> updateItem(@PathVariable Long id, @PathVariable Long itemId, @RequestBody ItemReq req) {
        PurchaseApply apply = requireEditable(id);
        PurchaseApplyItem old = itemMapper.selectById(itemId);
        if (old == null || !id.equals(old.getApplyId())) {
            throw new BizException("明细行不存在");
        }
        PurchaseApplyItem item = buildItem(id, req);
        item.setId(itemId);
        itemMapper.updateById(item);
        BigDecimal others = itemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                        .eq(PurchaseApplyItem::getApplyId, id)
                        .ne(PurchaseApplyItem::getId, itemId))
                .stream().map(PurchaseApplyItem::getTaxInclAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        apply.setTotalTaxInclAmount(others.add(item.getTaxInclAmount()));
        applyMapper.updateById(apply);
        return R.ok(item);
    }

    /** 提交审批 */
    @RequirePerm("po:apply:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseApply> submit(@PathVariable Long id, @RequestBody(required = false) SubmitReq req,
                                   @RequestAttribute("userId") Long userId) {
        PurchaseApply apply = requireEditable(id);
        List<PurchaseApplyItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                .eq(PurchaseApplyItem::getApplyId, id));
        if (items.isEmpty()) {
            throw new BizException("申请明细不能为空，请先修改补全");
        }
        if (req != null && req.getRemark() != null) {
            apply.setRemark(req.getRemark());
        }
        applyMapper.updateById(apply);
        flowApprovalService.start(ApprovalService.BIZ_APPLY, ApprovalService.BIZ_APPLY,
                id, apply.getApplyNo(), userId, "采购申请-" + apply.getApplyNo(),
                status -> {
                    apply.setStatus(status);
                    applyMapper.updateById(apply);
                });
        return R.ok(apply);
    }

    /** 作废（仅 DRAFT / REJECTED） */
    @RequirePerm("po:apply:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseApply> cancel(@PathVariable Long id) {
        PurchaseApply apply = require(id);
        if (!ApprovalService.ST_DRAFT.equals(apply.getStatus())
                && !ApprovalService.ST_REJECTED.equals(apply.getStatus())) {
            throw new BizException("当前状态不可作废");
        }
        apply.setStatus(ApprovalService.ST_CANCELLED);
        applyMapper.updateById(apply);
        return R.ok(apply);
    }

    @RequirePerm("po:apply:delete")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        PurchaseApply apply = require(id);
        if (!ApprovalService.ST_DRAFT.equals(apply.getStatus())
                && !ApprovalService.ST_REJECTED.equals(apply.getStatus())
                && !ApprovalService.ST_CANCELLED.equals(apply.getStatus())) {
            throw new BizException("仅草稿/驳回/作废单可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<PurchaseApplyItem>().eq(PurchaseApplyItem::getApplyId, id));
        applyMapper.deleteById(id);
        return R.ok();
    }

    private PurchaseApplyItem buildItem(Long applyId, ItemReq req) {
        if (req.getMaterialId() == null) {
            throw new BizException("物资不能为空");
        }
        if (req.getQty() == null || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("数量必须大于0");
        }
        Material material = materialMapper.selectById(req.getMaterialId());
        if (material == null) {
            throw new BizException("物资不存在: " + req.getMaterialId());
        }
        BigDecimal price = req.getRefPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            price = material.getRefPrice();
        }
        BigDecimal rate = req.getTaxRate() != null ? req.getTaxRate() : material.getRefTaxRate();
        PriceTaxCalculator.Line line = calculator.fromInclusive(price, rate, req.getQty());

        PurchaseApplyItem item = new PurchaseApplyItem();
        item.setApplyId(applyId);
        item.setMaterialId(req.getMaterialId());
        item.setQty(req.getQty());
        item.setUnit(req.getUnit() != null ? req.getUnit() : material.getUnit());
        item.setRefPrice(line.taxInclusivePrice);
        item.setTaxCode(req.getTaxCode());
        item.setTaxRate(rate);
        item.setTaxInclAmount(line.taxInclusiveAmount);
        item.setRemark(req.getRemark());
        return item;
    }

    private PurchaseApply require(Long id) {
        PurchaseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("采购申请不存在");
        }
        return apply;
    }

    private PurchaseApply requireEditable(Long id) {
        PurchaseApply apply = require(id);
        if (!ApprovalService.ST_DRAFT.equals(apply.getStatus())
                && !ApprovalService.ST_REJECTED.equals(apply.getStatus())) {
            throw new BizException("仅草稿/驳回状态可编辑");
        }
        return apply;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
