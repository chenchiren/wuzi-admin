package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.ApprovalFlowDef;
import com.relex.wuzi.domain.ApprovalFlowNode;
import com.relex.wuzi.domain.ApprovalInstance;
import com.relex.wuzi.service.FlowApprovalService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多级审批流：发起 / 通过 / 驳回 / 转交 / 加签。
 */
@RestController
@RequestMapping("/api/flow-approvals")
@RequiredArgsConstructor
public class FlowApprovalController {

    private final FlowApprovalService flowApprovalService;

    @Data
    public static class StartReq {
        /** 流程编码，如 PURCHASE_APPLY；空则按 bizType */
        private String flowCode;
        private String bizType;
        private Long bizId;
        private String bizNo;
        private String title;
    }

    @Data
    public static class ActionReq {
        private String comment;
    }

    @Data
    public static class TransferReq {
        private Long targetUserId;
        private String comment;
    }

    @Data
    public static class AddSignReq {
        private Long signUserId;
        private String comment;
    }

    @Data
    public static class SaveFlowReq {
        private String flowCode;
        private String flowName;
        private String bizType;
        private Integer status;
        private String remark;
    }

    @Data
    public static class SaveNodeReq {
        private String nodeNo;
        private String nodeName;
        private Integer orderNo;
        /** USER / ROLE / POSITION / DEPT_LEADER */
        private String approverType;
        private String approverRole;
        private Long assigneeId;
        private String positionCode;
        /** ALL / SELF / PARENT */
        private String deptScope;
    }

    /** 流程定义列表 */
    @GetMapping("/flows")
    public R<List<ApprovalFlowDef>> flows() {
        return R.ok(flowApprovalService.listFlows());
    }

    /** 配置项下拉：角色 / 职位 / 用户 / 审批人类型 / 部门范围 */
    @GetMapping("/flows/config-options")
    public R<Map<String, Object>> configOptions() {
        return R.ok(flowApprovalService.configOptions());
    }

    /** 流程节点 */
    @GetMapping("/flows/{flowCode}/nodes")
    public R<List<ApprovalFlowNode>> flowNodes(@PathVariable String flowCode) {
        return R.ok(flowApprovalService.listNodesByCode(flowCode));
    }

    @RequirePerm("flow:config:create")
    @PostMapping("/flows")
    @OpLog(module = "审批流配置", action = "新建流程")
    public R<ApprovalFlowDef> createFlow(@RequestBody SaveFlowReq req) {
        return R.ok(flowApprovalService.createFlow(req.getFlowCode(), req.getFlowName(),
                req.getBizType(), req.getStatus(), req.getRemark()));
    }

    @RequirePerm("flow:config:update")
    @PutMapping("/flows/{id}")
    @OpLog(module = "审批流配置", action = "编辑流程")
    public R<ApprovalFlowDef> updateFlow(@PathVariable Long id, @RequestBody SaveFlowReq req) {
        return R.ok(flowApprovalService.updateFlow(id, req.getFlowCode(), req.getFlowName(),
                req.getBizType(), req.getStatus(), req.getRemark()));
    }

    @RequirePerm("flow:config:delete")
    @DeleteMapping("/flows/{id}")
    @OpLog(module = "审批流配置", action = "删除流程")
    public R<Void> deleteFlow(@PathVariable Long id) {
        flowApprovalService.deleteFlow(id);
        return R.ok();
    }

    @RequirePerm("flow:config:update")
    @PostMapping("/flows/{id}/nodes")
    @OpLog(module = "审批流配置", action = "新建节点")
    public R<ApprovalFlowNode> createNode(@PathVariable Long id, @RequestBody SaveNodeReq req) {
        return R.ok(flowApprovalService.createNode(id, req.getNodeNo(), req.getNodeName(),
                req.getOrderNo(), req.getApproverType(), req.getApproverRole(),
                req.getAssigneeId(), req.getPositionCode(), req.getDeptScope()));
    }

