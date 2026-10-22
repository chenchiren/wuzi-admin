package com.relex.wuzi.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApiAccessRulesTest {

    @Test
    void adminOnlyPaths() {
        assertTrue(ApiAccessRules.isAdminOnly("/api/users"));
        assertTrue(ApiAccessRules.isAdminOnly("/api/roles/1/permissions"));
        assertFalse(ApiAccessRules.isAdminOnly("/api/stock"));
    }

    @Test
    void writeRequiresRoles() {
        List<String> required = ApiAccessRules.requiredRoles("POST", "/api/warehouses");
        assertNotNull(required);
        assertTrue(ApiAccessRules.allows(List.of("WAREHOUSE", "ADMIN"), required));
        assertFalse(ApiAccessRules.allows(List.of("EMPLOYEE"), required));
    }

    @Test
    void readAllowsAnyAuthenticated() {
        assertNull(ApiAccessRules.requiredRoles("GET", "/api/stock"));
        assertTrue(ApiAccessRules.allows(List.of("EMPLOYEE"), null));
    }

    @Test
    void purchaseWriteRestricted() {
        List<String> required = ApiAccessRules.requiredRoles("POST", "/api/purchase/orders");
        assertTrue(ApiAccessRules.allows(List.of("PURCHASER"), required));
        assertFalse(ApiAccessRules.allows(List.of("EMPLOYEE"), required));
    }

    @Test
    void financeWriteRestricted() {
        List<String> required = ApiAccessRules.requiredRoles("POST", "/api/invoices");
        assertTrue(ApiAccessRules.allows(List.of("FINANCE"), required));
        assertFalse(ApiAccessRules.allows(List.of("WAREHOUSE"), required));
    }

    @Test
    void unregisteredWriteIsDeniedByDefault() {
        List<String> required = ApiAccessRules.requiredRoles("POST", "/api/unknown-module/thing");
        assertEquals(ApiAccessRules.DENY, required);
        assertFalse(ApiAccessRules.allows(List.of("ADMIN", "WAREHOUSE", "FINANCE"), required));
    }

    @Test
    void mobileWriteTightened() {
        List<String> inbound = ApiAccessRules.requiredRoles("POST", "/api/m/inbounds/1/confirm");
        assertTrue(ApiAccessRules.allows(List.of("WAREHOUSE"), inbound));
        assertFalse(ApiAccessRules.allows(List.of("FINANCE"), inbound));

        List<String> use = ApiAccessRules.requiredRoles("POST", "/api/m/material-use");
        assertTrue(ApiAccessRules.allows(List.of("EMPLOYEE"), use));
        assertFalse(ApiAccessRules.allows(List.of("FINANCE"), use));
    }
}
