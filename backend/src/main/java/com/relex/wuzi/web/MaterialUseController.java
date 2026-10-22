package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.BillNoUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
import com.relex.wuzi.service.StockService;
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
 * 领用申请 / 审批 / 发料记录
 * status: DRAFT -> PENDING_APPROVAL -> APPROVED -> ISSUED (或 REJECTED)
 */
@RestController
@RequestMapping("/api/material-use")
@RequiredArgsConstructor
public class MaterialUseController {

    private final MaterialUseApplyMapper applyMapper;
    private final MaterialUseApplyItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final FlowApprovalService flowApprovalService;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundOrderItemMapper;
    private final StockService stockService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private BigDecimal qty;
        private String unit;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private String useType;
        private String projectName;
        private LocalDate needDate;
        private String purpose;
        private String remark;
        private List<CreateItem> items;
    }

    @Data
    public static class ApproveReq {
        private Boolean approved;
        private String comment;
    }

    @Data
    public static class IssueItem {
        private Long itemId;
        private Long materialId;
        private BigDecimal qty;
        /** 可选库位 */
        private Long locationId;
    }

    @Data
    public static class IssueReq {
        private Long warehouseId;
        /** 默认库位（明细未指定时生效） */
        private Long locationId;
        private String remark;
        private List<IssueItem> items;
    }

    @GetMapping
    @com.relex.wuzi.annotation.DataScope(deptField = "dept_id", userField = "apply_user_id")
    public R<PageResult<MaterialUseApply>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<MaterialUseApply> qw = new LambdaQueryWrapper<MaterialUseApply>()
                .eq(status != null && !status.isBlank(), MaterialUseApply::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MaterialUseApply::getApplyNo, keyword)
                        .or().like(MaterialUseApply::getProjectName, keyword)
                        .or().like(MaterialUseApply::getPurpose, keyword))
                .orderByDesc(MaterialUseApply::getId);
        com.relex.wuzi.aspect.DataScopeSupport.apply(qw);
        Page<MaterialUseApply> p = applyMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        MaterialUseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("领用申请不存在");
        }
        List<MaterialUseApplyItem> items = itemMapper.selectList(new LambdaQueryWrapper<MaterialUseApplyItem>()
                .eq(MaterialUseApplyItem::getApplyId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("apply", apply);
        map.put("items", items);
        map.put("stockRecords", stockRecordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getBizId, id)
                .in(StockRecord::getBizType, "USE_ISSUE", "USE_RETURN")
                .orderByDesc(StockRecord::getId)));
        // 出库单（发料同步生成），便于看出库影响
        map.put("outbound", outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getApplyId, id)
                .orderByDesc(OutboundOrder::getId)
                .last("LIMIT 1")));
        return R.ok(map);
    }

    @RequirePerm("asset:use:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("领用明细不能为空");
        }
        MaterialUseApply apply = new MaterialUseApply();
        apply.setApplyNo(BillNoUtil.next("UA"));
        apply.setApplyUserId(userId == null ? 0L : userId);
        apply.setUseType(req.getUseType() == null ? "DAILY" : req.getUseType());
        apply.setProjectName(req.getProjectName());
        apply.setNeedDate(req.getNeedDate());
        apply.setPurpose(req.getPurpose());
        apply.setStatus("DRAFT");
        apply.setIsDeleted(0);
        applyMapper.insert(apply);

        List<MaterialUseApplyItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("申请数量必须大于 0");
            }
            MaterialUseApplyItem item = new MaterialUseApplyItem();
            item.setApplyId(apply.getId());
            item.setMaterialId(it.getMaterialId());
            item.setQty(it.getQty());
            item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
            item.setRemark(it.getRemark());
            itemMapper.insert(item);
            items.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("apply", apply);
        result.put("items", items);
        return R.ok(result);
    }

    /**
     * 修改领用申请（仅 DRAFT / REJECTED）：覆盖主表字段与明细，驳回后可改再重新发起。
     */
    @RequirePerm("asset:use:update")
    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody CreateReq req) {
        MaterialUseApply apply = requireEditable(id);
        if (req.getUseType() != null) {
            apply.setUseType(req.getUseType());
        }
        apply.setProjectName(req.getProjectName());
        apply.setNeedDate(req.getNeedDate());
        apply.setPurpose(req.getPurpose());
        applyMapper.updateById(apply);

        if (req.getItems() != null && !req.getItems().isEmpty()) {
            itemMapper.delete(new LambdaQueryWrapper<MaterialUseApplyItem>()
                    .eq(MaterialUseApplyItem::getApplyId, id));
            List<MaterialUseApplyItem> items = new ArrayList<>();
            for (CreateItem it : req.getItems()) {
                Material material = materialMapper.selectById(it.getMaterialId());
                if (material == null) {
                    throw new BizException("物资不存在: " + it.getMaterialId());
                }
                if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("申请数量必须大于 0");
                }
                MaterialUseApplyItem item = new MaterialUseApplyItem();
                item.setApplyId(apply.getId());
                item.setMaterialId(it.getMaterialId());
                item.setQty(it.getQty());
                item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
                item.setRemark(it.getRemark());
                itemMapper.insert(item);
                items.add(item);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("apply", apply);
            result.put("items", items);
            return R.ok(result);
        }
        List<MaterialUseApplyItem> items = itemMapper.selectList(new LambdaQueryWrapper<MaterialUseApplyItem>()
                .eq(MaterialUseApplyItem::getApplyId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("apply", apply);
        result.put("items", items);
        return R.ok(result);
    }

    @RequirePerm("asset:use:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<MaterialUseApply> submit(@PathVariable Long id,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        MaterialUseApply apply = requireApply(id);
        if (!"DRAFT".equals(apply.getStatus()) && !"REJECTED".equals(apply.getStatus())) {
            throw new BizException("当前状态不可提交: " + apply.getStatus());
        }
        List<MaterialUseApplyItem> exists = itemMapper.selectList(new LambdaQueryWrapper<MaterialUseApplyItem>()
                .eq(MaterialUseApplyItem::getApplyId, id));
        if (exists.isEmpty()) {
            throw new BizException("领用明细不能为空，请先修改补全");
        }
        flowApprovalService.start(ApprovalService.BIZ_MATERIAL_USE, ApprovalService.BIZ_MATERIAL_USE,
                id, apply.getApplyNo(), userId == null ? 0L : userId,
                "领用申请-" + apply.getApplyNo(),
                status -> {
                    apply.setStatus(status);
                    applyMapper.updateById(apply);
                });
        return R.ok(apply);
    }

    @RequirePerm("asset:use:approve")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<MaterialUseApply> approve(@PathVariable Long id,
                                       @RequestBody ApproveReq req,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        MaterialUseApply apply = requireApply(id);
        boolean approved = req.getApproved() == null || req.getApproved();
        // 已绑定审批流时走多级引擎
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(
                ApprovalService.BIZ_MATERIAL_USE, id);
        if (inst != null && flowApprovalService.hasFlow(inst.getId())
                && ApprovalService.INST_PENDING.equals(inst.getStatus())) {
            if (approved) {
                flowApprovalService.approve(inst.getId(), userId, req.getComment(), null);
            } else {
                flowApprovalService.reject(inst.getId(), userId, req.getComment(), null);
            }
            return R.ok(applyMapper.selectById(id));
        }
        if (!"SUBMITTED".equals(apply.getStatus())
                && !ApprovalService.ST_PENDING.equals(apply.getStatus())) {
            throw new BizException("仅已提交的申请可审批，当前: " + apply.getStatus());
        }
        apply.setStatus(approved ? "APPROVED" : "REJECTED");
        apply.setFlowRemark(req.getComment());
        applyMapper.updateById(apply);
        return R.ok(apply);
    }

    /**
     * 发料：扣减库存并写 stock_record（biz_type=USE_ISSUE）
     */
    @RequirePerm("asset:use:issue")
    @PostMapping("/{id}/issue")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> issue(@PathVariable Long id,
                                        @RequestBody IssueReq req,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        MaterialUseApply apply = requireApply(id);
        if (!"APPROVED".equals(apply.getStatus())) {
            throw new BizException("仅已审批通过的申请可发料，当前: " + apply.getStatus());
        }
        if (req.getWarehouseId() == null) {
            throw new BizException("请指定仓库");
        }
        List<MaterialUseApplyItem> applyItems = itemMapper.selectList(new LambdaQueryWrapper<MaterialUseApplyItem>()
                .eq(MaterialUseApplyItem::getApplyId, id));
        Map<Long, MaterialUseApplyItem> itemMap = new HashMap<>();
        for (MaterialUseApplyItem it : applyItems) {
            itemMap.put(it.getId(), it);
            itemMap.putIfAbsent(it.getMaterialId(), it);
        }

        List<IssueItem> issueItems = req.getItems();
        if (issueItems == null || issueItems.isEmpty()) {
            issueItems = new ArrayList<>();
            for (MaterialUseApplyItem it : applyItems) {
                IssueItem ii = new IssueItem();
                ii.setItemId(it.getId());
                ii.setMaterialId(it.getMaterialId());
                ii.setQty(it.getQty());
                issueItems.add(ii);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (IssueItem ii : issueItems) {
            BigDecimal qty = ii.getQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("发料数量必须大于 0");
            }
            Long materialId = ii.getMaterialId();
            if (materialId == null && ii.getItemId() != null) {
                MaterialUseApplyItem src = itemMap.get(ii.getItemId());
                if (src != null) {
                    materialId = src.getMaterialId();
                }
            }
            if (materialId == null) {
                throw new BizException("明细缺少物资ID");
            }
            Map<String, Object> rec = changeStock("USE_ISSUE", apply.getApplyNo(), apply.getId(),
                    req.getWarehouseId(), ii.getLocationId() != null ? ii.getLocationId() : req.getLocationId(),
                    materialId, qty.negate(), userId, req.getRemark());
            records.add(rec);
        }
        apply.setStatus("ISSUED");
        apply.setIssueWarehouseId(req.getWarehouseId());
        apply.setFlowRemark(req.getRemark());
        applyMapper.updateById(apply);

        // 同步生成出库单（已确认），便于出库板块追溯领用发料
        OutboundOrder outbound = new OutboundOrder();
        outbound.setOutboundNo(BillNoUtil.next("CK"));
        outbound.setOutboundType("USE");
        outbound.setWarehouseId(req.getWarehouseId());
        outbound.setApplyId(apply.getId());
        outbound.setDeptId(apply.getDeptId());
        outbound.setUserId(apply.getApplyUserId());
        outbound.setOperatorId(userId);
        outbound.setOutboundTime(LocalDateTime.now());
        outbound.setStatus("CONFIRMED");
        outbound.setRemark("领用发料 " + apply.getApplyNo());
        outbound.setIsDeleted(0);
        outbound.setCreatedAt(LocalDateTime.now());
        outboundOrderMapper.insert(outbound);
        for (IssueItem ii : issueItems) {
            Long materialId = ii.getMaterialId();
            if (materialId == null && ii.getItemId() != null) {
                MaterialUseApplyItem src = itemMap.get(ii.getItemId());
                if (src != null) materialId = src.getMaterialId();
            }
            if (materialId == null) continue;
            OutboundOrderItem oi = new OutboundOrderItem();
            oi.setOutboundId(outbound.getId());
            oi.setMaterialId(materialId);
            oi.setLocationId(ii.getLocationId() != null ? ii.getLocationId() : req.getLocationId());
            oi.setQty(ii.getQty());
            oi.setActualQty(ii.getQty());
            outboundOrderItemMapper.insert(oi);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("apply", apply);
        result.put("outbound", outbound);
        result.put("records", records);
        return R.ok(result);
    }

    /**
     * 发料记录（stock_record 中 USE_ISSUE / USE_RETURN）
     */
    @GetMapping("/{id}/issue-records")
    public R<List<StockRecord>> issueRecords(@PathVariable Long id) {
        return R.ok(stockRecordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getBizId, id)
                .in(StockRecord::getBizType, "USE_ISSUE", "USE_RETURN")
                .orderByDesc(StockRecord::getId)));
    }

    private MaterialUseApply requireApply(Long id) {
        MaterialUseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("领用申请不存在");
        }
        return apply;
    }

    private MaterialUseApply requireEditable(Long id) {
        MaterialUseApply apply = requireApply(id);
        if (!"DRAFT".equals(apply.getStatus()) && !"REJECTED".equals(apply.getStatus())) {
            throw new BizException("仅草稿/驳回状态可修改");
        }
        return apply;
    }

    /**
     * 变更库存并记账。changeQty 为正入库、为负出库。
     */
    private Map<String, Object> changeStock(String bizType, String bizNo, Long bizId,
                                            Long warehouseId, Long locationId, Long materialId, BigDecimal changeQty,
                                            Long operatorId, String remark) {
        StockService.StockChange cmd = new StockService.StockChange();
        cmd.setBizType(bizType);
        cmd.setBizNo(bizNo == null ? String.valueOf(bizId) : bizNo);
        cmd.setBizId(bizId);
        cmd.setWarehouseId(warehouseId);
        cmd.setLocationId(locationId);
        cmd.setMaterialId(materialId);
        cmd.setChangeQty(changeQty);
        cmd.setOperatorId(operatorId);
        cmd.setRemark(remark);
        StockRecord record = stockService.changeAuto(cmd);
        Map<String, Object> map = new HashMap<>();
        map.put("record", record);
        return map;
    }
}
