package com.relex.wuzi.web;

import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.ContractPaymentNode;
import com.relex.wuzi.domain.PurchaseContract;
import com.relex.wuzi.domain.PurchaseContractItem;
import com.relex.wuzi.service.finance.ContractService;
import com.relex.wuzi.web.annotation.OpLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购合同管理。
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "created_by")
    public R<PageResult<PurchaseContract>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long supplierId,
                                                @RequestParam(required = false) String keyword) {
        return R.ok(contractService.page(page, size, status, supplierId, keyword));
    }

    @GetMapping("/{id}")
    public R<PurchaseContract> detail(@PathVariable Long id) {
        return R.ok(contractService.require(id));
    }

    @PostMapping
    @RequirePerm("po:contract:create")
    @OpLog(module = "业务", action = "新建合同")
    public R<PurchaseContract> create(@RequestBody ContractService.CreateReq req,
                                      @RequestAttribute("userId") Long userId) {
        return R.ok(contractService.create(req, userId));
    }

    @PutMapping("/{id}")
    @RequirePerm("po:contract:update")
    @OpLog(module = "业务", action = "编辑合同")
    public R<Void> update(@PathVariable Long id,
                          @RequestBody ContractService.CreateReq req,
                          @RequestAttribute("userId") Long userId) {
        contractService.update(id, req, userId);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @RequirePerm("po:contract:submit")
    @OpLog(module = "业务", action = "提交合同")
    public R<PurchaseContract> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(contractService.submit(id, userId));
    }

    @PostMapping("/{id}/activate")
    @RequirePerm("po:contract:activate")
    @OpLog(module = "业务", action = "合同生效")
    public R<PurchaseContract> activate(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return R.ok(contractService.activate(id, userId));
    }

    @PostMapping("/{id}/close")
    @RequirePerm("po:contract:close")
    @OpLog(module = "业务", action = "关闭合同")
    public R<PurchaseContract> close(@PathVariable Long id,
                                     @RequestParam(defaultValue = "CLOSED") String status,
                                     @RequestParam(required = false) String remark,
                                     @RequestAttribute("userId") Long userId) {
        return R.ok(contractService.close(id, status, userId, remark));
    }

    @GetMapping("/{id}/items")
    public R<List<PurchaseContractItem>> items(@PathVariable Long id) {
        return R.ok(contractService.items(id));
    }

    @GetMapping("/{id}/pay-nodes")
    public R<List<ContractPaymentNode>> payNodes(@PathVariable Long id) {
        return R.ok(contractService.payNodes(id));
    }
}
