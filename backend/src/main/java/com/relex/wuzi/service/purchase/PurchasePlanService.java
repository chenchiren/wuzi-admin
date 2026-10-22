package com.relex.wuzi.service.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.PurchaseApply;
import com.relex.wuzi.domain.PurchaseApplyItem;
import com.relex.wuzi.domain.PurchasePlan;
import com.relex.wuzi.domain.PurchasePlanItem;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.PurchaseApplyItemMapper;
import com.relex.wuzi.mapper.PurchaseApplyMapper;
import com.relex.wuzi.mapper.PurchasePlanItemMapper;
import com.relex.wuzi.mapper.PurchasePlanMapper;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购计划 / 需求汇总：部门·项目维度计划单，支持行转采购申请。
 */
@Service
@RequiredArgsConstructor
public class PurchasePlanService {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_SUBMITTED = "SUBMITTED";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_CLOSED = "CLOSED";
    public static final String ST_CANCELLED = "CANCELLED";

    private final PurchasePlanMapper planMapper;
    private final PurchasePlanItemMapper itemMapper;
    private final PurchaseApplyMapper applyMapper;
    private final PurchaseApplyItemMapper applyItemMapper;
    private final MaterialMapper materialMapper;
    private final PriceTaxCalculator priceTaxCalculator;

    @Data
    public static class PlanLine {
        private Long materialId;
        private String materialName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal estimatedPrice;
        private String purpose;
        private Long demandDeptId;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private String title;
        private Integer planYear;
        private Integer planMonth;
        private Long deptId;
        private String projectCode;
        private String planType;
        private String remark;
        private List<PlanLine> items;
    }

    /** 计划行转采购申请：itemIds 为空表示转全部 PENDING 行 */
    @Data
    public static class ToApplyReq {
        private List<Long> itemIds;
        private LocalDate needDate;
        private String urgency;
        private String purpose;
        private String remark;
    }

    public PurchasePlan get(Long id) {
        return require(id, null);
    }

    public PageResult<PurchasePlan> page(long page, long size, String status, Integer planYear, Long deptId) {
        Page<PurchasePlan> p = planMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PurchasePlan>()
                        .eq(status != null && !status.isBlank(), PurchasePlan::getStatus, status)
                        .eq(planYear != null, PurchasePlan::getPlanYear, planYear)
                        .eq(deptId != null, PurchasePlan::getDeptId, deptId)
                        .orderByDesc(PurchasePlan::getId));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public List<PurchasePlanItem> items(Long planId) {
        List<PurchasePlanItem> list = itemMapper.selectList(new LambdaQueryWrapper<PurchasePlanItem>()
                .eq(PurchasePlanItem::getPlanId, planId)
                .orderByAsc(PurchasePlanItem::getId));
        fillApplyNo(list);
        return list;
    }

