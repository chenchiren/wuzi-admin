package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.ApprovalInstance;
import com.relex.wuzi.mapper.ApprovalInstanceMapper;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的审批待办（前 10 条）。可见角色与审批动作白名单一致。
 */
@Component
@RequiredArgsConstructor
public class QueryMyTodosTool implements AiTool {

    private static final int MAX_ROWS = 10;

    private final ApprovalInstanceMapper instanceMapper;
    private final FlowApprovalService flowApprovalService;

    @Override
    public String name() {
        return "query_my_todos";
    }

    @Override
    public String label() {
        return "查询待办";
    }

    @Override
    public String description() {
        return "查询当前用户名下的审批待办（单号、类型、发起人、当前节点）。用户问「我有几单要审/待办」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return ctx.hasRole("DEPT_MANAGER") || ctx.hasRole("FINANCE")
                || ctx.hasRole("WAREHOUSE") || ctx.hasRole("PURCHASER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        List<ApprovalInstance> allPending = instanceMapper.selectList(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getStatus, ApprovalService.INST_PENDING)
                .orderByDesc(ApprovalInstance::getId));
        List<FlowApprovalService.TodoRow> rows = flowApprovalService.buildTodoRows(allPending, ctx.userId());

        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowApprovalService.TodoRow row : rows.subList(0, Math.min(MAX_ROWS, rows.size()))) {
            ApprovalInstance inst = row.inst();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bizNo", inst.getBizNo());
            m.put("bizType", inst.getBizType());
            m.put("title", inst.getTitle());
            m.put("currentNodeName", row.currentNodeName());
            m.put("updatedAt", inst.getUpdatedAt());
            out.add(m);
        }
        String digest = "待办" + rows.size() + "条" + (out.isEmpty() ? "" : "，首条: " + out.get(0).get("bizNo"));
        return ToolResult.of("你有 " + rows.size() + " 条待审批", out, digest);
    }
}
