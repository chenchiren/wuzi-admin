package com.relex.wuzi.web;

import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PurchaseApply;
import com.relex.wuzi.domain.PurchasePlan;
import com.relex.wuzi.domain.PurchasePlanItem;
import com.relex.wuzi.service.purchase.PurchasePlanService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购计划 / 需求汇总。
 */
@RestController
@RequestMapping("/api/purchase-plans")
@RequiredArgsConstructor
public class PurchasePlanController {

    private final PurchasePlanService planService;

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "created_by")
    public R<PageResult<PurchasePlan>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Integer planYear,
                                            @RequestParam(required = false) Long deptId) {
        return R.ok(planService.page(page, size, status, planYear, deptId));
    }

    @GetMapping("/{id}")
    public R<PurchasePlan> detail(@PathVariable Long id) {
        return R.ok(planService.get(id));
    }

    @PostMapping
    @RequirePerm("po:plan:create")
    @OpLog(module = "业务", action = "新建采购计划")
    public R<PurchasePlan> create(@RequestBody PurchasePlanService.CreateReq req,
                                  @RequestAttribute("userId") Long userId) {
        return R.ok(planService.create(req, userId));
    }

    @PutMapping("/{id}")
    @RequirePerm("po:plan:update")
    @OpLog(module = "业务", action = "编辑采购计划")
    public R<Void> update(@PathVariable Long id,
                          @RequestBody PurchasePlanService.CreateReq req,
                          @RequestAttribute("userId") Long userId) {
        planService.update(id, req, userId);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequirePerm("po:plan:submit")
    @OpLog(module = "业务", action = "提交采购计划")
    public R<PurchasePlan> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(planService.submit(id, userId));
    }

    @PostMapping("/{id}/approve")
    @RequirePerm("po:plan:approve")
    @OpLog(module = "业务", action = "审核采购计划")
    public R<PurchasePlan> approve(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(planService.approve(id, userId));
    }

    @PostMapping("/{id}/reject")
    @RequirePerm("po:plan:reject")
    @OpLog(module = "业务", action = "驳回采购计划")
    public R<PurchasePlan> reject(@PathVariable Long id,
                                  @RequestParam(required = false) String reason,
                                  @RequestAttribute("userId") Long userId) {
        return R.ok(planService.reject(id, reason, userId));
    }

    @PostMapping("/{id}/cancel")
    @RequirePerm("po:plan:cancel")
    @OpLog(module = "业务", action = "作废采购计划")
    public R<PurchasePlan> cancel(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(planService.cancel(id, userId));
    }

    @PostMapping("/items/{itemId}/convert")
    @RequirePerm("po:plan:convert")
    @OpLog(module = "业务", action = "计划行转申请")
    public R<Void> convert(@PathVariable Long itemId,
                           @RequestParam Long applyId,
                           @RequestAttribute("userId") Long userId) {
        planService.markItemConverted(itemId, applyId, userId);
        return R.ok();
    }

    /** 计划行批量转采购申请：创建草稿申请并回写计划行关联 */
    @PostMapping("/{id}/to-apply")
    @RequirePerm("po:plan:convert")
    @OpLog(module = "业务", action = "计划转采购申请")
    public R<PurchaseApply> toApply(@PathVariable Long id,
                                    @RequestBody(required = false) PurchasePlanService.ToApplyReq req,
                                    @RequestAttribute("userId") Long userId) {
        return R.ok(planService.toApply(id, req == null ? new PurchasePlanService.ToApplyReq() : req, userId));
    }

    @GetMapping("/{id}/items")
    public R<List<PurchasePlanItem>> items(@PathVariable Long id) {
        return R.ok(planService.items(id));
    }
}
