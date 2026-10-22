package com.relex.wuzi.web.mobile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.security.JwtAuthFilter;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.TraceScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机端首页：待办计数 + 库存预警 + 最近扫码。
 */
@RestController
@RequestMapping("/api/m")
@RequiredArgsConstructor
public class MobileHomeController {

    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final MaterialUseApplyMapper materialUseApplyMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final PurchaseArrivalMapper purchaseArrivalMapper;
    private final StockMapper stockMapper;
    private final TraceScanLogMapper traceScanLogMapper;
    private final SysUserMapper sysUserMapper;

    @GetMapping("/home")
    public R<Map<String, Object>> home(@RequestAttribute(value = "userId", required = false) Long userId,
                                       @RequestParam(required = false) Boolean hidePrice,
                                       @RequestAttribute(value = "roles", required = false) List<String> roles) {
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);

        long pendingApprovals = approvalInstanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING));
        long submittedApplies = materialUseApplyMapper.selectCount(new LambdaQueryWrapper<MaterialUseApply>()
                .eq(MaterialUseApply::getStatus, "SUBMITTED"));
        long draftInbounds = inboundOrderMapper.selectCount(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getStatus, "DRAFT"));
        long draftOutbounds = outboundOrderMapper.selectCount(new LambdaQueryWrapper<OutboundOrder>()
                .eq(OutboundOrder::getStatus, "DRAFT"));
        long draftArrivals = purchaseArrivalMapper.selectCount(new LambdaQueryWrapper<PurchaseArrival>()
                .eq(PurchaseArrival::getStatus, "DRAFT"));
        long myApplies = 0L;
        if (userId != null) {
            myApplies = materialUseApplyMapper.selectCount(new LambdaQueryWrapper<MaterialUseApply>()
                    .eq(MaterialUseApply::getApplyUserId, userId));
        }

        Map<String, Object> todos = new HashMap<>();
        todos.put("pendingApprovals", pendingApprovals);
        todos.put("submittedMaterialUse", submittedApplies);
        todos.put("draftInbounds", draftInbounds);
        todos.put("draftOutbounds", draftOutbounds);
        todos.put("draftArrivals", draftArrivals);
        todos.put("myMaterialUse", myApplies);

        List<Map<String, Object>> low = stockMapper.selectLowStock();
        List<Map<String, Object>> over = stockMapper.selectOverStock();
        Map<String, Object> alerts = new HashMap<>();
        alerts.put("lowStockCount", low.size());
        alerts.put("overStockCount", over.size());
        alerts.put("lowStock", low.size() > 5 ? low.subList(0, 5) : low);
        alerts.put("overStock", over.size() > 5 ? over.subList(0, 5) : over);

        LambdaQueryWrapper<TraceScanLog> scanQw = new LambdaQueryWrapper<TraceScanLog>()
                .orderByDesc(TraceScanLog::getId)
                .last("LIMIT 10");
        if (userId != null) {
            scanQw.eq(TraceScanLog::getOperatorId, userId);
        }
        List<TraceScanLog> recent = traceScanLogMapper.selectList(scanQw);
        List<Map<String, Object>> recentScans = new ArrayList<>();
        for (TraceScanLog log : recent) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", log.getId());
            row.put("qrCode", log.getQrCode());
            row.put("result", log.getResult());
            row.put("channel", log.getChannel());
            row.put("scanType", log.getScanType());
            row.put("scanTime", log.getScanTime());
            recentScans.add(row);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("todos", todos);
        data.put("alerts", alerts);
        data.put("recentScans", recentScans);
        data.put("hidePrice", hide);
        return R.ok(data);
    }

    @GetMapping("/todos")
    public R<Map<String, Object>> todos(@RequestParam(required = false) String role,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        Map<String, Object> data = new HashMap<>();
        if ("approver".equalsIgnoreCase(role)) {
            data.put("pendingApprovals", approvalInstanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                    .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)));
            data.put("submittedMaterialUse", materialUseApplyMapper.selectCount(new LambdaQueryWrapper<MaterialUseApply>()
                    .eq(MaterialUseApply::getStatus, "SUBMITTED")));
        } else if ("wh".equalsIgnoreCase(role)) {
            data.put("draftInbounds", inboundOrderMapper.selectCount(new LambdaQueryWrapper<InboundOrder>()
                    .eq(InboundOrder::getStatus, "DRAFT")));
            data.put("draftOutbounds", outboundOrderMapper.selectCount(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getStatus, "DRAFT")));
            data.put("draftArrivals", purchaseArrivalMapper.selectCount(new LambdaQueryWrapper<PurchaseArrival>()
                    .eq(PurchaseArrival::getStatus, "DRAFT")));
        } else {
            data.put("myMaterialUse", userId == null ? 0 : materialUseApplyMapper.selectCount(
                    new LambdaQueryWrapper<MaterialUseApply>().eq(MaterialUseApply::getApplyUserId, userId)));
            data.put("pendingApprovals", approvalInstanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstance>()
                    .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)));
        }
        return R.ok(data);
    }
}
