package com.relex.wuzi.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限解析契约：不再用路径后缀猜动作。
 * 动作码以 Controller 上的 @RequirePerm 为准；本解析器仅作动词兜底。
 */
class PermCodeResolverTest {

    @Test
    void createPost() {
        assertEquals("po:apply:create", PermCodeResolver.resolve("POST", "/api/purchase/applies"));
        assertEquals("sys:user:create", PermCodeResolver.resolve("POST", "/api/users"));
        assertEquals("sys:position:create", PermCodeResolver.resolve("POST", "/api/positions"));
    }

    @Test
    void updatePutDelete() {
        assertEquals("base:material:update", PermCodeResolver.resolve("PUT", "/api/materials/1"));
        assertEquals("base:material:delete", PermCodeResolver.resolve("DELETE", "/api/materials/1"));
    }

    @Test
    void noSuffixGuessing() {
        // 不再由 /submit /approve 猜动作；写接口应靠 @RequirePerm
        assertEquals("po:apply:create", PermCodeResolver.resolve("POST", "/api/purchase/applies/9/submit"));
        // 动词兜底是 create，而不是 endsWith 猜出的 approval:approve
        assertEquals("approval:create", PermCodeResolver.resolve("POST", "/api/approvals/1/approve"));
        assertNotEquals("approval:approve", PermCodeResolver.resolve("POST", "/api/approvals/1/approve"));
    }

    @Test
    void redFlushIsNotOutboundCreate() {
        // 红冲完整路径不在 EXPLICIT，且 POST 不再猜 red-flush 后缀
        // 实际动作由 OutboundController/RedFlushController 的 @RequirePerm 声明
        String code = PermCodeResolver.resolve("POST", "/api/outbound/1/red-flush");
        assertEquals("wh:outbound:create", code);
    }

    @Test
    void readHasNoPerm() {
        assertNull(PermCodeResolver.resolve("GET", "/api/stock"));
    }

    @Test
    void explicitPaths() {
        assertEquals("msg:read", PermCodeResolver.resolve("POST", "/api/messages/read-all"));
        assertEquals("flow:config:create", PermCodeResolver.resolve("POST", "/api/flow-approvals/flows"));
    }

    @Test
    void flowConfigVerbFallback() {
        assertEquals("flow:config:update", PermCodeResolver.resolve("PUT", "/api/flow-approvals/flows/1"));
        assertEquals("flow:config:delete", PermCodeResolver.resolve("DELETE", "/api/flow-approvals/flows/1"));
    }
}
