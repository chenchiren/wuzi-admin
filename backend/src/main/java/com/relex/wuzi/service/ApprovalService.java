package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.ApprovalInstance;
import com.relex.wuzi.domain.ApprovalRecord;
import com.relex.wuzi.mapper.ApprovalInstanceMapper;
import com.relex.wuzi.mapper.ApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 通用审批服务：创建审批实例、通过/驳回、回写业务单状态。
 * 业务单状态机：DRAFT / PENDING_APPROVAL / APPROVED / REJECTED / COMPLETED / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_PENDING = "PENDING_APPROVAL";
    public static final String ST_APPROVED = "APPROVED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_COMPLETED = "COMPLETED";
    public static final String ST_CANCELLED = "CANCELLED";

    public static final String INST_PENDING = "PENDING";
    public static final String INST_APPROVED = "APPROVED";
    public static final String INST_REJECTED = "REJECTED";

    public static final String BIZ_APPLY = "PURCHASE_APPLY";
    public static final String BIZ_RETURN = "PURCHASE_RETURN";
    public static final String BIZ_INQUIRY = "PURCHASE_INQUIRY";
    public static final String BIZ_ORDER = "PURCHASE_ORDER";
    public static final String BIZ_MATERIAL_USE = "MATERIAL_USE";
    public static final String BIZ_BORROW = "BORROW";
    public static final String BIZ_SCRAP = "SCRAP";
    public static final String BIZ_TRANSFER = "TRANSFER";
    public static final String BIZ_RETURN_STORE = "RETURN_STORE";
    public static final String BIZ_INVENTORY = "INVENTORY_CHECK";
    public static final String BIZ_ARRIVAL = "PURCHASE_ARRIVAL";
    public static final String BIZ_OUTBOUND = "OUTBOUND";

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;
    private final MessageService messageService;

    /** 创建审批实例，并将业务单置为 PENDING_APPROVAL */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstance createInstance(String bizType, Long bizId, String bizNo, Long applicantId, String title,
                                           BusinessStatusUpdater updater) {
        ApprovalInstance inst = new ApprovalInstance();
        inst.setBizType(bizType);
        inst.setBizId(bizId);
        inst.setBizNo(bizNo);
        inst.setApplicantId(applicantId);
        inst.setTitle(title);
        inst.setStatus(INST_PENDING);
        instanceMapper.insert(inst);
        if (updater != null) {
            updater.updateStatus(ST_PENDING);
        }
        try {
            messageService.send(applicantId, "审批已提交", title + " 已进入审批", bizType, bizId);
        } catch (Exception ignore) {
        }
        return inst;
    }

    public ApprovalInstance getByBiz(String bizType, Long bizId) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getBizType, bizType)
                .eq(ApprovalInstance::getBizId, bizId)
                .orderByDesc(ApprovalInstance::getId)
                .last("limit 1"));
    }

    public List<ApprovalRecord> listRecords(Long instanceId) {
        return recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, instanceId)
                .orderByAsc(ApprovalRecord::getId));
    }

    /** 审批通过 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long instanceId, Long approverId, String comment, BusinessStatusUpdater updater) {
        ApprovalInstance inst = requirePending(instanceId);
        inst.setStatus(INST_APPROVED);
        instanceMapper.updateById(inst);
        writeRecord(instanceId, approverId, "APPROVE", comment);
        try {
            if (inst.getApplicantId() != null) {
                messageService.send(inst.getApplicantId(), "审批通过",
                        (inst.getTitle() == null ? "" : inst.getTitle()) + " 已通过", inst.getBizType(), inst.getBizId());
            }
        } catch (Exception ignore) {
        }
        if (updater != null) {
            updater.updateStatus(ST_APPROVED);
        }
    }

    /** 审批驳回 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long instanceId, Long approverId, String comment, BusinessStatusUpdater updater) {
        ApprovalInstance inst = requirePending(instanceId);
        inst.setStatus(INST_REJECTED);
        instanceMapper.updateById(inst);
        writeRecord(instanceId, approverId, "REJECT", comment);
        try {
            if (inst.getApplicantId() != null) {
                messageService.send(inst.getApplicantId(), "审批驳回",
                        (inst.getTitle() == null ? "" : inst.getTitle()) + " 被驳回", inst.getBizType(), inst.getBizId());
            }
        } catch (Exception ignore) {
        }
        if (updater != null) {
            updater.updateStatus(ST_REJECTED);
        }
    }

    /** 直接更新业务单状态（不经过审批流，如 COMPLETED / CANCELLED） */
    public void markBizStatus(String bizType, Long bizId, String status, BusinessStatusUpdater updater) {
        if (updater != null) {
            updater.updateStatus(status);
        }
    }

    private ApprovalInstance requirePending(Long instanceId) {
        ApprovalInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException("审批实例不存在");
        }
        if (!INST_PENDING.equals(inst.getStatus())) {
            throw new BizException("审批实例非待审状态，无法操作");
        }
        return inst;
    }

    private void writeRecord(Long instanceId, Long approverId, String action, String comment) {
        ApprovalRecord rec = new ApprovalRecord();
        rec.setInstanceId(instanceId);
        rec.setNodeName("DEFAULT");
        rec.setApproverId(approverId);
        rec.setAction(action);
        rec.setComment(comment);
        recordMapper.insert(rec);
    }

    @FunctionalInterface
    public interface BusinessStatusUpdater {
        void updateStatus(String status);
    }
}
