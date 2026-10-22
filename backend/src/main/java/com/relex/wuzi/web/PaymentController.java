package com.relex.wuzi.web;

import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PaymentOrder;
import com.relex.wuzi.service.finance.PaymentService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 付款单 / 应付账款。
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "created_by")
    public R<PageResult<PaymentOrder>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long supplierId,
                                            @RequestParam(required = false) Long contractId) {
        return R.ok(paymentService.page(page, size, status, supplierId, contractId));
    }

    @GetMapping("/{id}")
    public R<PaymentOrder> detail(@PathVariable Long id) {
        return R.ok(paymentService.require(id));
    }

    @PostMapping
    @RequirePerm("fin:payment:create")
    @OpLog(module = "业务", action = "新建付款单")
    public R<PaymentOrder> create(@RequestBody PaymentService.CreateReq req,
                                  @RequestAttribute("userId") Long userId) {
        return R.ok(paymentService.create(req, userId));
    }

    @PostMapping("/{id}/submit")
    @RequirePerm("fin:payment:submit")
    @OpLog(module = "业务", action = "提交付款")
    public R<PaymentOrder> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(paymentService.submit(id, userId));
    }

    @PostMapping("/{id}/confirm")
    @RequirePerm("fin:payment:confirm")
    @OpLog(module = "业务", action = "确认付款")
    public R<PaymentOrder> confirm(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(paymentService.confirmPaid(id, userId));
    }

    @PostMapping("/{id}/reject")
    @RequirePerm("fin:payment:reject")
    @OpLog(module = "业务", action = "驳回付款")
    public R<PaymentOrder> reject(@PathVariable Long id,
                                  @RequestParam(required = false) String reason,
                                  @RequestAttribute("userId") Long userId) {
        return R.ok(paymentService.reject(id, reason, userId));
    }

    @PostMapping("/{id}/cancel")
    @RequirePerm("fin:payment:cancel")
    @OpLog(module = "业务", action = "作废付款")
    public R<PaymentOrder> cancel(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(paymentService.cancel(id, userId));
    }

    @GetMapping("/ap-aging")
    public R<List<Map<String, Object>>> apAging() {
        return R.ok(paymentService.apAging());
    }
}
