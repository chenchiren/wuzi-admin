package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Budget;
import com.relex.wuzi.domain.BudgetControlLog;
import com.relex.wuzi.mapper.BudgetMapper;
import com.relex.wuzi.service.finance.BudgetService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算与费用控制。
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetMapper budgetMapper;
    private final BudgetService budgetService;

    @Data
    public static class CreateReq {
        private Integer budgetYear;
        private Long deptId;
        private String projectCode;
        private String category;
        private BigDecimal budgetAmount;
        private String remark;
    }

    @GetMapping
    public R<PageResult<Budget>> page(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) Integer budgetYear,
                                      @RequestParam(required = false) Long deptId,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String status) {
        Page<Budget> p = budgetMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Budget>()
                        .eq(budgetYear != null, Budget::getBudgetYear, budgetYear)
                        .eq(deptId != null, Budget::getDeptId, deptId)
                        .eq(category != null && !category.isBlank(), Budget::getCategory, category)
                        .eq(status != null && !status.isBlank(), Budget::getStatus, status)
                        .orderByDesc(Budget::getId));
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Budget b = require(id);
        Map<String, Object> map = new HashMap<>();
        map.put("budget", b);
        map.put("available", budgetService.available(b));
        map.put("logs", budgetService.logs(id));
        return R.ok(map);
    }

    @PostMapping
    @RequirePerm("fin:budget:create")
    @OpLog(module = "业务", action = "新建预算")
    public R<Budget> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getBudgetYear() == null) {
            throw new BizException("预算年度不能为空");
        }
        if (req.getBudgetAmount() == null || req.getBudgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("预算金额必须大于 0");
        }
        Budget b = new Budget();
        b.setBudgetNo(BillNoUtil.next("YS-"));
        b.setBudgetYear(req.getBudgetYear());
        b.setDeptId(req.getDeptId());
        b.setProjectCode(req.getProjectCode());
        b.setCategory(req.getCategory() == null || req.getCategory().isBlank() ? "PURCHASE" : req.getCategory());
        b.setBudgetAmount(req.getBudgetAmount());
        b.setUsedAmount(BigDecimal.ZERO);
        b.setFrozenAmount(BigDecimal.ZERO);
        b.setStatus("ACTIVE");
        b.setRemark(req.getRemark());
        b.setIsDeleted(0);
        b.setCreatedBy(userId);
        b.setCreatedAt(LocalDateTime.now());
        budgetMapper.insert(b);
        return R.ok(b);
    }

    @PutMapping("/{id}")
    @RequirePerm("fin:budget:update")
    @OpLog(module = "业务", action = "调整预算")
    public R<Budget> update(@PathVariable Long id, @RequestBody CreateReq req,
                            @RequestAttribute("userId") Long userId) {
        Budget b = require(id);
        if (req.getBudgetAmount() != null) {
            BigDecimal min = (b.getUsedAmount() == null ? BigDecimal.ZERO : b.getUsedAmount())
                    .add(b.getFrozenAmount() == null ? BigDecimal.ZERO : b.getFrozenAmount());
            if (req.getBudgetAmount().compareTo(min) < 0) {
                throw new BizException("预算不能低于已用+冻结");
            }
            b.setBudgetAmount(req.getBudgetAmount());
        }
        if (req.getRemark() != null) {
            b.setRemark(req.getRemark());
        }
        b.setUpdatedBy(userId);
        b.setUpdatedAt(LocalDateTime.now());
        budgetMapper.updateById(b);
        return R.ok(b);
    }

    @PostMapping("/{id}/toggle")
    @RequirePerm("fin:budget:toggle")
    @OpLog(module = "业务", action = "启停预算")
    public R<Budget> toggle(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        Budget b = require(id);
        b.setStatus("ACTIVE".equals(b.getStatus()) ? "FROZEN" : "ACTIVE");
        b.setUpdatedBy(userId);
        b.setUpdatedAt(LocalDateTime.now());
        budgetMapper.updateById(b);
        return R.ok(b);
    }

    @GetMapping("/{id}/logs")
    public R<List<BudgetControlLog>> logs(@PathVariable Long id) {
        return R.ok(budgetService.logs(id));
    }

    private Budget require(Long id) {
        Budget b = budgetMapper.selectById(id);
        if (b == null || (b.getIsDeleted() != null && b.getIsDeleted() == 1)) {
            throw new BizException("预算不存在");
        }
        return b;
    }
}
