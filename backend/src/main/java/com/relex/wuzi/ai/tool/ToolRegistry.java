package com.relex.wuzi.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：收集全部 {@link AiTool}，按角色裁剪可见性，校验参数。
 * 无权限的工具不进 tools 定义（对模型不可见），而非运行时拒绝。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();
    private final ObjectMapper om;

    public ToolRegistry(List<AiTool> discovered, ObjectMapper om) {
        this.om = om;
        for (AiTool t : discovered) {
            AiTool prev = tools.putIfAbsent(t.name(), t);
            if (prev != null) {
                log.warn("duplicate AI tool name [{}], keep first", t.name());
            }
        }
        log.info("AI tools registered: {}", tools.keySet());
    }

    public AiTool byName(String name) {
        return name == null ? null : tools.get(name);
    }

    public String labelOf(String name) {
        AiTool t = byName(name);
        return t == null ? name : t.label();
    }

    /** OpenAI tools 定义（按角色过滤） */
    public List<Map<String, Object>> visibleDefs(ToolContext ctx) {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (AiTool t : tools.values()) {
            if (!t.allowedFor(ctx)) {
                continue;
            }
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", t.jsonSchema());
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("type", "function");
            def.put("function", fn);
            defs.add(def);
        }
        return defs;
    }

    /** 解析模型给出的参数 JSON（非法抛 IllegalArgumentException） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> args = om.readValue(argumentsJson, Map.class);
            return args == null ? new LinkedHashMap<>() : args;
        } catch (Exception e) {
            throw new IllegalArgumentException("参数不是合法 JSON");
        }
    }

    /** 按 JSON Schema 校验：required 必填 + 基本类型；失败抛异常（错误将回传模型重试） */
    public void validateArgs(AiTool tool, Map<String, Object> args) {
        Map<String, Object> schema = tool.jsonSchema();
        if (schema == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        for (String key : required) {
            if (args.get(key) == null || String.valueOf(args.get(key)).isBlank()) {
                throw new IllegalArgumentException("缺少必填参数: " + key);
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object spec = properties.get(e.getKey());
            if (!(spec instanceof Map)) {
                continue; // 模型多给的键忽略
            }
            @SuppressWarnings("unchecked")
            String type = String.valueOf(((Map<String, Object>) spec).getOrDefault("type", "string"));
            Object v = e.getValue();
            boolean ok = switch (type) {
                case "string" -> v instanceof String;
                case "number" -> v instanceof Number;
                case "integer" -> v instanceof Number && !(v instanceof Double && ((Double) v) % 1 != 0);
                case "boolean" -> v instanceof Boolean;
                default -> true;
            };
            if (!ok) {
                throw new IllegalArgumentException("参数 " + e.getKey() + " 类型应为 " + type);
            }
        }
    }

    /** 供门禁自检：name → required 参数缺失即启动失败（可扩展） */
    public int size() {
        return tools.size();
    }
}