    @RequirePerm("flow:config:update")
    @PutMapping("/flows/nodes/{nodeId}")
    @OpLog(module = "审批流配置", action = "编辑节点")
    public R<ApprovalFlowNode> updateNode(@PathVariable Long nodeId, @RequestBody SaveNodeReq req) {
        return R.ok(flowApprovalService.updateNode(nodeId, req.getNodeNo(), req.getNodeName(),
                req.getOrderNo(), req.getApproverType(), req.getApproverRole(),
                req.getAssigneeId(), req.getPositionCode(), req.getDeptScope()));
    }

    @RequirePerm("flow:config:delete")
    @DeleteMapping("/flows/nodes/{nodeId}")
    @OpLog(module = "审批流配置", action = "删除节点")
    public R<Void> deleteNode(@PathVariable Long nodeId) {
        flowApprovalService.deleteNode(nodeId);
        return R.ok();
    }

    /** 发起审批 */
    @RequirePerm("approval:start")
    @PostMapping("/start")
    @OpLog(module = "审批流", action = "发起审批")
    public R<ApprovalInstance> start(@RequestBody StartReq req,
                                     @RequestAttribute("userId") Long userId) {
        ApprovalInstance inst = flowApprovalService.start(req.getFlowCode(), req.getBizType(),
                req.getBizId(), req.getBizNo(), userId, req.getTitle(),
                status -> flowApprovalService.applyBizStatus(req.getBizType(), req.getBizId(), status));
        return R.ok(inst);
    }

    /** 我的待办（当前节点可审） */
    @GetMapping("/todo")
    public R<PageResult<ApprovalInstance>> todo(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String bizType,
                                                @RequestAttribute("userId") Long userId) {
        return R.ok(flowApprovalService.todo(userId, page, size, bizType));
    }

    /** 详情：实例 + 当前节点 + 节点列表 + 记录 */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(flowApprovalService.detail(id));
    }

    /** 当前节点通过 */
    @RequirePerm("approval:approve")
    @PostMapping("/{id}/approve")
    @OpLog(module = "审批流", action = "审批通过")
    public R<ApprovalInstance> approve(@PathVariable Long id,
                                       @RequestBody(required = false) ActionReq req,
                                       @RequestAttribute("userId") Long userId) {
        String comment = req != null ? req.getComment() : null;
        return R.ok(flowApprovalService.approve(id, userId, comment, null));
    }

    /** 当前节点驳回 */
    @RequirePerm("approval:reject")
    @PostMapping("/{id}/reject")
    @OpLog(module = "审批流", action = "审批驳回")
    public R<ApprovalInstance> reject(@PathVariable Long id,
                                      @RequestBody(required = false) ActionReq req,
                                      @RequestAttribute("userId") Long userId) {
        String comment = req != null ? req.getComment() : null;
        return R.ok(flowApprovalService.reject(id, userId, comment, null));
    }

    /** 转交 */
    @RequirePerm("approval:transfer")
    @PostMapping("/{id}/transfer")
    @OpLog(module = "审批流", action = "审批转交")
    public R<Void> transfer(@PathVariable Long id,
                            @RequestBody TransferReq req,
                            @RequestAttribute("userId") Long userId) {
        flowApprovalService.transfer(id, userId, req.getTargetUserId(), req.getComment());
        return R.ok();
    }

    /** 加签 */
    @RequirePerm("approval:add-sign")
    @PostMapping("/{id}/add-sign")
    @OpLog(module = "审批流", action = "审批加签")
    public R<Void> addSign(@PathVariable Long id,
                           @RequestBody AddSignReq req,
                           @RequestAttribute("userId") Long userId) {
        flowApprovalService.addSign(id, userId, req.getSignUserId(), req.getComment());
        return R.ok();
    }

    /** 是否可审（前端按钮控制） */
    @GetMapping("/{id}/can-approve")
    public R<Map<String, Boolean>> canApprove(@PathVariable Long id,
                                              @RequestAttribute("userId") Long userId) {
        return R.ok(Map.of("can", flowApprovalService.canApprove(id, userId)));
    }
}
