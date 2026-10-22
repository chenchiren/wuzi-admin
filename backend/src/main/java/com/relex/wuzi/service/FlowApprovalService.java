package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * 多级审批引擎：按 flow 定义顺序推进节点。
 * 发起(start) → 当前节点通过(approve)到下一节点 / 驳回(reject) / 转交(transfer) / 加签(addSign)。
 * 全部节点通过后实例置 APPROVED，并回调业务状态。
 */
@Service
@RequiredArgsConstructor
public class FlowApprovalService {

    public static final String ACT_START = "START";
    public static final String ACT_APPROVE = "APPROVE";
    public static final String ACT_REJECT = "REJECT";
    public static final String ACT_TRANSFER = "TRANSFER";
    public static final String ACT_ADD_SIGN = "ADD_SIGN";
    /** 驳回后修改内容并重新发起：同实例从首节点重走，历史记录保留 */
    public static final String ACT_RESUBMIT = "RESUBMIT";

    public static final String FLOW_PURCHASE_APPLY = ApprovalService.BIZ_APPLY;
    public static final String FLOW_PURCHASE_ORDER = ApprovalService.BIZ_ORDER;

    public static final String ROLE_ADMIN = "ADMIN";

    private final ApprovalFlowDefMapper flowDefMapper;
    private final ApprovalFlowNodeMapper flowNodeMapper;
    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalInstanceExtMapper extMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysPositionMapper positionMapper;
    private final OrgPositionService orgPositionService;
    private final MessageService messageService;
    private final PurchaseApplyMapper applyMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseReturnMapper returnMapper;
    private final PurchaseInquiryMapper inquiryMapper;
    private final MaterialUseApplyMapper materialUseMapper;
    private final BorrowOrderMapper borrowMapper;
    private final ScrapOrderMapper scrapMapper;
    private final TransferOrderMapper transferMapper;
    private final ReturnStoreOrderMapper returnStoreMapper;
    private final InventoryCheckMapper inventoryCheckMapper;
    private final PurchaseArrivalMapper arrivalMapper;
    private final OutboundOrderMapper outboundMapper;

    // ---------- 流程定义 ----------

