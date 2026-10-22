package com.relex.wuzi.service;

import com.relex.wuzi.common.BillNoUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillNoUtilTest {

    @Test
    void prefixAndUnique() {
        String a = BillNoUtil.next("CG-");
        String b = BillNoUtil.next("CG-");
        assertTrue(a.startsWith("CG-"));
        assertNotEquals(a, b);
    }

    @Test
    void differentPrefixes() {
        assertNotEquals(BillNoUtil.next("JH-").substring(0, 3), BillNoUtil.next("FK-").substring(0, 3));
    }
}