    private void fillApplyNo(List<PurchasePlanItem> list) {
        Set<Long> applyIds = list.stream()
                .map(PurchasePlanItem::getApplyId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (applyIds.isEmpty()) {
            return;
        }
        Map<Long, String> applyNoMap = applyMapper.selectBatchIds(applyIds).stream()
                .collect(Collectors.toMap(PurchaseApply::getId, PurchaseApply::getApplyNo, (a, b) -> a));
        for (PurchasePlanItem item : list) {
            if (item.getApplyId() != null) {
                item.setApplyNo(applyNoMap.get(item.getApplyId()));
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchasePlan create(CreateReq req, Long userId) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("计划标题不能为空");
        }
        if (req.getPlanYear() == null) {
            throw new BizException("计划年度不能为空");
        }
        PurchasePlan plan = new PurchasePlan();
        plan.setPlanNo(BillNoUtil.next("JH-"));
        plan.setTitle(req.getTitle().trim());
        plan.setPlanYear(req.getPlanYear());
        plan.setPlanMonth(req.getPlanMonth());
        plan.setDeptId(req.getDeptId());
        plan.setProjectCode(req.getProjectCode());
        plan.setPlanType(req.getPlanType() == null || req.getPlanType().isBlank() ? "MONTHLY" : req.getPlanType());
        plan.setStatus(ST_DRAFT);
        plan.setRemark(req.getRemark());
        plan.setIsDeleted(0);
        plan.setCreatedBy(userId);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setTotalAmount(BigDecimal.ZERO);
        planMapper.insert(plan);
        replaceItems(plan, req.getItems(), userId);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CreateReq req, Long userId) {
        PurchasePlan plan = require(id, userId);
        if (!ST_DRAFT.equals(plan.getStatus()) && !ST_REJECTED.equals(plan.getStatus())) {
            throw new BizException("仅草稿/已驳回可编辑");
        }
        plan.setTitle(req.getTitle() == null ? plan.getTitle() : req.getTitle().trim());
        if (req.getPlanYear() != null) plan.setPlanYear(req.getPlanYear());
        plan.setPlanMonth(req.getPlanMonth());
        plan.setDeptId(req.getDeptId());
        plan.setProjectCode(req.getProjectCode());
        if (req.getPlanType() != null && !req.getPlanType().isBlank()) plan.setPlanType(req.getPlanType());
        plan.setRemark(req.getRemark());
        if (ST_REJECTED.equals(plan.getStatus())) plan.setStatus(ST_DRAFT);
        plan.setUpdatedBy(userId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        if (req.getItems() != null) {
            replaceItems(plan, req.getItems(), userId);
        }
    }

    private void replaceItems(PurchasePlan plan, List<PlanLine> lines, Long userId) {
        itemMapper.delete(new LambdaQueryWrapper<PurchasePlanItem>().eq(PurchasePlanItem::getPlanId, plan.getId()));
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            int idx = 0;
            for (PlanLine line : lines) {
                idx++;
                // 先建档再采购：明细必须挂接已登记物资
                if (line.getMaterialId() == null) {
                    throw new BizException("第" + idx + "行未选择已登记物资，请先在物资档案建档后再采购");
                }
                PurchasePlanItem item = new PurchasePlanItem();
                item.setPlanId(plan.getId());
                item.setMaterialId(line.getMaterialId());
                item.setMaterialName(line.getMaterialName());
                item.setSpec(line.getSpec());
                item.setUnit(line.getUnit());
                BigDecimal qty = nvl(line.getQty());
                BigDecimal price = nvl(line.getEstimatedPrice());
                BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
                item.setQty(qty);
                item.setEstimatedPrice(price);
                item.setEstimatedAmount(amount);
                item.setPurpose(line.getPurpose());
                item.setDemandDeptId(line.getDemandDeptId());
                item.setStatus("PENDING");
                item.setRemark(line.getRemark());
                itemMapper.insert(item);
                total = total.add(amount);
            }
        }
        plan.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        planMapper.updateById(plan);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchasePlan submit(Long id, Long userId) {
        PurchasePlan plan = require(id, userId);
        if (!ST_DRAFT.equals(plan.getStatus()) && !ST_REJECTED.equals(plan.getStatus())) {
            throw new BizException("当前状态不可提交");
        }
        List<PurchasePlanItem> items = items(id);
        if (items.isEmpty()) {
            throw new BizException("计划明细不能为空");
        }
        plan.setStatus(ST_SUBMITTED);
        plan.setUpdatedBy(userId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchasePlan approve(Long id, Long userId) {
        PurchasePlan plan = require(id, userId);
        if (!ST_SUBMITTED.equals(plan.getStatus())) {
            throw new BizException("当前状态不可审核通过");
        }
        plan.setStatus(ST_APPROVED);
        plan.setUpdatedBy(userId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchasePlan reject(Long id, String reason, Long userId) {
        PurchasePlan plan = require(id, userId);
        if (!ST_SUBMITTED.equals(plan.getStatus())) {
            throw new BizException("当前状态不可驳回");
        }
        plan.setStatus(ST_REJECTED);
        plan.setRemark(reason == null ? plan.getRemark() : reason);
        plan.setUpdatedBy(userId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchasePlan cancel(Long id, Long userId) {
        PurchasePlan plan = require(id, userId);
        if (ST_CLOSED.equals(plan.getStatus()) || ST_CANCELLED.equals(plan.getStatus())) {
            throw new BizException("已终态不可作废");
        }
        plan.setStatus(ST_CANCELLED);
        plan.setUpdatedBy(userId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemConverted(Long itemId, Long applyId, Long userId) {
        PurchasePlanItem item = itemMapper.selectById(itemId);
        if (item == null) throw new BizException("计划行不存在");
        item.setStatus("CONVERTED");
        item.setApplyId(applyId);
        itemMapper.updateById(item);
        // 全部转单后关闭计划
        closePlanIfAllConverted(item.getPlanId());
    }

    /**
     * 计划行批量转采购申请：创建一张采购申请（草稿），回写 apply_id，全部转完后关闭计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseApply toApply(Long planId, ToApplyReq req, Long userId) {
        PurchasePlan plan = require(planId, null);
        if (!ST_APPROVED.equals(plan.getStatus())) {
            throw new BizException("仅已通过（APPROVED）的采购计划可转采购申请");
        }

        List<PurchasePlanItem> all = itemMapper.selectList(new LambdaQueryWrapper<PurchasePlanItem>()
                .eq(PurchasePlanItem::getPlanId, planId)
                .orderByAsc(PurchasePlanItem::getId));
        List<PurchasePlanItem> pending = all.stream()
                .filter(it -> !"CONVERTED".equals(it.getStatus()))
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            throw new BizException("没有可转的计划行（均已是已转申请状态）");
        }

        List<PurchasePlanItem> selected;
        if (req.getItemIds() == null || req.getItemIds().isEmpty()) {
            selected = pending;
        } else {
            Set<Long> idSet = new HashSet<>(req.getItemIds());
            selected = pending.stream()
                    .filter(it -> idSet.contains(it.getId()))
                    .collect(Collectors.toList());
            if (selected.isEmpty()) {
                throw new BizException("所选计划行不存在或已转申请");
            }
            if (selected.size() != idSet.size()) {
                throw new BizException("存在无效或已转申请的计划行，请刷新后重试");
            }
        }

        // 价格兜底：计划预估价 → 物资参考价
        Map<Long, Material> materials = new HashMap<>();
        for (PurchasePlanItem planItem : selected) {
            Long mid = planItem.getMaterialId();
            if (mid != null && !materials.containsKey(mid)) {
                materials.put(mid, materialMapper.selectById(mid));
            }
        }

        PurchaseApply apply = new PurchaseApply();
        apply.setApplyNo(BillNoUtil.next("PA"));
        apply.setPlanId(plan.getId());
        apply.setPlanNo(plan.getPlanNo());
        apply.setApplyUserId(userId);
        apply.setDeptId(plan.getDeptId());
        apply.setApplyTime(LocalDateTime.now());
        apply.setNeedDate(req.getNeedDate());
        apply.setUrgency(req.getUrgency() == null || req.getUrgency().isBlank() ? "NORMAL" : req.getUrgency());
        apply.setPurpose(req.getPurpose() != null && !req.getPurpose().isBlank()
                ? req.getPurpose()
                : ("采购计划-" + plan.getPlanNo()));
        apply.setStatus("DRAFT");
        String remark = req.getRemark() != null && !req.getRemark().isBlank()
                ? req.getRemark()
                : ("来源采购计划 " + plan.getPlanNo() + " / " + plan.getTitle());
        apply.setRemark(remark);
        apply.setTotalTaxInclAmount(BigDecimal.ZERO);
        apply.setIsDeleted(0);
        apply.setCreatedAt(LocalDateTime.now());
        applyMapper.insert(apply);

        BigDecimal sum = BigDecimal.ZERO;
        int idx = 0;
        for (PurchasePlanItem planItem : selected) {
            idx++;
            if (planItem.getMaterialId() == null) {
                throw new BizException("第" + idx + "行未关联系统物资，无法转申请");
            }
            Material material = materials.get(planItem.getMaterialId());
            if (material == null) {
                throw new BizException("第" + idx + "行物资不存在: " + planItem.getMaterialId());
            }

            BigDecimal price = planItem.getEstimatedPrice();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                price = material.getRefPrice();
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("「" + planItem.getMaterialName() + "」缺少预估单价/物资参考价，请先补充后再转申请");
            }
            BigDecimal rate = material.getRefTaxRate() != null ? material.getRefTaxRate() : BigDecimal.ZERO;
            BigDecimal qty = planItem.getQty() == null ? BigDecimal.ZERO : planItem.getQty();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("「" + planItem.getMaterialName() + "」数量必须大于0");
            }

            PriceTaxCalculator.Line line = priceTaxCalculator.fromInclusive(price, rate, qty);
            PurchaseApplyItem applyItem = new PurchaseApplyItem();
            applyItem.setApplyId(apply.getId());
            applyItem.setMaterialId(planItem.getMaterialId());
            applyItem.setQty(qty);
            applyItem.setUnit(planItem.getUnit() != null ? planItem.getUnit() : material.getUnit());
            applyItem.setRefPrice(line.taxInclusivePrice);
            applyItem.setTaxRate(rate);
            applyItem.setTaxInclAmount(line.taxInclusiveAmount);
            applyItem.setRemark(planItem.getRemark());
            applyItemMapper.insert(applyItem);
            sum = sum.add(line.taxInclusiveAmount);

            planItem.setStatus("CONVERTED");
            planItem.setApplyId(apply.getId());
            itemMapper.updateById(planItem);
        }

        apply.setTotalTaxInclAmount(sum.setScale(2, RoundingMode.HALF_UP));
        applyMapper.updateById(apply);
        closePlanIfAllConverted(planId);
        return apply;
    }

    private void closePlanIfAllConverted(Long planId) {
        Long pending = itemMapper.selectCount(new LambdaQueryWrapper<PurchasePlanItem>()
                .eq(PurchasePlanItem::getPlanId, planId)
                .ne(PurchasePlanItem::getStatus, "CONVERTED"));
        if (pending == null || pending == 0) {
            planMapper.update(null, new LambdaUpdateWrapper<PurchasePlan>()
                    .eq(PurchasePlan::getId, planId)
                    .set(PurchasePlan::getStatus, ST_CLOSED)
                    .set(PurchasePlan::getUpdatedAt, LocalDateTime.now()));
        }
    }

    private PurchasePlan require(Long id, Long userId) {
        PurchasePlan plan = planMapper.selectById(id);
        if (plan == null || (plan.getIsDeleted() != null && plan.getIsDeleted() == 1)) {
            throw new BizException("采购计划不存在");
        }
        return plan;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
