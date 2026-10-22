package com.relex.wuzi.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.BorrowOrder;
import com.relex.wuzi.domain.AlertRecord;
import com.relex.wuzi.domain.ContractPaymentNode;
import com.relex.wuzi.mapper.AlertRecordMapper;
import com.relex.wuzi.mapper.BorrowOrderMapper;
import com.relex.wuzi.mapper.ContractPaymentNodeMapper;
import com.relex.wuzi.service.MessageService;
import com.relex.wuzi.service.SupplierEvalService;
import com.relex.wuzi.service.notify.NotifyChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扩展定时任务：借用逾期、合同付款节点提醒、供应商评价重算。
 * 均带 JobLock，避免多实例重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpsJobs {

    public static final String JOB_BORROW_OVERDUE = "borrow-overdue";
    public static final String JOB_PAY_NODE = "contract-pay-node";
    public static final String JOB_SUPPLIER_EVAL = "supplier-eval-recalc";

    private final JobLockService jobLockService;
    private final BorrowOrderMapper borrowMapper;
    private final ContractPaymentNodeMapper nodeMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final SupplierEvalService supplierEvalService;
    private final MessageService messageService;
    private final NotifyChannelService notifyChannelService;

    /** 每日 08:10 借用逾期扫描 */
    @Scheduled(cron = "0 10 8 * * ?")
    public void scanBorrowOverdue() {
        if (!jobLockService.tryLock(JOB_BORROW_OVERDUE, 30)) {
            return;
        }
        try {
            List<BorrowOrder> list = borrowMapper.selectList(new LambdaQueryWrapper<BorrowOrder>()
                    .eq(BorrowOrder::getStatus, "LENT"));
            int n = 0;
            LocalDate today = LocalDate.now();
            for (BorrowOrder b : list) {
                if (b.getExpectReturnDate() != null && b.getExpectReturnDate().isBefore(today)) {
                    if (!"OVERDUE".equals(b.getStatus())) {
                        b.setStatus("OVERDUE");
                        b.setUpdatedAt(LocalDateTime.now());
                        borrowMapper.updateById(b);
                    }
                    AlertRecord rec = new AlertRecord();
                    rec.setAlertType("BORROW_OVERDUE");
                    rec.setAlertLevel("WARN");
                    rec.setBizNo(b.getBorrowNo());
                    rec.setTitle("借用逾期：" + (b.getBorrowNo() == null ? b.getId() : b.getBorrowNo()));
                    rec.setContent("应还日期 " + b.getExpectReturnDate() + "，请及时催还");
                    rec.setStatus("OPEN");
                    rec.setIsDeleted(0);
                    rec.setCreatedAt(LocalDateTime.now());
                    alertRecordMapper.insert(rec);
                    if (b.getCreatedBy() != null) {
                        messageService.send(b.getCreatedBy(), "借用逾期提醒",
                                "单号 " + b.getBorrowNo() + " 已逾期，请尽快归还", "ALERT", b.getId());
                        notifyChannelService.push(b.getCreatedBy(), "借用逾期提醒", b.getBorrowNo(), null);
                    }
                    n++;
                }
            }
            log.info("BorrowOverdueJob done: overdue={}", n);
        } finally {
            jobLockService.release(JOB_BORROW_OVERDUE);
        }
    }

    /** 每日 08:20 合同付款节点到期提醒 */
    @Scheduled(cron = "0 20 8 * * ?")
    public void remindContractPayNodes() {
        if (!jobLockService.tryLock(JOB_PAY_NODE, 30)) {
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            LocalDate soon = today.plusDays(7);
            List<ContractPaymentNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ContractPaymentNode>()
                    .eq(ContractPaymentNode::getStatus, "PENDING")
                    .isNotNull(ContractPaymentNode::getPlanDate)
                    .le(ContractPaymentNode::getPlanDate, soon));
            int n = 0;
            for (ContractPaymentNode node : nodes) {
                messageService.sendToUsers(List.of(1L), "合同付款节点提醒",
                        "节点「" + node.getNodeName() + "」计划日 " + node.getPlanDate()
                                + "，计划金额 " + node.getPlanAmount(), "CONTRACT", node.getContractId());
                n++;
            }
            log.info("ContractPayNodeJob done: remind={}", n);
        } finally {
            jobLockService.release(JOB_PAY_NODE);
        }
    }

    /** 每周日 03:00 供应商评价重算（有数据才跑） */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void recalcSupplierEval() {
        if (!jobLockService.tryLock(JOB_SUPPLIER_EVAL, 120)) {
            return;
        }
        try {
            supplierEvalService.recalcAllSuppliers();
            log.info("SupplierEvalRecalcJob done");
        } catch (Exception e) {
            log.warn("SupplierEvalRecalcJob skip: {}", e.getMessage());
        } finally {
            jobLockService.release(JOB_SUPPLIER_EVAL);
        }
    }
}
