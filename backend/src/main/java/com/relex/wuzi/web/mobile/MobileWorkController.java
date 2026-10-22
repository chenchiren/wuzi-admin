package com.relex.wuzi.web.mobile;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PurchaseArrival;
import com.relex.wuzi.service.MobileWorkService;
import com.relex.wuzi.web.mobile.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 手机端简化作业：扫码入库/出库、到货提交、领用与审批、库存与脱敏溯源。
 * 收参与权限声明留在本层，用例编排见 {@link MobileWorkService}。
 */
@RestController
@RequestMapping("/api/m")
@RequiredArgsConstructor
public class MobileWorkController {

    private final MobileWorkService mobileWorkService;

    // ---------- 入库 ----------

    @RequirePerm("wh:inbound:scan")
    @PostMapping("/inbounds/{id}/scan")
    public R<Map<String, Object>> inboundScan(@PathVariable Long id,
                                              @RequestBody WorkScanReq req,
                                              @RequestAttribute(value = "userId", required = false) Long userId,
                                              @RequestAttribute(value = "username", required = false) String username,
                                              @RequestAttribute(value = "roles", required = false) List<String> roles,
                                              @RequestParam(required = false) Boolean hidePrice,
                                              jakarta.servlet.http.HttpServletRequest http) {
        return R.ok(mobileWorkService.inboundScan(id, req, userId, username, roles, hidePrice, clientIp(http)));
    }

    /**
     * 扫码反查待办：命中待收货入库单 / 借用中单据，避免多单平铺。
     */
    @RequirePerm("wh:inbound:query")
    @PostMapping("/scan/lookup-work")
    public R<Map<String, Object>> lookupWork(@RequestBody WorkScanReq req,
                                             @RequestAttribute(value = "userId", required = false) Long userId,
                                             @RequestAttribute(value = "username", required = false) String username,
                                             jakarta.servlet.http.HttpServletRequest http) {
        return R.ok(mobileWorkService.lookupWork(req, userId, username, clientIp(http)));
    }

    /**
     * 扫码归还（件管/借出物资）：扫码即身份，不依赖借单号。
     */
    @RequirePerm("asset:borrow:return")
    @PostMapping("/borrows/scan-return")
    public R<Map<String, Object>> borrowScanReturn(@RequestBody ScanReturnReq req,
                                                   @RequestAttribute(value = "userId", required = false) Long userId,
                                                   @RequestAttribute(value = "username", required = false) String username,
                                                   jakarta.servlet.http.HttpServletRequest http) {
        return R.ok(mobileWorkService.borrowScanReturn(req, userId, username, clientIp(http)));
    }

    @RequirePerm("wh:inbound:confirm")
    @PostMapping("/inbounds/{id}/confirm")
    public R<Map<String, Object>> inboundConfirm(@PathVariable Long id,
                                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.inboundConfirm(id, userId));
    }

    @GetMapping("/inbounds/{id}")
    public R<Map<String, Object>> inboundDetail(@PathVariable Long id) {
        return R.ok(mobileWorkService.inboundDetail(id));
    }

    // ---------- 出库 ----------

    @RequirePerm("wh:outbound:scan")
    @PostMapping("/outbounds/{id}/scan")
    public R<Map<String, Object>> outboundScan(@PathVariable Long id,
                                               @RequestBody WorkScanReq req,
                                               @RequestAttribute(value = "userId", required = false) Long userId,
                                               @RequestAttribute(value = "username", required = false) String username,
                                               @RequestAttribute(value = "roles", required = false) List<String> roles,
                                               @RequestParam(required = false) Boolean hidePrice,
                                               jakarta.servlet.http.HttpServletRequest http) {
        return R.ok(mobileWorkService.outboundScan(id, req, userId, username, roles, hidePrice, clientIp(http)));
    }

    @RequirePerm("wh:outbound:confirm")
    @PostMapping("/outbounds/{id}/confirm")
    public R<Map<String, Object>> outboundConfirm(@PathVariable Long id,
                                                  @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.outboundConfirm(id, userId));
    }

    @GetMapping("/outbounds/{id}")
    public R<Map<String, Object>> outboundDetail(@PathVariable Long id,
                                                 @RequestAttribute(value = "roles", required = false) List<String> roles,
                                                 @RequestParam(required = false) Boolean hidePrice) {
        return R.ok(mobileWorkService.outboundDetail(id, roles, hidePrice));
    }

    // ---------- 到货 ----------

    @GetMapping("/arrivals/{id}")
    public R<Map<String, Object>> arrivalDetail(@PathVariable Long id,
                                                @RequestAttribute(value = "roles", required = false) List<String> roles,
                                                @RequestParam(required = false) Boolean hidePrice) {
        return R.ok(mobileWorkService.arrivalDetail(id, roles, hidePrice));
    }

    @RequirePerm("po:arrival:submit")
    @PostMapping("/arrivals/{id}/submit")
    public R<PurchaseArrival> arrivalSubmit(@PathVariable Long id,
                                            @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.arrivalSubmit(id, userId));
    }

    @RequirePerm("po:arrival:complete")
    @PostMapping("/arrivals/{id}/complete")
    public R<PurchaseArrival> arrivalComplete(@PathVariable Long id) {
        return R.ok(mobileWorkService.arrivalComplete(id));
    }

    // ---------- 领用 / 审批 ----------

    @RequirePerm("asset:use:create")
    @PostMapping("/material-use")
    public R<Map<String, Object>> createUse(@RequestBody UseCreateReq req,
                                            @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.createUse(req, userId));
    }

    @RequirePerm("approval:approve")
    @PostMapping("/approvals/{id}/approve")
    public R<Map<String, Object>> approve(@PathVariable Long id,
                                          @RequestBody(required = false) CommentReq req,
                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.approve(id, req, userId));
    }

    @RequirePerm("approval:reject")
    @PostMapping("/approvals/{id}/reject")
    public R<Map<String, Object>> reject(@PathVariable Long id,
                                         @RequestBody(required = false) CommentReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        return R.ok(mobileWorkService.reject(id, req, userId));
    }

    // ---------- 查询 / 溯源 ----------

    @GetMapping("/stocks")
    public R<PageResult<Map<String, Object>>> stocks(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) Long materialId,
                                                     @RequestParam(required = false) Long warehouseId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestAttribute(value = "roles", required = false) List<String> roles,
                                                     @RequestParam(required = false) Boolean hidePrice) {
        return R.ok(mobileWorkService.stocks(page, size, materialId, warehouseId, keyword, roles, hidePrice));
    }

    @GetMapping("/trace/{traceToken}")
    public R<Map<String, Object>> trace(@PathVariable String traceToken,
                                        @RequestAttribute(value = "userId", required = false) Long userId,
                                        @RequestAttribute(value = "roles", required = false) List<String> roles,
                                        @RequestParam(required = false) Boolean hidePrice) {
        return R.ok(mobileWorkService.trace(traceToken, userId, roles, hidePrice));
    }

    private static String clientIp(jakarta.servlet.http.HttpServletRequest http) {
        return http != null ? http.getRemoteAddr() : null;
    }
}
