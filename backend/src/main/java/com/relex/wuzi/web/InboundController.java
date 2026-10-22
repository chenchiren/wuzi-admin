package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.InboundOrder;
import com.relex.wuzi.service.InboundService;
import com.relex.wuzi.web.dto.InboundCreateCmd;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采购入库 HTTP 入口：仅收参、声明权限、包装响应。
 * 业务编排见 {@link InboundService}。
 */
@RestController
@RequestMapping("/api/inbound/orders")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    @GetMapping
    public R<PageResult<InboundOrder>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long warehouseId) {
        return R.ok(inboundService.page(page, size, status, warehouseId));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(inboundService.detail(id));
    }

    @RequirePerm("wh:inbound:cancel")
    @PostMapping("/{id}/cancel")
    public R<InboundOrder> cancel(@PathVariable Long id) {
        return R.ok(inboundService.cancel(id));
    }

    @RequirePerm("wh:inbound:create")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody InboundCreateCmd req,
                                         @RequestAttribute("userId") Long userId) {
        return R.ok(inboundService.create(req, userId));
    }

    @RequirePerm("wh:inbound:confirm")
    @PostMapping("/{id}/confirm")
    public R<Map<String, Object>> confirm(@PathVariable Long id,
                                          @RequestAttribute("userId") Long userId) {
        return R.ok(inboundService.confirm(id, userId));
    }
}
