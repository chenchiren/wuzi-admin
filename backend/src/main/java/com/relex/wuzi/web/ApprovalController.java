package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.ApprovalInstanceMapper;
import com.relex.wuzi.mapper.ApprovalRecordMapper;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批中心：待办、详情、通过/驳回。
 * 绑定多级流程的实例走 FlowApprovalService；旧单级实例走 ApprovalService。
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalService approvalService;
    private final FlowApprovalService flowApprovalService;

    @Data
    public static class ActionReq {
        private String comment;
    }

    /**
     * 待办列表：批量加载 ext/角色/节点后过滤分页，避免 N+1。
     */
    @GetMapping("/todo")
    public R<PageResult<Map<String, Object>>> todo(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String bizType,
                                                   @RequestAttribute("userId") Long userId) {
        LambdaQueryWrapper<ApprovalInstance> qw = new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)
                .eq(bizType != null && !bizType.isBlank(), ApprovalInstance::getBizType, bizType)
                .orderByDesc(ApprovalInstance::getId);
        List<ApprovalInstance> allPending = instanceMapper.selectList(qw);
        List<FlowApprovalService.TodoRow> rows = flowApprovalService.buildTodoRows(allPending, userId);

        long total = rows.size();
        int from = (int) Math.max(0, (page - 1) * size);
        int to = (int) Math.min(rows.size(), from + size);
        List<Map<String, Object>> records = new ArrayList<>();
        if (from < rows.size()) {
            for (FlowApprovalService.TodoRow row : rows.subList(from, to)) {
                ApprovalInstance inst = row.inst();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", inst.getId());
                m.put("bizType", inst.getBizType());
                m.put("bizId", inst.getBizId());
                m.put("bizNo", inst.getBizNo());
                m.put("applicantId", inst.getApplicantId());
                m.put("title", inst.getTitle());
                m.put("status", inst.getStatus());
                m.put("createdAt", inst.getCreatedAt());
                m.put("updatedAt", inst.getUpdatedAt());
                m.put("hasFlow", row.hasFlow());
                m.put("currentNodeName", row.currentNodeName());
                records.add(m);
            }
        }
        return R.ok(PageResult.of(total, records));
    }

    /** 我发起的 */
    @GetMapping("/mine")
    public R<PageResult<ApprovalInstance>> mine(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestAttribute("userId") Long userId) {
        LambdaQueryWrapper<ApprovalInstance> qw = new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getApplicantId, userId)
                .orderByDesc(ApprovalInstance::getId);
        Page<ApprovalInstance> p = instanceMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 按业务单据查询审批流程（单据页展示节点用） */
    @GetMapping("/by-biz")
    public R<Map<String, Object>> byBiz(@RequestParam String bizType,
                                        @RequestParam Long bizId) {
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(bizType, bizId);
        if (inst == null) {
            // 旧单级也可能直接挂在 instance 上
            inst = instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                    .eq(ApprovalInstance::getBizType, bizType)
                    .eq(ApprovalInstance::getBizId, bizId)
                    .orderByDesc(ApprovalInstance::getId)
                    .last("LIMIT 1"));
        }
        if (inst == null) {
            return R.ok(Map.of(
                    "hasInstance", false,
                    "hasFlow", false,
                    "instance", Map.of(),
                    "records", List.of(),
                    "nodes", List.of()
            ));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hasInstance", true);
        map.put("instance", inst);
        map.put("records", recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, inst.getId())
                .orderByAsc(ApprovalRecord::getId)));
        boolean hasFlow = flowApprovalService.hasFlow(inst.getId());
        map.put("hasFlow", hasFlow);
        map.put("nodes", List.of());
        map.put("currentNode", null);
        map.put("currentApprovers", List.of());
        if (hasFlow) {
            try {
                Map<String, Object> fd = flowApprovalService.detail(inst.getId());
                map.put("nodes", fd.getOrDefault("nodes", List.of()));
                map.put("currentNode", fd.get("currentNode"));
                map.put("currentApprovers", fd.getOrDefault("currentApprovers", List.of()));
                map.put("flow", fd.get("flow"));
            } catch (BizException ignore) {
            }
        }
        return R.ok(map);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ApprovalInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instance", inst);
        map.put("records", recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, id)
                .orderByAsc(ApprovalRecord::getId)));
        boolean hasFlow = flowApprovalService.hasFlow(id);
        map.put("hasFlow", hasFlow);
        if (hasFlow) {
            try {
                map.putAll(flowApprovalService.detail(id));
            } catch (BizException ignore) {
            }
        }
        return R.ok(map);
    }

    /** 审批通过：多级推进 / 旧单级直接通过 */
    @RequirePerm("approval:approve")
    @PostMapping("/{id}/approve")
    // 按钮权限由 PermCodeResolver: approval:approve
    @Transactional(rollbackFor = Exception.class)
    public R<ApprovalInstance> approve(@PathVariable Long id,
                                       @RequestBody(required = false) ActionReq req,
                                       @RequestAttribute("userId") Long userId) {
        ApprovalInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        String comment = req != null ? req.getComment() : null;
        if (flowApprovalService.hasFlow(id)) {
            return R.ok(flowApprovalService.approve(id, userId, comment, null));
        }
        approvalService.approve(id, userId, comment,
                status -> flowApprovalService.applyBizStatus(inst.getBizType(), inst.getBizId(), status, userId));
        return R.ok(instanceMapper.selectById(id));
    }

    /** 审批驳回 */
    @RequirePerm("approval:reject")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<ApprovalInstance> reject(@PathVariable Long id,
                                      @RequestBody(required = false) ActionReq req,
                                      @RequestAttribute("userId") Long userId) {
        ApprovalInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        String comment = req != null ? req.getComment() : null;
        if (flowApprovalService.hasFlow(id)) {
            return R.ok(flowApprovalService.reject(id, userId, comment, null));
        }
        approvalService.reject(id, userId, comment,
                status -> flowApprovalService.applyBizStatus(inst.getBizType(), inst.getBizId(), status, userId));
        return R.ok(instanceMapper.selectById(id));
    }

    /** 转交（仅多级流程） */
    @RequirePerm("approval:transfer")
    @PostMapping("/{id}/transfer")
    public R<Void> transfer(@PathVariable Long id,
                            @RequestBody Map<String, Object> body,
                            @RequestAttribute("userId") Long userId) {
        if (!flowApprovalService.hasFlow(id)) {
            throw new BizException("旧单级实例不支持转交");
        }
        Object target = body.get("targetUserId");
        if (target == null) {
            throw new BizException("缺少转交目标用户");
        }
        Long targetUserId = Long.valueOf(String.valueOf(target));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        flowApprovalService.transfer(id, userId, targetUserId, comment);
        return R.ok();
    }

    /** 加签（仅多级流程） */
    @RequirePerm("approval:add-sign")
    @PostMapping("/{id}/add-sign")
    public R<Void> addSign(@PathVariable Long id,
                           @RequestBody Map<String, Object> body,
                           @RequestAttribute("userId") Long userId) {
        if (!flowApprovalService.hasFlow(id)) {
            throw new BizException("旧单级实例不支持加签");
        }
        Object sign = body.get("signUserId");
        if (sign == null) {
            throw new BizException("缺少加签用户");
        }
        Long signUserId = Long.valueOf(String.valueOf(sign));
        String comment = body.get("comment") == null ? null : String.valueOf(body.get("comment"));
        flowApprovalService.addSign(id, userId, signUserId, comment);
        return R.ok();
    }
}
