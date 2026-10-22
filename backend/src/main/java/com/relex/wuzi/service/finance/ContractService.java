package com.relex.wuzi.service.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.ContractPaymentNode;
import com.relex.wuzi.domain.PurchaseContract;
import com.relex.wuzi.domain.PurchaseContractItem;
import com.relex.wuzi.mapper.ContractPaymentNodeMapper;
import com.relex.wuzi.mapper.PurchaseContractItemMapper;
import com.relex.wuzi.mapper.PurchaseContractMapper;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购合同：行税价落库、付款节点、签订时冻结预算、履约执行。
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_ACTIVE = "ACTIVE";
    public static final String ST_CLOSED = "CLOSED";
    public static final String ST_TERMINATED = "TERMINATED";

    private final PurchaseContractMapper contractMapper;
    private final PurchaseContractItemMapper itemMapper;
    private final ContractPaymentNodeMapper nodeMapper;
    private final BudgetService budgetService;
    private final PriceTaxCalculator priceTaxCalculator;

    @Data
    public static class ContractLine {
        private Long materialId;
        private String materialName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal price;
        private BigDecimal taxRate;
        private String remark;
    }

    @Data
    public static class PayNode {
        private String nodeName;
        private LocalDate planDate;
        private BigDecimal planAmount;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private String title;
        private String contractType;
        private Long supplierId;
        private LocalDate signDate;
        private LocalDate startDate;
        private LocalDate endDate;
        private Long budgetId;
        private Long deptId;
        private Long ownerId;
        private String remark;
        private List<ContractLine> items;
        private List<PayNode> payNodes;
    }

    public PageResult<PurchaseContract> page(long page, long size, String status, Long supplierId, String keyword) {
        Page<PurchaseContract> p = contractMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PurchaseContract>()
                        .eq(status != null && !status.isBlank(), PurchaseContract::getStatus, status)
                        .eq(supplierId != null, PurchaseContract::getSupplierId, supplierId)
                        .and(keyword != null && !keyword.isBlank(),
                                w -> w.like(PurchaseContract::getContractNo, keyword)
                                        .or().like(PurchaseContract::getTitle, keyword))
                        .orderByDesc(PurchaseContract::getId));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public List<PurchaseContractItem> items(Long contractId) {
        return itemMapper.selectList(new LambdaQueryWrapper<PurchaseContractItem>()
                .eq(PurchaseContractItem::getContractId, contractId));
    }

    public List<ContractPaymentNode> payNodes(Long contractId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ContractPaymentNode>()
                .eq(ContractPaymentNode::getContractId, contractId)
                .orderByAsc(ContractPaymentNode::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseContract create(CreateReq req, Long userId) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BizException("合同标题不能为空");
        }
        if (req.getSupplierId() == null) {
            throw new BizException("供应商不能为空");
        }
        PurchaseContract c = new PurchaseContract();
        c.setContractNo(BillNoUtil.next("HT-"));
        c.setTitle(req.getTitle().trim());
        c.setContractType(req.getContractType() == null || req.getContractType().isBlank()
                ? "ORDINARY" : req.getContractType());
        c.setSupplierId(req.getSupplierId());
        c.setSignDate(req.getSignDate());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setBudgetId(req.getBudgetId());
        c.setDeptId(req.getDeptId());
        c.setOwnerId(req.getOwnerId());
        c.setRemark(req.getRemark());
        c.setStatus(ST_DRAFT);
        c.setPaidAmount(BigDecimal.ZERO);
        c.setIsDeleted(0);
        c.setCreatedBy(userId);
        c.setCreatedAt(LocalDateTime.now());
        contractMapper.insert(c);
        replaceItems(c, req.getItems());
        replaceNodes(c, req.getPayNodes());
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CreateReq req, Long userId) {
        PurchaseContract c = require(id);
        if (!ST_DRAFT.equals(c.getStatus())) {
            throw new BizException("仅草稿可编辑");
        }
        if (req.getTitle() != null) c.setTitle(req.getTitle().trim());
        if (req.getContractType() != null) c.setContractType(req.getContractType());
        if (req.getSupplierId() != null) c.setSupplierId(req.getSupplierId());
        c.setSignDate(req.getSignDate());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setBudgetId(req.getBudgetId());
        c.setDeptId(req.getDeptId());
        c.setOwnerId(req.getOwnerId());
        c.setRemark(req.getRemark());
        c.setUpdatedBy(userId);
        c.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(c);
        if (req.getItems() != null) replaceItems(c, req.getItems());
        if (req.getPayNodes() != null) replaceNodes(c, req.getPayNodes());
    }

    private void replaceItems(PurchaseContract c, List<ContractLine> lines) {
        itemMapper.delete(new LambdaQueryWrapper<PurchaseContractItem>()
                .eq(PurchaseContractItem::getContractId, c.getId()));
        BigDecimal taxIn = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        if (lines != null) {
            for (ContractLine line : lines) {
                BigDecimal qty = nvl(line.getQty());
                BigDecimal price = nvl(line.getPrice());
                BigDecimal rate = nvl(line.getTaxRate());
                if (qty.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("合同行数量/单价必须大于 0");
                }
                PriceTaxCalculator.Line r = priceTaxCalculator.fromInclusive(price, rate, qty);
                PurchaseContractItem item = new PurchaseContractItem();
                item.setContractId(c.getId());
                item.setMaterialId(line.getMaterialId());
                item.setMaterialName(line.getMaterialName());
                item.setSpec(line.getSpec());
                item.setUnit(line.getUnit());
                item.setQty(qty);
                item.setPrice(price);
                item.setTaxRate(rate);
                item.setTaxExclusiveAmount(r.taxExclAmount);
                item.setTaxInclusiveAmount(r.taxInclusiveAmount);
                item.setTaxAmount(r.taxAmount);
                item.setRemark(line.getRemark());
                itemMapper.insert(item);
                taxIn = taxIn.add(r.taxInclusiveAmount);
                tax = tax.add(r.taxAmount);
            }
        }
        c.setTaxInclusiveAmount(taxIn.setScale(2, RoundingMode.HALF_UP));
        c.setTaxAmount(tax.setScale(2, RoundingMode.HALF_UP));
        c.setContractAmount(c.getTaxInclusiveAmount());
        contractMapper.updateById(c);
    }

    private void replaceNodes(PurchaseContract c, List<PayNode> nodes) {
        nodeMapper.delete(new LambdaQueryWrapper<ContractPaymentNode>()
                .eq(ContractPaymentNode::getContractId, c.getId()));
        if (nodes != null) {
            for (PayNode n : nodes) {
                ContractPaymentNode node = new ContractPaymentNode();
                node.setContractId(c.getId());
                node.setNodeName(n.getNodeName());
                node.setPlanDate(n.getPlanDate());
                node.setPlanAmount(nvl(n.getPlanAmount()).setScale(2, RoundingMode.HALF_UP));
                node.setPaidAmount(BigDecimal.ZERO);
                node.setStatus("PENDING");
                node.setRemark(n.getRemark());
                nodeMapper.insert(node);
            }
        }
    }

    /** 提交审批 */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseContract submit(Long id, Long userId) {
        PurchaseContract c = require(id);
        if (!ST_DRAFT.equals(c.getStatus())) {
            throw new BizException("当前状态不可提交");
        }
        if (items(id).isEmpty()) {
            throw new BizException("合同明细不能为空");
        }
        c.setStatus(ST_PENDING);
        c.setUpdatedBy(userId);
        c.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(c);
        return c;
    }

    /** 生效：冻结预算 */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseContract activate(Long id, Long userId) {
        PurchaseContract c = require(id);
        if (!ST_PENDING.equals(c.getStatus()) && !ST_DRAFT.equals(c.getStatus())) {
            throw new BizException("当前状态不可生效");
        }
        if (c.getBudgetId() != null) {
            budgetService.freeze(c.getBudgetId(), nvl(c.getContractAmount()),
                    "CONTRACT", c.getId(), userId, "合同生效冻结 " + c.getContractNo());
        }
        c.setStatus(ST_ACTIVE);
        c.setUpdatedBy(userId);
        c.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(c);
        return c;
    }

    /** 关闭 / 终止：释放剩余冻结（按未付金额） */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseContract close(Long id, String status, Long userId, String remark) {
        PurchaseContract c = require(id);
        if (!ST_ACTIVE.equals(c.getStatus())) {
            throw new BizException("仅执行中合同可关闭/终止");
        }
        String target = ST_TERMINATED.equals(status) ? ST_TERMINATED : ST_CLOSED;
        if (c.getBudgetId() != null) {
            BigDecimal unpaid = nvl(c.getContractAmount()).subtract(nvl(c.getPaidAmount()));
            if (unpaid.compareTo(BigDecimal.ZERO) > 0) {
                budgetService.release(c.getBudgetId(), unpaid, "CONTRACT", c.getId(), userId,
                        "合同" + (ST_TERMINATED.equals(target) ? "终止" : "关闭") + "释放 " + c.getContractNo());
            }
        }
        c.setStatus(target);
        if (remark != null) c.setRemark(remark);
        c.setUpdatedBy(userId);
        c.setUpdatedAt(LocalDateTime.now());
        contractMapper.updateById(c);
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public void addPaidAmount(Long contractId, BigDecimal delta, Long userId) {
        PurchaseContract c = require(contractId);
        contractMapper.update(null, new LambdaUpdateWrapper<PurchaseContract>()
                .eq(PurchaseContract::getId, contractId)
                .set(PurchaseContract::getPaidAmount, nvl(c.getPaidAmount()).add(nvl(delta)).setScale(2, RoundingMode.HALF_UP))
                .set(PurchaseContract::getUpdatedAt, LocalDateTime.now()));
    }

    public PurchaseContract require(Long id) {
        PurchaseContract c = contractMapper.selectById(id);
        if (c == null || (c.getIsDeleted() != null && c.getIsDeleted() == 1)) {
            throw new BizException("合同不存在");
        }
        return c;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
