package com.relex.wuzi.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 写接口权限契约：业务写端点必须声明 @RequirePerm 或 @LoginOnly。
 * 与运行期 {@link PermContractChecker} 双保险（本测试可不启 Spring）。
 */
class PermContractTest {

    private static final Set<String> PUBLIC_AUTH_PREFIX = Set.of(
            "com.relex.wuzi.web.AuthController",
            "com.relex.wuzi.web.mobile.MobileAuthController",
            "com.relex.wuzi.web.mobile.WeComAuthController",
            "com.relex.wuzi.web.mobile.DingTalkAuthController"
    );

    @Test
    void writeEndpointsMustDeclarePerm() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Class<?> clazz : scanControllers()) {
            if (PUBLIC_AUTH_PREFIX.contains(clazz.getName())) {
                continue;
            }
            for (Method m : clazz.getDeclaredMethods()) {
                if (!isWriteMapping(m)) {
                    continue;
                }
                boolean hasPerm = m.isAnnotationPresent(com.relex.wuzi.annotation.RequirePerm.class)
                        || m.isAnnotationPresent(com.relex.wuzi.annotation.LoginOnly.class)
                        || clazz.isAnnotationPresent(com.relex.wuzi.annotation.RequirePerm.class);
                if (!hasPerm) {
                    missing.add(clazz.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertTrue(missing.isEmpty(),
                "写接口缺少 @RequirePerm/@LoginOnly:\n" + String.join("\n", missing));
    }

    @Test
    void permCodesFollowResourceActionFormat() {
        List<String> bad = new ArrayList<>();
        for (Class<?> clazz : scanControllers()) {
            for (Method m : clazz.getDeclaredMethods()) {
                var ann = m.getAnnotation(com.relex.wuzi.annotation.RequirePerm.class);
                if (ann == null) {
                    continue;
                }
                for (String code : ann.value()) {
                    if (code == null || !code.matches("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$")) {
                        bad.add(clazz.getSimpleName() + "#" + m.getName() + " -> " + code);
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "非法权限码:\n" + String.join("\n", bad));
    }

    @Test
    void inquiryApproveIsNotGeneric() throws Exception {
        boolean found = false;
        for (Method method : Class.forName("com.relex.wuzi.web.PurchaseInquiryController").getDeclaredMethods()) {
            var ann = method.getAnnotation(com.relex.wuzi.annotation.RequirePerm.class);
            if (ann != null) {
                for (String c : ann.value()) {
                    if ("po:inquiry:approve".equals(c)) {
                        found = true;
                    }
                    assertNotEquals("approval:approve", c, "询价审批不得使用通用 approval:approve");
                }
            }
        }
        assertTrue(found, "询价审批应声明 po:inquiry:approve");
    }

    private static boolean isWriteMapping(Method m) {
        return m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                || m.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class);
    }

    private static List<Class<?>> scanControllers() {
        // 轻量扫描：显式列出关键 Controller 包下类（由测试类路径推断）
        List<Class<?>> list = new ArrayList<>();
        String[] names = {
                "com.relex.wuzi.web.ApprovalController",
                "com.relex.wuzi.web.AttachmentController",
                "com.relex.wuzi.web.BasicDataController",
                "com.relex.wuzi.web.BorrowController",
                "com.relex.wuzi.web.CategoryController",
                "com.relex.wuzi.web.DeptController",
                "com.relex.wuzi.web.ExcelCenterController",
                "com.relex.wuzi.web.ExcelImportController",
                "com.relex.wuzi.web.ExpiryAlertController",
                "com.relex.wuzi.web.FlowApprovalController",
                "com.relex.wuzi.web.InboundController",
                "com.relex.wuzi.web.InventoryCheckController",
                "com.relex.wuzi.web.InventoryCheckEnhanceController",
                "com.relex.wuzi.web.InvoiceController",
                "com.relex.wuzi.web.LabelTemplateController",
                "com.relex.wuzi.web.LogController",
                "com.relex.wuzi.web.MaterialUseController",
                "com.relex.wuzi.web.MessageController",
                "com.relex.wuzi.web.OutboundController",
                "com.relex.wuzi.web.PositionController",
                "com.relex.wuzi.web.PurchaseApplyController",
                "com.relex.wuzi.web.PurchaseArrivalController",
                "com.relex.wuzi.web.PurchaseInquiryController",
                "com.relex.wuzi.web.PurchaseOrderController",
                "com.relex.wuzi.web.PurchaseReturnController",
                "com.relex.wuzi.web.RedFlushController",
                "com.relex.wuzi.web.ReturnStoreController",
                "com.relex.wuzi.web.ReturnStoreOrderController",
                "com.relex.wuzi.web.RoleController",
                "com.relex.wuzi.web.ScrapController",
                "com.relex.wuzi.web.SupplierContactController",
                "com.relex.wuzi.web.SupplierEvalApiController",
                "com.relex.wuzi.web.SysDictController",
                "com.relex.wuzi.web.TaxPriceController",
                "com.relex.wuzi.web.TracePrintController",
                "com.relex.wuzi.web.TraceQrController",
                "com.relex.wuzi.web.TransferController",
                "com.relex.wuzi.web.UserController",
                "com.relex.wuzi.web.WarehouseController",
                "com.relex.wuzi.web.mobile.MobileWorkController",
                "com.relex.wuzi.web.mobile.MobileScanController",
                "com.relex.wuzi.web.mobile.MobileSyncController",
        };
        for (String n : names) {
            try {
                list.add(Class.forName(n));
            } catch (ClassNotFoundException ignored) {
                // 类未生成时跳过
            }
        }
        return list;
    }
}