    public ApprovalFlowDef requireFlow(String flowCode) {
        ApprovalFlowDef flow = flowDefMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowDef>()
                .eq(ApprovalFlowDef::getFlowCode, flowCode)
                .and(w -> w.eq(ApprovalFlowDef::getIsDeleted, 0).or().isNull(ApprovalFlowDef::getIsDeleted))
                .last("limit 1"));
        if (flow == null) {
            throw new BizException("审批流不存在: " + flowCode);
        }
        return flow;
    }

    public List<ApprovalFlowDef> listFlows() {
        return flowDefMapper.selectList(new LambdaQueryWrapper<ApprovalFlowDef>()
                .and(w -> w.eq(ApprovalFlowDef::getIsDeleted, 0).or().isNull(ApprovalFlowDef::getIsDeleted))
                .orderByAsc(ApprovalFlowDef::getId));
    }

    public List<ApprovalFlowNode> listNodes(Long flowId) {
        return flowNodeMapper.selectList(new LambdaQueryWrapper<ApprovalFlowNode>()
                .eq(ApprovalFlowNode::getFlowId, flowId)
                .and(w -> w.eq(ApprovalFlowNode::getIsDeleted, 0).or().isNull(ApprovalFlowNode::getIsDeleted))
                .orderByAsc(ApprovalFlowNode::getOrderNo)
                .orderByAsc(ApprovalFlowNode::getId));
    }

    public List<ApprovalFlowNode> listNodesByCode(String flowCode) {
        return listNodes(requireFlow(flowCode).getId());
    }

    // ---------- 流程配置 CRUD ----------

    @Transactional(rollbackFor = Exception.class)
    public ApprovalFlowDef createFlow(String flowCode, String flowName, String bizType,
                                      Integer status, String remark) {
        if (flowCode == null || flowCode.isBlank()) {
            throw new BizException("流程编码不能为空");
        }
        if (flowName == null || flowName.isBlank()) {
            throw new BizException("流程名称不能为空");
        }
        String code = flowCode.trim().toUpperCase(Locale.ROOT);
        Long exists = flowDefMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowDef>()
                .eq(ApprovalFlowDef::getFlowCode, code)
                .and(w -> w.eq(ApprovalFlowDef::getIsDeleted, 0).or().isNull(ApprovalFlowDef::getIsDeleted)));
        if (exists != null && exists > 0) {
            throw new BizException("流程编码已存在: " + code);
        }
        ApprovalFlowDef flow = new ApprovalFlowDef();
        flow.setFlowCode(code);
        flow.setFlowName(flowName.trim());
        flow.setBizType(bizType != null && !bizType.isBlank() ? bizType.trim() : code);
        flow.setStatus(status == null ? 1 : status);
        flow.setRemark(remark);
        flow.setIsDeleted(0);
        flow.setCreatedAt(LocalDateTime.now());
        flow.setUpdatedAt(LocalDateTime.now());
        flowDefMapper.insert(flow);
        return flow;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalFlowDef updateFlow(Long id, String flowCode, String flowName, String bizType,
                                      Integer status, String remark) {
        ApprovalFlowDef flow = requireFlowById(id);
        if (flowCode != null && !flowCode.isBlank()) {
            String code = flowCode.trim().toUpperCase(Locale.ROOT);
            Long exists = flowDefMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowDef>()
                    .eq(ApprovalFlowDef::getFlowCode, code)
                    .ne(ApprovalFlowDef::getId, id)
                    .and(w -> w.eq(ApprovalFlowDef::getIsDeleted, 0).or().isNull(ApprovalFlowDef::getIsDeleted)));
            if (exists != null && exists > 0) {
                throw new BizException("流程编码已存在: " + code);
            }
            flow.setFlowCode(code);
        }
        if (flowName != null && !flowName.isBlank()) {
            flow.setFlowName(flowName.trim());
        }
        if (bizType != null && !bizType.isBlank()) {
            flow.setBizType(bizType.trim());
        }
        if (status != null) {
            flow.setStatus(status);
        }
        if (remark != null) {
            flow.setRemark(remark);
        }
        flow.setUpdatedAt(LocalDateTime.now());
        flowDefMapper.updateById(flow);
        return flow;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFlow(Long id) {
        ApprovalFlowDef flow = requireFlowById(id);
        Long pending = instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)
                .inSql(ApprovalInstance::getId,
                        "SELECT instance_id FROM approval_instance_ext WHERE flow_id = " + flow.getId()));
        if (pending != null && pending > 0) {
            throw new BizException("该流程仍有 " + pending + " 条待审实例，暂不能删除");
        }
        flow.setIsDeleted(1);
        flow.setUpdatedAt(LocalDateTime.now());
        flowDefMapper.updateById(flow);
        for (ApprovalFlowNode n : listNodes(flow.getId())) {
            n.setIsDeleted(1);
            flowNodeMapper.updateById(n);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalFlowNode createNode(Long flowId, String nodeNo, String nodeName, Integer orderNo,
                                       String approverType, String approverRole, Long assigneeId,
                                       String positionCode, String deptScope) {
        requireFlowById(flowId);
        if (nodeNo == null || nodeNo.isBlank()) {
            throw new BizException("节点编码不能为空");
        }
        if (nodeName == null || nodeName.isBlank()) {
            throw new BizException("节点名称不能为空");
        }
        String no = nodeNo.trim().toUpperCase(Locale.ROOT);
        Long exists = flowNodeMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowNode>()
                .eq(ApprovalFlowNode::getFlowId, flowId)
                .eq(ApprovalFlowNode::getNodeNo, no)
                .and(w -> w.eq(ApprovalFlowNode::getIsDeleted, 0).or().isNull(ApprovalFlowNode::getIsDeleted)));
        if (exists != null && exists > 0) {
            throw new BizException("节点编码已存在: " + no);
        }
        ApprovalFlowNode node = new ApprovalFlowNode();
        node.setFlowId(flowId);
        node.setNodeNo(no);
        node.setNodeName(nodeName.trim());
        node.setOrderNo(orderNo == null ? nextOrderNo(flowId) : orderNo);
        applyApprover(node, approverType, approverRole, assigneeId, positionCode, deptScope);
        node.setIsDeleted(0);
        node.setCreatedAt(LocalDateTime.now());
        flowNodeMapper.insert(node);
        return node;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalFlowNode updateNode(Long nodeId, String nodeNo, String nodeName, Integer orderNo,
                                       String approverType, String approverRole, Long assigneeId,
                                       String positionCode, String deptScope) {
        ApprovalFlowNode node = requireNodeById(nodeId);
        if (nodeNo != null && !nodeNo.isBlank()) {
            String no = nodeNo.trim().toUpperCase(Locale.ROOT);
            Long exists = flowNodeMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowNode>()
                    .eq(ApprovalFlowNode::getFlowId, node.getFlowId())
                    .eq(ApprovalFlowNode::getNodeNo, no)
                    .ne(ApprovalFlowNode::getId, nodeId)
                    .and(w -> w.eq(ApprovalFlowNode::getIsDeleted, 0).or().isNull(ApprovalFlowNode::getIsDeleted)));
            if (exists != null && exists > 0) {
                throw new BizException("节点编码已存在: " + no);
            }
            node.setNodeNo(no);
        }
        if (nodeName != null && !nodeName.isBlank()) {
            node.setNodeName(nodeName.trim());
        }
        if (orderNo != null) {
            node.setOrderNo(orderNo);
        }
        applyApprover(node, approverType, approverRole, assigneeId, positionCode, deptScope);
        flowNodeMapper.updateById(node);
        return node;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        ApprovalFlowNode node = requireNodeById(nodeId);
        Long pending = instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)
                .inSql(ApprovalInstance::getId,
                        "SELECT instance_id FROM approval_instance_ext WHERE flow_id = " + node.getFlowId()
                                + " AND current_node_no = '" + node.getNodeNo().replace("'", "") + "'"));
        if (pending != null && pending > 0) {
            throw new BizException("该节点仍有待审实例，暂不能删除");
        }
        node.setIsDeleted(1);
        flowNodeMapper.updateById(node);
    }

    public Map<String, Object> configOptions() {
        List<Map<String, Object>> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .and(w -> w.eq(SysRole::getIsDeleted, 0).or().isNull(SysRole::getIsDeleted))
                        .orderByAsc(SysRole::getId))
                .stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", r.getRoleCode());
                    m.put("label", r.getRoleName());
                    return m;
                })
                .toList();
        List<Map<String, Object>> positions = positionMapper.selectList(new LambdaQueryWrapper<SysPosition>()
                        .eq(SysPosition::getStatus, 1)
                        .and(w -> w.eq(SysPosition::getIsDeleted, 0).or().isNull(SysPosition::getIsDeleted))
                        .orderByDesc(SysPosition::getPositionLevel)
                        .orderByAsc(SysPosition::getSortNo))
                .stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", p.getPositionCode());
                    m.put("label", p.getPositionName() + "（" + p.getPositionCode() + "）");
                    return m;
                })
                .toList();
        List<Map<String, Object>> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .and(w -> w.eq(SysUser::getIsDeleted, 0).or().isNull(SysUser::getIsDeleted))
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("value", u.getId());
                    String name = u.getRealName() != null && !u.getRealName().isBlank()
                            ? u.getRealName() : u.getUsername();
                    m.put("label", name + "（" + u.getUsername() + "）");
                    return m;
                })
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roles", roles);
        data.put("positions", positions);
        data.put("users", users);
        data.put("approverTypes", List.of(
                Map.of("value", OrgPositionService.TYPE_USER, "label", "指定人员"),
                Map.of("value", OrgPositionService.TYPE_ROLE, "label", "按角色"),
                Map.of("value", OrgPositionService.TYPE_POSITION, "label", "按职位"),
                Map.of("value", OrgPositionService.TYPE_DEPT_LEADER, "label", "部门负责人"),
                Map.of("value", OrgPositionService.TYPE_TEAM_LEADER, "label", "小组长")
        ));
        data.put("deptScopes", List.of(
                Map.of("value", OrgPositionService.SCOPE_ALL, "label", "不限部门"),
                Map.of("value", OrgPositionService.SCOPE_SELF, "label", "同节点"),
                Map.of("value", OrgPositionService.SCOPE_DEPT, "label", "本部门含兄弟组"),
                Map.of("value", OrgPositionService.SCOPE_PARENT, "label", "上级组织")
        ));
        return data;
    }

    private void applyApprover(ApprovalFlowNode node, String approverType, String approverRole,
                               Long assigneeId, String positionCode, String deptScope) {
        String type = approverType == null || approverType.isBlank()
                ? OrgPositionService.TYPE_ROLE
                : approverType.trim().toUpperCase(Locale.ROOT);
        node.setApproverType(type);
        node.setApproverRole(null);
        node.setAssigneeId(null);
        node.setPositionCode(null);
        node.setDeptScope(deptScope == null || deptScope.isBlank()
                ? OrgPositionService.SCOPE_ALL
                : deptScope.trim().toUpperCase(Locale.ROOT));

        switch (type) {
            case OrgPositionService.TYPE_USER -> {
                if (assigneeId == null) {
                    throw new BizException("指定人员时必须选择审批人");
                }
                node.setAssigneeId(assigneeId);
            }
            case OrgPositionService.TYPE_ROLE -> {
                if (approverRole == null || approverRole.isBlank()) {
                    throw new BizException("按角色时必须选择角色");
                }
                node.setApproverRole(approverRole.trim());
            }
            case OrgPositionService.TYPE_POSITION -> {
                if (positionCode == null || positionCode.isBlank()) {
                    throw new BizException("按职位时必须选择职位");
                }
                node.setPositionCode(positionCode.trim());
            }
            case OrgPositionService.TYPE_DEPT_LEADER, OrgPositionService.TYPE_TEAM_LEADER -> {
                // 组织负责人按节点+职位解析，无需额外目标
            }
            default -> throw new BizException("不支持的审批人类型: " + type);
        }
    }

    private int nextOrderNo(Long flowId) {
        List<ApprovalFlowNode> nodes = listNodes(flowId);
        return nodes.stream()
                .map(ApprovalFlowNode::getOrderNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private ApprovalFlowDef requireFlowById(Long id) {
        ApprovalFlowDef flow = id == null ? null : flowDefMapper.selectById(id);
        if (flow == null || (flow.getIsDeleted() != null && flow.getIsDeleted() == 1)) {
            throw new BizException("审批流不存在");
        }
        return flow;
    }

    private ApprovalFlowNode requireNodeById(Long id) {
        ApprovalFlowNode node = id == null ? null : flowNodeMapper.selectById(id);
        if (node == null || (node.getIsDeleted() != null && node.getIsDeleted() == 1)) {
            throw new BizException("审批节点不存在");
        }
        return node;
    }

    // ---------- 发起 ----------

    /**
     * 发起审批：创建实例并进入流程首节点。
     *
     * @param flowCode 流程编码，如 PURCHASE_APPLY；空则按 bizType 查找
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstance start(String flowCode, String bizType, Long bizId, String bizNo,
                                  Long applicantId, String title,
                                  ApprovalService.BusinessStatusUpdater updater) {
        String code = flowCode != null && !flowCode.isBlank() ? flowCode : bizType;
        ApprovalFlowDef flow = flowDefMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowDef>()
                .eq(ApprovalFlowDef::getFlowCode, code)
                .and(w -> w.eq(ApprovalFlowDef::getIsDeleted, 0).or().isNull(ApprovalFlowDef::getIsDeleted))
                .last("limit 1"));
        // 流程未配置时回退为单级审批实例（无 ext），兼容旧数据
        if (flow == null) {
            return startSimple(bizType != null ? bizType : code, bizId, bizNo, applicantId, title, updater);
        }
        List<ApprovalFlowNode> nodes = listNodes(flow.getId());
        if (nodes.isEmpty()) {
            throw new BizException("审批流未配置节点: " + code);
        }

        // 防止并发重复提交产生多条待审实例
        String resolvedBizType = bizType != null ? bizType : flow.getBizType();
        ApprovalInstance existing = getByBizIfPresent(resolvedBizType, bizId);
        if (existing != null && ApprovalService.INST_PENDING.equals(existing.getStatus())) {
            throw new BizException("该单据已有待审实例，请勿重复提交");
        }

        // 驳回后重新发起：复用原实例从首节点重走，保留全部历史记录
        if (existing != null && ApprovalService.INST_REJECTED.equals(existing.getStatus())) {
            return resumeFromFirstNode(existing, flow, nodes, applicantId, title, updater);
        }

        ApprovalInstance inst = new ApprovalInstance();
        inst.setBizType(bizType != null ? bizType : flow.getBizType());
        inst.setBizId(bizId);
        inst.setBizNo(bizNo);
        inst.setApplicantId(applicantId);
        inst.setTitle(title);
        inst.setStatus(ApprovalService.INST_PENDING);
        inst.setIsDeleted(0);
        inst.setCreatedAt(LocalDateTime.now());
        inst.setUpdatedAt(LocalDateTime.now());
        instanceMapper.insert(inst);

        ApprovalFlowNode first = nodes.get(0);
        ApprovalInstanceExt ext = new ApprovalInstanceExt();
        ext.setInstanceId(inst.getId());
        ext.setFlowId(flow.getId());
        ext.setCurrentNodeNo(first.getNodeNo());
        ext.setCurrentOrderNo(first.getOrderNo());
        ext.setAssigneeId(first.getAssigneeId());
        ext.setCreatedAt(LocalDateTime.now());
        ext.setUpdatedAt(LocalDateTime.now());
        extMapper.insert(ext);

        writeRecord(inst.getId(), first.getNodeName(), applicantId, ACT_START, "发起审批");
        notifyCurrentNode(inst, first, ext, "您有新的审批待办：" + nvl(title));

        applyStatus(updater, inst.getBizType(), bizId, ApprovalService.ST_PENDING);
        return inst;
    }

    /** 流程未定义时的单级发起（不写 ext） */
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public ApprovalInstance startSimple(String bizType, Long bizId, String bizNo,
                                        Long applicantId, String title,
                                        ApprovalService.BusinessStatusUpdater updater) {
        ApprovalInstance existing = getByBizIfPresent(bizType, bizId);
        if (existing != null && ApprovalService.INST_PENDING.equals(existing.getStatus())) {
            throw new BizException("该单据已有待审实例，请勿重复提交");
        }
        // 驳回后重新发起：同实例回到待审（单级从头）
        if (existing != null && ApprovalService.INST_REJECTED.equals(existing.getStatus())) {
            existing.setStatus(ApprovalService.INST_PENDING);
            existing.setTitle(title);
            existing.setUpdatedAt(LocalDateTime.now());
            instanceMapper.updateById(existing);
            writeRecord(existing.getId(), "DEFAULT", applicantId, ACT_RESUBMIT, "修改后重新发起（从头审批）");
            applyStatus(updater, bizType, bizId, ApprovalService.ST_PENDING);
            try {
                messageService.send(applicantId, "审批已重新提交", nvl(title) + " 已重新进入审批", bizType, bizId);
            } catch (Exception ignore) {
            }
            return existing;
        }
        ApprovalInstance inst = new ApprovalInstance();
        inst.setBizType(bizType);
        inst.setBizId(bizId);
        inst.setBizNo(bizNo);
        inst.setApplicantId(applicantId);
        inst.setTitle(title);
        inst.setStatus(ApprovalService.INST_PENDING);
        inst.setIsDeleted(0);
        inst.setCreatedAt(LocalDateTime.now());
        inst.setUpdatedAt(LocalDateTime.now());
        instanceMapper.insert(inst);
        writeRecord(inst.getId(), "DEFAULT", applicantId, ACT_START, "发起审批（单级）");
        applyStatus(updater, bizType, bizId, ApprovalService.ST_PENDING);
        try {
            messageService.send(applicantId, "审批已提交", nvl(title) + " 已进入审批", bizType, bizId);
        } catch (Exception ignore) {
        }
        return inst;
    }

    /** 驳回后重新发起：实例回到首节点 PENDING，历史记录完整保留 */
    private ApprovalInstance resumeFromFirstNode(ApprovalInstance inst, ApprovalFlowDef flow,
                                                 List<ApprovalFlowNode> nodes,
                                                 Long applicantId, String title,
                                                 ApprovalService.BusinessStatusUpdater updater) {
        ApprovalFlowNode first = nodes.get(0);
        ApprovalInstanceExt ext = extMapper.selectOne(new LambdaQueryWrapper<ApprovalInstanceExt>()
                .eq(ApprovalInstanceExt::getInstanceId, inst.getId())
                .last("limit 1"));
        if (ext == null) {
            ext = new ApprovalInstanceExt();
            ext.setInstanceId(inst.getId());
            ext.setCreatedAt(LocalDateTime.now());
        }
        ext.setFlowId(flow.getId());
        ext.setCurrentNodeNo(first.getNodeNo());
        ext.setCurrentOrderNo(first.getOrderNo());
        ext.setAssigneeId(first.getAssigneeId());
        ext.setExtraApproverIds(null);
        ext.setUpdatedAt(LocalDateTime.now());
        if (ext.getId() == null) {
            extMapper.insert(ext);
        } else {
            extMapper.updateById(ext);
        }

        inst.setStatus(ApprovalService.INST_PENDING);
        if (title != null && !title.isBlank()) {
            inst.setTitle(title);
        }
        inst.setUpdatedAt(LocalDateTime.now());
        instanceMapper.updateById(inst);

        writeRecord(inst.getId(), first.getNodeName(), applicantId, ACT_RESUBMIT, "修改后重新发起（从头审批）");
        notifyCurrentNode(inst, first, ext, "您有新的审批待办：" + nvl(inst.getTitle()));
        applyStatus(updater, inst.getBizType(), inst.getBizId(), ApprovalService.ST_PENDING);
        return inst;
    }

    /** 实例是否绑定多级流程 */
    public boolean hasFlow(Long instanceId) {
        return extMapper.selectCount(new LambdaQueryWrapper<ApprovalInstanceExt>()
                .eq(ApprovalInstanceExt::getInstanceId, instanceId)) > 0;
    }

    /** 按业务查最近审批实例；无则 null */
    public ApprovalInstance getByBizIfPresent(String bizType, Long bizId) {
        if (bizType == null || bizId == null) {
            return null;
        }
        return instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getBizType, bizType)
                .eq(ApprovalInstance::getBizId, bizId)
                .orderByDesc(ApprovalInstance::getId)
                .last("limit 1"));
    }

    // ---------- 通过 / 驳回 / 转交 / 加签 ----------

    /** 当前节点通过：推进到下一节点；若已是末节点则实例 APPROVED 并回调业务状态 */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstance approve(Long instanceId, Long operatorId, String comment,
                                    ApprovalService.BusinessStatusUpdater updater) {
        Ctx ctx = loadCtx(instanceId);
        requireCanOperate(ctx, operatorId, "审批");

        writeRecord(instanceId, ctx.node.getNodeName(), operatorId, ACT_APPROVE, comment);

        ApprovalFlowNode next = nextNode(ctx);
        if (next == null) {
            ctx.inst.setStatus(ApprovalService.INST_APPROVED);
            ctx.inst.setUpdatedAt(LocalDateTime.now());
            instanceMapper.updateById(ctx.inst);
            messageService.send(ctx.inst.getApplicantId(), "审批通过",
                    "「" + nvl(ctx.inst.getTitle()) + "」已全部审批通过",
                    MessageService.BIZ_APPROVAL, instanceId);
            applyStatus(updater, ctx.inst.getBizType(), ctx.inst.getBizId(), ApprovalService.ST_APPROVED, operatorId);
        } else {
            advanceTo(ctx, next);
            notifyCurrentNode(ctx.inst, next, ctx.ext, "您有新的审批待办：" + nvl(ctx.inst.getTitle()));
        }
        return instanceMapper.selectById(instanceId);
    }

    /** 当前节点驳回：实例 REJECTED，业务单 REJECTED */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstance reject(Long instanceId, Long operatorId, String comment,
                                   ApprovalService.BusinessStatusUpdater updater) {
        Ctx ctx = loadCtx(instanceId);
        requireCanOperate(ctx, operatorId, "驳回");

        writeRecord(instanceId, ctx.node.getNodeName(), operatorId, ACT_REJECT, comment);
        ctx.inst.setStatus(ApprovalService.INST_REJECTED);
        ctx.inst.setUpdatedAt(LocalDateTime.now());
        instanceMapper.updateById(ctx.inst);

        messageService.send(ctx.inst.getApplicantId(), "审批驳回",
                "「" + nvl(ctx.inst.getTitle()) + "」被驳回" + tailComment(comment),
                MessageService.BIZ_APPROVAL, instanceId);
        applyStatus(updater, ctx.inst.getBizType(), ctx.inst.getBizId(), ApprovalService.ST_REJECTED);
        return instanceMapper.selectById(instanceId);
    }

    /** 转交：当前节点审批权交给 targetUserId */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long instanceId, Long operatorId, Long targetUserId, String comment) {
        if (targetUserId == null || targetUserId.equals(operatorId)) {
            throw new BizException("转交目标用户无效");
        }
        Ctx ctx = loadCtx(instanceId);
        requireCanOperate(ctx, operatorId, "转交");
        if (userMapper.selectById(targetUserId) == null) {
            throw new BizException("转交目标用户不存在");
        }

        writeRecord(instanceId, ctx.node.getNodeName(), operatorId, ACT_TRANSFER,
                "转交给用户" + targetUserId + tailComment(comment));
        ctx.ext.setAssigneeId(targetUserId);
        ctx.ext.setUpdatedAt(LocalDateTime.now());
        extMapper.updateById(ctx.ext);

        messageService.send(targetUserId, "审批转交",
                "「" + nvl(ctx.inst.getTitle()) + "」转交给您处理" + tailComment(comment),
                MessageService.BIZ_APPROVAL, instanceId);
    }

    /** 加签：追加 signUserId 为当前节点可审人（不改变节点顺序） */
    @Transactional(rollbackFor = Exception.class)
    public void addSign(Long instanceId, Long operatorId, Long signUserId, String comment) {
        if (signUserId == null || signUserId.equals(operatorId)) {
            throw new BizException("加签用户无效");
        }
        Ctx ctx = loadCtx(instanceId);
        requireCanOperate(ctx, operatorId, "加签");
        if (userMapper.selectById(signUserId) == null) {
            throw new BizException("加签用户不存在");
        }

        writeRecord(instanceId, ctx.node.getNodeName(), operatorId, ACT_ADD_SIGN,
                "加签用户" + signUserId + tailComment(comment));

        Set<Long> extras = parseExtra(ctx.ext.getExtraApproverIds());
        extras.add(signUserId);
        ctx.ext.setExtraApproverIds(extras.stream().map(String::valueOf).collect(Collectors.joining(",")));
        ctx.ext.setUpdatedAt(LocalDateTime.now());
        extMapper.updateById(ctx.ext);

        messageService.send(signUserId, "审批加签",
                "「" + nvl(ctx.inst.getTitle()) + "」加签给您，请协助审批" + tailComment(comment),
                MessageService.BIZ_APPROVAL, instanceId);
    }

    // ---------- 查询 ----------

    public boolean canApprove(Long instanceId, Long userId) {
        try {
            Ctx ctx = loadCtx(instanceId);
            return canOperate(ctx, userId);
        } catch (BizException e) {
            return false;
        }
    }

    /** 我的待办：当前节点可由我处理的 PENDING 实例（批量查询，避免 N+1） */
    public PageResult<ApprovalInstance> todo(Long userId, long page, long size, String bizType) {
        LambdaQueryWrapper<ApprovalInstance> qw = new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)
                .eq(bizType != null && !bizType.isBlank(), ApprovalInstance::getBizType, bizType)
                .orderByDesc(ApprovalInstance::getId);
        List<ApprovalInstance> all = instanceMapper.selectList(qw);
        List<TodoRow> rows = buildTodoRows(all, userId);
        long total = rows.size();
        int from = (int) Math.max(0, (page - 1) * size);
        int to = (int) Math.min(total, from + size);
        List<ApprovalInstance> records = from >= total
                ? List.of()
                : rows.subList(from, to).stream().map(r -> r.inst).toList();
        return PageResult.of(total, new ArrayList<>(records));
    }

    /** 批量构建待办行：一次查 ext / 角色 / 节点，避免每条实例多次查询 */
    public List<TodoRow> buildTodoRows(List<ApprovalInstance> pending, Long userId) {
        List<TodoRow> result = new ArrayList<>();
        if (pending == null || pending.isEmpty()) {
            return result;
        }

        List<Long> ids = pending.stream().map(ApprovalInstance::getId).toList();
        List<ApprovalInstanceExt> exts = extMapper.selectList(new LambdaQueryWrapper<ApprovalInstanceExt>()
                .in(ApprovalInstanceExt::getInstanceId, ids));
        Map<Long, ApprovalInstanceExt> extMap = new HashMap<>();
        for (ApprovalInstanceExt e : exts) {
            extMap.put(e.getInstanceId(), e);
        }

        Set<Long> flowIds = new LinkedHashSet<>();
        for (ApprovalInstanceExt e : exts) {
            if (e.getFlowId() != null) {
                flowIds.add(e.getFlowId());
            }
        }
        Map<Long, List<ApprovalFlowNode>> nodesByFlow = new HashMap<>();
        if (!flowIds.isEmpty()) {
            List<ApprovalFlowNode> allNodes = flowNodeMapper.selectList(new LambdaQueryWrapper<ApprovalFlowNode>()
                    .in(ApprovalFlowNode::getFlowId, flowIds)
                    .orderByAsc(ApprovalFlowNode::getOrderNo));
            for (ApprovalFlowNode n : allNodes) {
                nodesByFlow.computeIfAbsent(n.getFlowId(), k -> new ArrayList<>()).add(n);
            }
        }

        Set<String> userRoleCodes = userId == null ? Set.of() : loadRoleCodes(userId);
        boolean isAdmin = userRoleCodes.contains(ROLE_ADMIN);

        for (ApprovalInstance inst : pending) {
            ApprovalInstanceExt ext = extMap.get(inst.getId());
            boolean hasFlow = ext != null && ext.getFlowId() != null;
            String nodeName = "";
            if (hasFlow) {
                List<ApprovalFlowNode> nodes = nodesByFlow.getOrDefault(ext.getFlowId(), List.of());
                ApprovalFlowNode current = nodes.stream()
                        .filter(n -> Objects.equals(n.getNodeNo(), ext.getCurrentNodeNo()))
                        .findFirst()
                        .orElse(null);
                if (current == null || !canOperateCached(ext, current, inst.getApplicantId(), userId, userRoleCodes, isAdmin)) {
                    continue;
                }
                nodeName = current.getNodeName();
            }
            TodoRow row = new TodoRow(inst, hasFlow, nodeName);
            result.add(row);
        }
        return result;
    }

    public record TodoRow(ApprovalInstance inst, boolean hasFlow, String currentNodeName) {
    }

    public Map<String, Object> detail(Long instanceId) {
        Ctx ctx = loadCtx(instanceId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("instance", ctx.inst);
        map.put("ext", ctx.ext);
        map.put("flow", flowDefMapper.selectById(ctx.ext.getFlowId()));
        map.put("nodes", ctx.nodes);
        map.put("currentNode", ctx.node);
        map.put("records", recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, instanceId)
                .orderByAsc(ApprovalRecord::getId)));
        map.put("currentApprovers", resolveApprovers(ctx.node, ctx.ext, ctx.inst.getApplicantId()));
        return map;
    }

    // ---------- 业务状态回写 ----------

    /** 默认业务状态回写（updater 为空时使用） */
    public void applyBizStatus(String bizType, Long bizId, String status) {
        applyBizStatus(bizType, bizId, status, null);
    }

    /** 默认业务状态回写，operatorId 用于回写审批人（如询价 approved_by） */
    public void applyBizStatus(String bizType, Long bizId, String status, Long operatorId) {
        if (bizType == null || bizId == null) {
            return;
        }
        switch (bizType) {
            case ApprovalService.BIZ_APPLY -> {
                PurchaseApply apply = applyMapper.selectById(bizId);
                if (apply != null) {
                    apply.setStatus(status);
                    applyMapper.updateById(apply);
                }
            }
            case FLOW_PURCHASE_ORDER -> {
                PurchaseOrder order = orderMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    orderMapper.updateById(order);
                }
            }
            case ApprovalService.BIZ_RETURN -> {
                PurchaseReturn ret = returnMapper.selectById(bizId);
                if (ret != null) {
                    ret.setStatus(status);
                    returnMapper.updateById(ret);
                }
            }
            case ApprovalService.BIZ_INQUIRY -> {
                PurchaseInquiry inquiry = inquiryMapper.selectById(bizId);
                if (inquiry != null) {
                    inquiry.setStatus(status);
                    if (ApprovalService.ST_APPROVED.equals(status)) {
                        inquiry.setApprovedBy(operatorId);
                        inquiry.setApprovedAt(LocalDateTime.now());
                    }
                    inquiryMapper.updateById(inquiry);
                }
            }
            case ApprovalService.BIZ_MATERIAL_USE -> {
                MaterialUseApply apply = materialUseMapper.selectById(bizId);
                if (apply != null) {
                    apply.setStatus(status);
                    materialUseMapper.updateById(apply);
                }
            }
            case ApprovalService.BIZ_BORROW -> {
                BorrowOrder order = borrowMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    borrowMapper.updateById(order);
                }
            }
            case ApprovalService.BIZ_SCRAP -> {
                ScrapOrder order = scrapMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    scrapMapper.updateById(order);
                }
            }
            case ApprovalService.BIZ_TRANSFER -> {
                TransferOrder order = transferMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    transferMapper.updateById(order);
                }
            }
            case ApprovalService.BIZ_RETURN_STORE -> {
                ReturnStoreOrder order = returnStoreMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    returnStoreMapper.updateById(order);
                }
            }
            case ApprovalService.BIZ_INVENTORY -> {
                InventoryCheck check = inventoryCheckMapper.selectById(bizId);
                if (check != null) {
                    check.setStatus(status);
                    inventoryCheckMapper.updateById(check);
                }
            }
            case ApprovalService.BIZ_ARRIVAL -> {
                PurchaseArrival arrival = arrivalMapper.selectById(bizId);
                if (arrival != null) {
                    arrival.setStatus(status);
                    arrivalMapper.updateById(arrival);
                }
            }
            case ApprovalService.BIZ_OUTBOUND -> {
                OutboundOrder order = outboundMapper.selectById(bizId);
                if (order != null) {
                    order.setStatus(status);
                    outboundMapper.updateById(order);
                }
            }
            default -> {
                // 未知业务类型：仅回调 updater，不强制落库
            }
        }
    }

    // ---------- 内部 ----------

    private void applyStatus(ApprovalService.BusinessStatusUpdater updater, String bizType, Long bizId, String status) {
        applyStatus(updater, bizType, bizId, status, null);
    }

    private void applyStatus(ApprovalService.BusinessStatusUpdater updater, String bizType, Long bizId,
                             String status, Long operatorId) {
        if (updater != null) {
            updater.updateStatus(status);
        } else {
            applyBizStatus(bizType, bizId, status, operatorId);
        }
    }

    private void advanceTo(Ctx ctx, ApprovalFlowNode next) {
        ctx.ext.setCurrentNodeNo(next.getNodeNo());
        ctx.ext.setCurrentOrderNo(next.getOrderNo());
        ctx.ext.setAssigneeId(next.getAssigneeId());
        ctx.ext.setExtraApproverIds(null);
        ctx.ext.setUpdatedAt(LocalDateTime.now());
        extMapper.updateById(ctx.ext);
    }

    private ApprovalFlowNode nextNode(Ctx ctx) {
        Integer cur = ctx.ext.getCurrentOrderNo() != null ? ctx.ext.getCurrentOrderNo() : 1;
        return ctx.nodes.stream()
                .filter(n -> n.getOrderNo() != null && n.getOrderNo() > cur)
                .min(Comparator.comparing(ApprovalFlowNode::getOrderNo))
                .orElse(null);
    }

    private Ctx loadCtx(Long instanceId) {
        ApprovalInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        if (!ApprovalService.INST_PENDING.equals(inst.getStatus())) {
            throw new BizException("审批实例非待审状态，无法操作");
        }
        ApprovalInstanceExt ext = extMapper.selectOne(new LambdaQueryWrapper<ApprovalInstanceExt>()
                .eq(ApprovalInstanceExt::getInstanceId, instanceId)
                .last("limit 1"));
        if (ext == null || ext.getFlowId() == null) {
            throw new BizException("审批实例未绑定流程，请使用旧版审批接口");
        }
        List<ApprovalFlowNode> nodes = listNodes(ext.getFlowId());
        ApprovalFlowNode current = nodes.stream()
                .filter(n -> Objects.equals(n.getNodeNo(), ext.getCurrentNodeNo()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            throw new BizException("当前审批节点无效: " + ext.getCurrentNodeNo());
        }
        return new Ctx(inst, ext, nodes, current);
    }

    private void requireCanOperate(Ctx ctx, Long operatorId, String action) {
        if (operatorId == null) {
            throw new BizException(401, "未登录");
        }
        if (!canOperate(ctx, operatorId)) {
            throw new BizException("当前节点无权" + action);
        }
    }

    private boolean canOperate(Ctx ctx, Long userId) {
        if (userId == null) {
            return false;
        }
        Set<String> codes = loadRoleCodes(userId);
        return canOperateCached(ctx.ext, ctx.node, ctx.inst.getApplicantId(), userId, codes, codes.contains(ROLE_ADMIN));
    }

    /** 使用已缓存角色判断权限，避免循环内重复查库 */
    private boolean canOperateCached(ApprovalInstanceExt ext, ApprovalFlowNode node, Long applicantId,
                                     Long userId, Set<String> userRoleCodes, boolean isAdmin) {
        if (userId == null) {
            return false;
        }
        if (isAdmin) {
            return true;
        }
        if (ext.getAssigneeId() != null) {
            return userId.equals(ext.getAssigneeId());
        }
        if (parseExtra(ext.getExtraApproverIds()).contains(userId)) {
            return true;
        }
        if (node.getAssigneeId() != null) {
            return userId.equals(node.getAssigneeId());
        }
        String type = node.getApproverType();
        if (type != null && !type.isBlank()) {
            return switch (type.trim().toUpperCase(Locale.ROOT)) {
                case OrgPositionService.TYPE_USER -> node.getAssigneeId() != null && userId.equals(node.getAssigneeId());
                case OrgPositionService.TYPE_POSITION -> orgPositionService.userHoldsPosition(
                        userId, node.getPositionCode(), applicantDeptId(applicantId), node.getDeptScope());
                case OrgPositionService.TYPE_DEPT_LEADER -> orgPositionService.userIsDeptLeader(
                        userId, applicantDeptId(applicantId), node.getDeptScope());
                case OrgPositionService.TYPE_TEAM_LEADER -> orgPositionService.userIsTeamLeader(
                        userId, applicantDeptId(applicantId));
                default -> hasRoleCode(userRoleCodes, node.getApproverRole());
            };
        }
        if (node.getApproverRole() != null && !node.getApproverRole().isBlank()) {
            return hasRoleCode(userRoleCodes, node.getApproverRole());
        }
        return false;
    }

    private boolean hasRoleCode(Set<String> userRoleCodes, String roleCode) {
        return roleCode != null && !roleCode.isBlank()
                && (userRoleCodes.contains(roleCode) || userRoleCodes.contains(ROLE_ADMIN));
    }

    private Long applicantDeptId(Long applicantId) {
        if (applicantId == null) {
            return null;
        }
        SysUser u = userMapper.selectById(applicantId);
        return u == null ? null : u.getDeptId();
    }

    /** 一次性加载用户全部角色编码 */
    private Set<String> loadRoleCodes(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        List<Long> roleIds = userRoleMapper.selectRoleIds(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void notifyCurrentNode(ApprovalInstance inst, ApprovalFlowNode node, ApprovalInstanceExt ext, String content) {
        List<Long> receivers = resolveApprovers(node, ext, inst.getApplicantId());
        messageService.sendToUsers(receivers, "审批待办", content, MessageService.BIZ_APPROVAL, inst.getId());
    }

    private List<Long> resolveApprovers(ApprovalFlowNode node, ApprovalInstanceExt ext, Long applicantId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (ext.getAssigneeId() != null) {
            ids.add(ext.getAssigneeId());
        } else if (node.getAssigneeId() != null) {
            ids.add(node.getAssigneeId());
        } else {
            String type = node.getApproverType();
            Long deptId = applicantDeptId(applicantId);
            if (type != null && !type.isBlank()) {
                switch (type.trim().toUpperCase(Locale.ROOT)) {
                    case OrgPositionService.TYPE_POSITION ->
                            ids.addAll(orgPositionService.usersByPosition(node.getPositionCode(), deptId, node.getDeptScope()));
                    case OrgPositionService.TYPE_DEPT_LEADER ->
                            ids.addAll(orgPositionService.deptLeaders(deptId, node.getDeptScope()));
                    case OrgPositionService.TYPE_TEAM_LEADER ->
                            ids.addAll(orgPositionService.teamLeaders(deptId));
                    case OrgPositionService.TYPE_USER -> {
                        if (node.getAssigneeId() != null) {
                            ids.add(node.getAssigneeId());
                        }
                    }
                    default -> {
                        if (node.getApproverRole() != null && !node.getApproverRole().isBlank()) {
                            ids.addAll(userIdsByRole(node.getApproverRole()));
                        }
                    }
                }
            } else if (node.getApproverRole() != null && !node.getApproverRole().isBlank()) {
                ids.addAll(userIdsByRole(node.getApproverRole()));
            }
        }
        ids.addAll(parseExtra(ext.getExtraApproverIds()));
        return new ArrayList<>(ids);
    }

    private boolean hasRole(Long userId, String roleCode) {
        List<Long> roleIds = userRoleMapper.selectRoleIds(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .anyMatch(r -> roleCode.equals(r.getRoleCode()) || ROLE_ADMIN.equals(r.getRoleCode()));
    }

    private List<Long> userIdsByRole(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode)
                .last("limit 1"));
        if (role == null) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, role.getId()))
                .stream().map(SysUserRole::getUserId).filter(Objects::nonNull).distinct().toList();
    }

    private Set<Long> parseExtra(String csv) {
        if (csv == null || csv.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<Long> set = new LinkedHashSet<>();
        for (String p : csv.split(",")) {
            if (!p.isBlank()) {
                try {
                    set.add(Long.parseLong(p.trim()));
                } catch (NumberFormatException ignore) {
                    // skip
                }
            }
        }
        return set;
    }

    private void writeRecord(Long instanceId, String nodeName, Long operatorId, String action, String comment) {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(instanceId);
        rec.setNodeName(nodeName);
        rec.setApproverId(operatorId);
        rec.setAction(action);
        rec.setComment(comment);
        rec.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(rec);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String tailComment(String comment) {
        return comment == null || comment.isBlank() ? "" : "：" + comment;
    }

    private record Ctx(ApprovalInstance inst, ApprovalInstanceExt ext,
                       List<ApprovalFlowNode> nodes, ApprovalFlowNode node) {
    }
}
