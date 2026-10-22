package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.InboundOrder;
import com.relex.wuzi.domain.OutboundOrder;
import com.relex.wuzi.domain.PurchaseReturn;
import com.relex.wuzi.service.InboundOutboundRedFlushService;
import com.relex.wuzi.service.ReturnRedFlushService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 红冲闭环入口：
 * - 采购退货确认红冲（库存 redFlush + stock_record amount_sign=-1）
 * - 入库红冲 / 出库红冲（含单据状态补全 RED_FLUSHED）
 */
@RestController
@RequestMapping("/api/red-flush")
@RequiredArgsConstructor
public class RedFlushController {

    private final ReturnRedFlushService returnRedFlushService;
    private final InboundOutboundRedFlushService inboundOutboundRedFlushService;

    /** 采购退货：确认时红冲库存 */
    @RequirePerm("po:return:red-flush")
    @PostMapping("/purchase-return/{id}")
    public R<PurchaseReturn> purchaseReturn(@PathVariable Long id,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestAttribute("userId") Long userId) {
        return R.ok(returnRedFlushService.redFlushOnConfirm(id, warehouseId, userId));
    }

    /** 入库红冲 */
    @RequirePerm("wh:inbound:red-flush")
    @PostMapping("/inbound/{id}")
    public R<InboundOrder> inbound(@PathVariable Long id,
                                   @RequestAttribute("userId") Long userId) {
        return R.ok(inboundOutboundRedFlushService.redFlushInbound(id, userId));
    }

    /** 出库红冲（已有逻辑则补单据状态） */
    @RequirePerm("wh:outbound:red-flush")
    @PostMapping("/outbound/{id}")
    public R<OutboundOrder> outbound(@PathVariable Long id,
                                     @RequestAttribute("userId") Long userId) {
        return R.ok(inboundOutboundRedFlushService.redFlushOutbound(id, userId));
    }

    /** 仅补出库红冲单据状态 */
    @RequirePerm("wh:outbound:update")
    @PostMapping("/outbound/{id}/status")
    public R<OutboundOrder> outboundStatus(@PathVariable Long id) {
        return R.ok(inboundOutboundRedFlushService.completeOutboundRedFlushStatus(id));
    }

    @GetMapping("/ping")
    public R<Map<String, String>> ping() {
        return R.ok(Map.of("module", "red-flush"));
    }
}
