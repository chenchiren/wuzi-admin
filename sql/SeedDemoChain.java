import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 全链路演示数据生成器：清空业务测试数据后，按采购→到货→入库→库存→领用/借用/调拨
 * →退库/盘点/报废→预算合同付款→溯源码 生成带备注与税率的真实感数据。
 *
 * 编码规范（必须）：源码 UTF-8；JDK17 在中文 Windows 默认按 GBK 读源码，会写入乱码。
 * 推荐用法（PowerShell）：
 *   $jar = "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar"
 *   javac -encoding UTF-8 -cp $jar -d sql/out sql/SeedDemoChain.java
 *   java -Dfile.encoding=UTF-8 -cp "sql/out;$jar" SeedDemoChain
 * 禁止：java SeedDemoChain.java（source-file 模式 + 平台默认编码 → 中文乱码入库）
 */
public class SeedDemoChain {

    /** 口令不落库：优先读环境变量 WUZI_DB_PASSWORD（run-java-utf8.ps1 会自动从 sql/.env.local 注入）。 */
    static String dbPassword() {
        String p = System.getenv("WUZI_DB_PASSWORD");
        if (p == null || p.isBlank()) {
            System.err.println("[ERROR] 未设置 WUZI_DB_PASSWORD。");
            System.err.println("        请在终端设置该环境变量，或写入 sql/.env.local（已 gitignore）。");
            System.exit(2);
        }
        return p;
    }

    /** 账号必须显式提供：WUZI_DB_USER（建议用只授必要权限的业务账号，而非超级管理员）。 */
    static String dbUser() {
        String u = System.getenv("WUZI_DB_USER");
        if (u == null || u.isBlank()) {
            System.err.println("[ERROR] 未设置 WUZI_DB_USER（数据库账号）。");
            System.err.println("        请在终端设置该环境变量，或写入用户目录下的 .wuzi/env（仓库外，不入库）。");
            System.exit(2);
        }
        return u.trim();
    }

    static final String URL =
            "jdbc:mysql://localhost:3306/wuzi_test?useUnicode=true&characterEncoding=utf8"
                    + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    static String USER() { return dbUser(); }
    static String PASS() { return dbPassword(); }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(URL, USER(), PASS())) {
            c.setAutoCommit(false);
            clearBiz(c);
            seedAll(c);
            c.commit();
            System.out.println("SEED OK");
        }
    }

    // ---------- tax helpers (与 PriceTaxCalculator 主路径一致) ----------
    static BigDecimal excl(BigDecimal incl, BigDecimal ratePct) {
        return incl.divide(BigDecimal.ONE.add(ratePct.movePointLeft(2)), 6, RoundingMode.HALF_UP);
    }
    static BigDecimal excl(String incl, String ratePct) {
        return excl(new BigDecimal(incl), new BigDecimal(ratePct));
    }
    static BigDecimal amt(BigDecimal price, BigDecimal qty) {
        return price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }
    static BigDecimal taxOf(BigDecimal inclAmt, BigDecimal exclAmt) {
        return inclAmt.subtract(exclAmt);
    }

    static void clearBiz(Connection c) throws SQLException {
        String[] tables = {
                "trace_scan_log", "trace_qr_print_log", "trace_qr_code",
                "alert_record", "return_store_order_item", "return_store_order",
                "borrow_return_record", "borrow_order_item", "borrow_order",
                "scrap_order_item", "scrap_order",
                "inventory_check_item", "inventory_check",
                "transfer_order_item", "transfer_order",
                "outbound_order_item", "outbound_order",
                "material_use_apply_item", "material_use_apply",
                "stock_record", "stock_serial", "stock_batch", "stock",
                "inbound_order_item", "inbound_order",
                "purchase_return_item", "purchase_return",
                "purchase_arrival_item", "purchase_arrival",
                "purchase_order_item", "purchase_order",
                "purchase_inquiry_item", "purchase_inquiry",
                "purchase_apply_item", "purchase_apply",
                "payment_order", "contract_payment_node", "purchase_contract_item", "purchase_contract",
                "budget_control_log", "budget",
                "purchase_plan_item", "purchase_plan",
                "supplier_product_price", "supplier_product", "supplier_contact",
                "approval_record", "approval_instance",
                "material", "material_category",
                "supplier", "warehouse_location", "warehouse",
                "project"
        };
        try (Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String t : tables) {
                try {
                    st.execute("DELETE FROM " + t);
                } catch (SQLException ignore) {
                    // 表可能未建，跳过
                }
            }
            st.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    static void seedAll(Connection c) throws Exception {
        seedMaster(c);
        seedPurchaseChain(c);
        seedStockAndIssue(c);
        seedBorrowTransferScrap(c);
        seedFinance(c);
        seedTraceAndAlert(c);
    }

    // ===================== 主数据 =====================
    static void seedMaster(Connection c) throws Exception {
        // 分类
        insert(c, "material_category", map(
                "id", 1, "parent_id", 0, "category_code", "OFFICE", "category_name", "办公用品"));
        insert(c, "material_category", map(
                "id", 2, "parent_id", 1, "category_code", "PAPER", "category_name", "纸张耗材"));
        insert(c, "material_category", map(
                "id", 3, "parent_id", 1, "category_code", "PEN", "category_name", "文具书写"));
        insert(c, "material_category", map(
                "id", 4, "parent_id", 0, "category_code", "PPE", "category_name", "劳保用品"));
        insert(c, "material_category", map(
                "id", 5, "parent_id", 0, "category_code", "PROD", "category_name", "生产物资"));
        insert(c, "material_category", map(
                "id", 6, "parent_id", 0, "category_code", "IT", "category_name", "IT设备"));

        // 物资（含备注 / 税率 / 量管·件管）
        mat(c, 1, "A4-001", "A4打印纸", 2, "70g 500张/包", "得力", "包",
                1, 0, "BATCH", 0, "80.000", "800.000", "22.00", "13.00",
                "办公常规耗材，按批号管保质期；整箱20包/箱，箱码可批量收货。");
        mat(c, 2, "PEN-001", "中性签字笔", 3, "0.5mm 黑色 12支/盒", "晨光", "盒",
                0, 0, "SKU", 0, "40.000", "400.000", "18.50", "13.00",
                "书写耗材，不批次管理；领用按盒发放。");
        mat(c, 3, "TNR-001", "激光打印机硒鼓", 3, "CF218A 适用HP M104", "格之格", "个",
                0, 1, "SERIAL", 0, "5.000", "50.000", "168.00", "13.00",
                "高值易耗，一物一码跟踪更换；以旧换新登记。");
        mat(c, 4, "GLV-001", "丁腈防护手套", 4, "M码 100双/箱", "星宇", "双",
                1, 0, "BATCH", 0, "200.000", "2000.000", "2.80", "13.00",
                "劳保批量物资：一单可上百双，按箱码扫一次整行收货，禁止逐双扫码。");
        mat(c, 5, "HAT-001", "ABS安全帽", 4, "Y类 红色", "梅思安", "顶",
                0, 0, "SKU", 0, "30.000", "200.000", "28.00", "13.00",
                "工地标配，按人配发，损坏报废需拍照。");
        mat(c, 6, "BOOT-001", "防砸劳保鞋", 4, "42码 防刺穿", "代尔塔", "双",
                0, 0, "SKU", 0, "20.000", "150.000", "156.00", "13.00",
                "按尺码领用，换季集中采购。");
        mat(c, 7, "OIL-001", "工业润滑油", 5, "L-AN46 18L/桶", "长城", "桶",
                1, 0, "BATCH", 365, "10.000", "80.000", "420.00", "13.00",
                "有保质期，批次+有效期必填；临期预警提前30天。");
        mat(c, 8, "BOX-001", "五层瓦楞纸箱", 5, "50×40×30cm", "合兴", "个",
                0, 0, "SKU", 0, "300.000", "3000.000", "3.60", "13.00",
                "包装辅料，量管按数量。");
        mat(c, 9, "NB-001", "ThinkPad T14 笔记本", 6, "i7/32G/1T", "联想", "台",
                0, 1, "SERIAL", 0, "0.000", "20.000", "6899.00", "13.00",
                "件管资产：入库登记SN，借还扫码可见借期。");
        mat(c, 10, "CAM-001", "单反相机套机", 6, "EOS R6 Mark II", "佳能", "台",
                0, 1, "SERIAL", 0, "0.000", "5.000", "15999.00", "13.00",
                "可借出设备，长期借用需扫码归还；维保联系影像组。");

        // 供应商
        sup(c, 1, "GYS001", "沪信办公用品有限公司", "91310000MA1FL2XXXX", "GOODS",
                "周敏", "13816001234", "zhoumin@huxin-office.example",
                "上海市浦东新区张江路 288 号 3 楼",
                "招商银行上海张江支行", "121905888812345678",
                "TAX-13", "13.00", "GENERAL", "VAT_SPECIAL", 1, "MONTHLY", "BANK_TRANSFER",
                "COOPERATING",
                "主供应商，办公耗材月结30天；对账联系人周敏。");
        sup(c, 2, "GYS002", "鼎力劳保科技有限公司", "91320115MA20XXXXXX", "GOODS",
                "陈强", "13905123456", "chenqiang@dingli-ppe.example",
                "江苏省南京市江宁区科建路 66 号",
                "中国银行南京江宁支行", "5023700123456789",
                "TAX-13", "13.00", "GENERAL", "VAT_SPECIAL", 1, "MONTHLY", "BANK_TRANSFER",
                "COOPERATING",
                "劳保物资框架协议供应商；旺季需提前7天备货。");
        sup(c, 3, "GYS003", "远航工业品供应链有限公司", "91440300MA5EXXXXXX", "GOODS",
                "刘芳", "13632009876", "liufang@yuanhang-ind.example",
                "广东省深圳市宝安区新湖路 99 号",
                "工商银行深圳宝安支行", "4000028809200012345",
                "TAX-13", "13.00", "GENERAL", "VAT_SPECIAL", 1, "QUARTERLY", "BANK_TRANSFER",
                "COOPERATING",
                "润滑油/包装材料；起订量高，运费到付。");
        sup(c, 4, "GYS004", "智联信息技术有限公司", "91110108MA01XXXXXX", "GOODS",
                "赵磊", "13701234567", "zhaolei@zhilian-it.example",
                "北京市海淀区中关村大街 1 号",
                "建设银行北京海淀支行", "11001085200053001234",
                "TAX-13", "13.00", "GENERAL", "VAT_SPECIAL", 1, "MILESTONE", "BANK_TRANSFER",
                "COOPERATING",
                "IT设备；含上门部署，合同按里程碑付款。");

        // 联系人（补丁表，失败可忽略）
        try {
            insert(c, "supplier_contact", map(
                    "supplier_id", 1, "contact_name", "周敏", "phone", "13816001234",
                    "email", "zhoumin@huxin-office.example", "is_primary", 1,
                    "remark", "销售经理，对账与发票"));
            insert(c, "supplier_contact", map(
                    "supplier_id", 1, "contact_name", "吴涛", "phone", "13816005678",
                    "email", "wutao@huxin-office.example", "is_primary", 0,
                    "remark", "仓储发货对接"));
            insert(c, "supplier_contact", map(
                    "supplier_id", 4, "contact_name", "赵磊", "phone", "13701234567",
                    "email", "zhaolei@zhilian-it.example", "is_primary", 1,
                    "remark", "大客户经理"));
        } catch (SQLException e) {
            System.out.println("skip supplier_contact: " + e.getMessage());
        }

        // 供应商产品 + 价格历史
        sp(c, 1, 1, 1, "HUXIN-A4-70", "A4打印纸 70g", "70g 500张/包", "包", "22.00", "13.00");
        sp(c, 2, 1, 2, "HUXIN-PEN-05", "中性签字笔 0.5mm", "0.5mm 12支/盒", "盒", "18.50", "13.00");
        sp(c, 3, 1, 3, "HUXIN-CF218A", "硒鼓 CF218A", "CF218A", "个", "168.00", "13.00");
        sp(c, 4, 2, 4, "DINGLI-GLV-M", "丁腈手套 M码", "M码 100双/箱", "双", "2.80", "13.00");
        sp(c, 5, 2, 5, "DINGLI-HAT-ABS", "ABS安全帽", "Y类 红色", "顶", "28.00", "13.00");
        sp(c, 6, 2, 6, "DINGLI-BOOT-42", "防砸劳保鞋 42码", "42码", "双", "156.00", "13.00");
        sp(c, 7, 3, 7, "YH-OIL-AN46", "工业润滑油 L-AN46", "18L/桶", "桶", "420.00", "13.00");
        sp(c, 8, 3, 8, "YH-BOX-5040", "五层瓦楞纸箱", "50×40×30", "个", "3.60", "13.00");
        sp(c, 9, 4, 9, "ZL-T14-I7", "ThinkPad T14", "i7/32G/1T", "台", "6899.00", "13.00");
        sp(c, 10, 4, 10, "ZL-EOSR6", "佳能 EOS R6 II 套机", "R6 Mark II", "台", "15999.00", "13.00");

        price(c, 1, 1, 1, "22.00", "13.00", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 8, 31), "IMPORT", "历史协议价，已被9月新价替代");
        price(c, 2, 1, 1, "21.50", "13.00", LocalDate.of(2025, 9, 1), null, "ORDER", "9月起降价0.5元，当前有效价");
        price(c, 3, 4, 2, "2.80", "13.00", LocalDate.of(2025, 6, 1), null, "INQUIRY", "询价锁定价（当前有效）");
        price(c, 4, 4, 2, "2.65", "13.00", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 5, 31), "ORDER", "旧价关旧开新");

        // 仓库 / 库位
        wh(c, 1, "WH01", "主材料仓", "NORMAL", 2, "上海市松江区玉阳路 58 号 1 号库", "张仓管", "021-57120001");
        wh(c, 2, "WH02", "设备资产仓", "EQUIPMENT", 2, "上海市松江区玉阳路 58 号 2 号库", "张仓管", "021-57120001");
        wh(c, 3, "WH03", "项目现场临时仓", "PROJECT", 2, "苏州项目部临建仓", "王现场", "0512-66880002");

        loc(c, 1, 1, "A01", "A区货架01", "A", "纸张耗材");
        loc(c, 2, 1, "A02", "A区货架02", "A", "文具");
        loc(c, 3, 1, "B01", "B区货架01", "B", "劳保");
        loc(c, 4, 1, "B02", "B区货架02", "B", "生产辅料");
        loc(c, 5, 2, "E01", "设备架01", "E", "IT设备");
        loc(c, 6, 2, "E02", "设备架02", "E", "影像设备");
        loc(c, 7, 3, "P01", "项目区01", "P", "现场材料");

        // 项目
        try {
            insert(c, "project", map(
                    "id", 1, "project_code", "PRJ-2026-SZ", "project_name", "苏州智能工厂改造",
                    "project_type", "ENGINEERING", "dept_id", 4, "manager_name", "王研发",
                    "start_date", LocalDate.of(2026, 1, 1), "end_date", LocalDate.of(2026, 12, 31),
                    "status", 1, "remark", "年度重点技改项目，材料成本归集到本项目"));
            insert(c, "project", map(
                    "id", 2, "project_code", "PRJ-2026-HQ", "project_name", "总部办公升级",
                    "project_type", "ADMIN", "dept_id", 1, "manager_name", "李采购",
                    "start_date", LocalDate.of(2026, 3, 1), "end_date", LocalDate.of(2026, 6, 30),
                    "status", 1, "remark", "办公耗材与IT设备采购主项目"));
        } catch (SQLException e) {
            System.out.println("skip project: " + e.getMessage());
        }
    }

    static void mat(Connection c, int id, String code, String name, int cat, String spec, String brand,
                    String unit, int batch, int serial, String qr, Integer shelf,
                    String safety, String max, String price, String tax, String remark) throws SQLException {
        insert(c, "material", map(
                "id", id, "material_code", code, "material_name", name, "category_id", cat,
                "spec", spec, "brand", brand, "unit", unit, "barcode", "69" + (1000000 + id),
                "batch_managed", batch, "serial_managed", serial, "qr_granularity", qr,
                "shelf_life_days", shelf, "safety_stock", safety, "max_stock", max,
                "ref_price", price, "ref_tax_rate", tax, "status", 1, "remark", remark));
    }

    static void qr(Connection c, int id, String code, String type, String content, String token,
                   int matId, String batch, String serial, String matCode, String matName,
                   String granularity, String status) throws SQLException {
        Map<String, Object> m = map(
                "id", id, "qr_code", code, "qr_type", type, "qr_content", content,
                "trace_token", token, "content_mode", "STRUCT", "material_id", matId,
                "material_code", matCode, "material_name", matName,
                "qr_granularity", granularity, "status", status, "scan_count", 0, "print_count", 1);
        if (batch != null) m.put("batch_no", batch);
        if (serial != null) m.put("serial_no", serial);
        insert(c, "trace_qr_code", m);
    }

    static void sup(Connection c, int id, String code, String name, String credit, String type,
                    String contact, String phone, String email, String addr,
                    String bank, String account, String taxCode, String taxRate,
                    String qual, String invoice, int incl, String settle, String pay,
                    String coop, String remark) throws SQLException {
        insert(c, "supplier", map(
                "id", id, "supplier_code", code, "supplier_name", name, "credit_code", credit,
                "supplier_type", type, "contact_name", contact, "contact_phone", phone,
                "email", email, "address", addr, "bank_name", bank, "bank_account", account,
                "default_tax_code", taxCode, "default_tax_rate", taxRate,
                "tax_qualification", qual, "invoice_type", invoice, "settle_price_incl", incl,
                "settle_type", settle, "pay_type", pay, "coop_status", coop, "remark", remark));
    }

    static void sp(Connection c, int id, int supId, int matId, String code, String name,
                   String spec, String unit, String incl, String tax) throws SQLException {
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal t = new BigDecimal(tax);
        BigDecimal pn = excl(pi, t);
        BigDecimal un = pi.subtract(pn).setScale(6, RoundingMode.HALF_UP);
        insert(c, "supplier_product", map(
                "id", id, "supplier_id", supId, "material_id", matId,
                "supplier_product_code", code, "product_name", name, "spec", spec, "unit", unit,
                "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn, "tax_unit_amount", un,
                "price_tax_mode", "INCLUSIVE", "currency", "CNY"));
    }

    static void price(Connection c, int id, int spId, int supId, String incl, String tax,
                      LocalDate from, LocalDate to, String source, String remark) throws SQLException {
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal t = new BigDecimal(tax);
        BigDecimal pn = excl(pi, t);
        insert(c, "supplier_product_price", map(
                "id", id, "supplier_product_id", spId, "supplier_id", supId,
                "material_id", spId, "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn,
                "tax_unit_amount", pi.subtract(pn).setScale(6, RoundingMode.HALF_UP),
                "currency", "CNY", "effective_from", from, "effective_to", to,
                "source_type", source, "remark", remark));
    }

    static void wh(Connection c, int id, String code, String name, String type, int mgr,
                   String addr, String person, String phone) throws SQLException {
        insert(c, "warehouse", map(
                "id", id, "warehouse_code", code, "warehouse_name", name, "warehouse_type", type,
                "manager_id", mgr, "address", addr, "phone", phone, "status", 1));
    }

    static void loc(Connection c, int id, int wh, String code, String name, String area, String remark)
            throws SQLException {
        insert(c, "warehouse_location", map(
                "id", id, "warehouse_id", wh, "location_code", code, "location_name", name,
                "area", area, "status", 1));
    }

    // ===================== 采购链路 =====================
    static void seedPurchaseChain(Connection c) throws Exception {
        // 采购计划
        insert(c, "purchase_plan", map(
                "id", 1, "plan_no", "PL-2026-03-001", "title", "2026年3月办公与劳保月度计划",
                "plan_year", 2026, "plan_month", 3, "dept_id", 3, "project_code", "PRJ-2026-HQ",
                "plan_type", "MONTHLY", "status", "APPROVED", "total_amount", "18650.00",
                "remark", "3月常规补货+安全帽换新；已与仓储确认安全库存缺口。",
                "created_by", 3));
        insert(c, "purchase_plan_item", map(
                "id", 1, "plan_id", 1, "material_id", 1, "material_name", "A4打印纸",
                "spec", "70g 500张/包", "unit", "包", "qty", "200.000",
                "estimated_price", "22.000000", "estimated_amount", "4400.00",
                "purpose", "各部门打印用纸补货", "demand_dept_id", 1, "status", "CONVERTED",
                "remark", "按整箱20包拆分入库"));
        insert(c, "purchase_plan_item", map(
                "id", 2, "plan_id", 1, "material_id", 4, "material_name", "丁腈防护手套",
                "spec", "M码 100双/箱", "unit", "双", "qty", "1000.000",
                "estimated_price", "2.800000", "estimated_amount", "2800.00",
                "purpose", "车间劳保配发", "demand_dept_id", 4, "status", "CONVERTED",
                "remark", "大批量，入库按箱码一次收货"));
        insert(c, "purchase_plan_item", map(
                "id", 3, "plan_id", 1, "material_id", 5, "material_name", "ABS安全帽",
                "spec", "Y类 红色", "unit", "顶", "qty", "50.000",
                "estimated_price", "28.000000", "estimated_amount", "1400.00",
                "purpose", "新员工入场", "demand_dept_id", 4, "status", "CONVERTED",
                "remark", "编号贴纸由安全部提供"));

        // 采购申请
        insert(c, "purchase_apply", map(
                "id", 1, "apply_no", "PA-20260301-001", "apply_user_id", 4, "dept_id", 4,
                "apply_time", LocalDateTime.of(2026, 3, 1, 9, 30),
                "need_date", LocalDate.of(2026, 3, 15), "urgency", "NORMAL",
                "purpose", "苏州项目现场劳保与办公耗材补货",
                "status", "APPROVED", "total_tax_incl_amount", "9860.00",
                "remark", "计划PL-2026-03-001转入；项目成本归集到PRJ-2026-SZ。"));
        insert(c, "purchase_apply_item", map(
                "id", 1, "apply_id", 1, "material_id", 4, "qty", "1000.000", "unit", "双",
                "ref_price", "2.800000", "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_incl_amount", "2800.00", "remark", "现场手套消耗快，按箱到货"));
        insert(c, "purchase_apply_item", map(
                "id", 2, "apply_id", 1, "material_id", 5, "qty", "50.000", "unit", "顶",
                "ref_price", "28.000000", "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_incl_amount", "1400.00", "remark", "含新人入场20顶"));
        insert(c, "purchase_apply_item", map(
                "id", 3, "apply_id", 1, "material_id", 1, "qty", "200.000", "unit", "包",
                "ref_price", "22.000000", "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_incl_amount", "4400.00", "remark", "总部+现场分仓"));
        insert(c, "purchase_apply_item", map(
                "id", 4, "apply_id", 1, "material_id", 7, "qty", "12.000", "unit", "桶",
                "ref_price", "420.000000", "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_incl_amount", "5040.00", "remark", "设备保养，注意保质期"));

        // 询价
        insert(c, "purchase_inquiry", map(
                "id", 1, "inquiry_no", "IQ-20260302-001", "apply_id", 1,
                "title", "劳保手套/安全帽比价", "status", "FINISHED",
                "selected_supplier_id", 2,
                "remark", "三家询价，鼎力总价最优且含运费；已走询价审批。",
                "created_by", 3));
        insert(c, "purchase_inquiry_item", map(
                "id", 1, "inquiry_id", 1, "material_id", 4, "qty", "1000.000", "unit", "双",
                "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_inclusive_price", "2.800000", "tax_exclusive_price", excl("2.80", "13"),
                "tax_unit_amount", new BigDecimal("2.80").subtract(excl(new BigDecimal("2.80"), new BigDecimal("13"))).setScale(6, RoundingMode.HALF_UP),
                "tax_inclusive_amount", "2800.00",
                "tax_exclusive_amount", amt(excl(new BigDecimal("2.80"), new BigDecimal("13")), new BigDecimal("1000")),
                "tax_amount", taxOf(new BigDecimal("2800.00"), amt(excl(new BigDecimal("2.80"), new BigDecimal("13")), new BigDecimal("1000"))),
                "supplier_id", 2, "quote_remark", "含税到厂价，7个工作日到货", "is_selected", 1));
        insert(c, "purchase_inquiry_item", map(
                "id", 2, "inquiry_id", 1, "material_id", 5, "qty", "50.000", "unit", "顶",
                "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_inclusive_price", "28.000000", "tax_exclusive_price", excl("28", "13"),
                "tax_unit_amount", new BigDecimal("28").subtract(excl(new BigDecimal("28"), new BigDecimal("13"))).setScale(6, RoundingMode.HALF_UP),
                "tax_inclusive_amount", "1400.00", "tax_exclusive_amount", amt(excl(new BigDecimal("28"), new BigDecimal("13")), new BigDecimal("50")),
                "tax_amount", taxOf(new BigDecimal("1400.00"), amt(excl(new BigDecimal("28"), new BigDecimal("13")), new BigDecimal("50"))),
                "supplier_id", 2, "quote_remark", "可印企业LOGO，加急费另计", "is_selected", 1));

        // 采购订单（已确认，分两批到货）
        insert(c, "purchase_order", map(
                "id", 1, "order_no", "PO-20260305-001", "supplier_id", 2, "apply_id", 1,
                "purchase_user_id", 3, "dept_id", 3,
                "order_date", LocalDate.of(2026, 3, 5),
                "expect_arrival_date", LocalDate.of(2026, 3, 12),
                "currency", "CNY", "default_tax_code", "TAX-13", "default_tax_rate", "13.00",
                "tax_inclusive_amount", "9240.00", "tax_exclusive_amount", "8176.99",
                "tax_amount", "1063.01", "tax_rate_summary", "TAX-13×全部行",
                "pay_type", "BANK_TRANSFER", "settle_type", "MONTHLY", "status", "PART_RECEIVED",
                "remark", "鼎力劳保2月框架协议下单；首批手套安全帽，纸箱另单。",
                "created_by", 3));

        poi(c, 1, 1, 4, 4, "1000.000", "2.80", "13.00", "询价IQ-20260302-001定标");
        poi(c, 2, 1, 5, 5, "50.000", "28.00", "13.00", "含LOGO丝印（安全部提供稿）");
        poi(c, 3, 1, 1, 1, "200.000", "21.50", "13.00", "9月起执行新价21.50");

        insert(c, "purchase_order", map(
                "id", 2, "order_no", "PO-20260308-002", "supplier_id", 4, "apply_id", null,
                "purchase_user_id", 3, "dept_id", 1,
                "order_date", LocalDate.of(2026, 3, 8),
                "expect_arrival_date", LocalDate.of(2026, 3, 20),
                "currency", "CNY", "default_tax_code", "TAX-13", "default_tax_rate", "13.00",
                "tax_inclusive_amount", "20697.00", "tax_exclusive_amount", "18315.93",
                "tax_amount", "2381.07", "tax_rate_summary", "TAX-13×IT设备",
                "pay_type", "BANK_TRANSFER", "settle_type", "MILESTONE", "status", "CONFIRMED",
                "remark", "总部办公升级项目IT采购；合同HT-2026-002，到货后登记SN再入库。",
                "created_by", 3));
        poi(c, 4, 2, 9, 9, "3.000", "6899.00", "13.00", "研发2台+行政1台，需贴资产标签");
        poi(c, 5, 2, 10, 10, "0.000", "15999.00", "13.00", "影像设备暂缓，预算未批");

        // 到货
        insert(c, "purchase_arrival", map(
                "id", 1, "arrival_no", "ARR-20260310-001", "order_id", 1, "supplier_id", 2,
                "arrival_time", LocalDateTime.of(2026, 3, 10, 14, 20), "operator_id", 2,
                "transport_info", "顺丰陆运 SF1234567890；随货同行单 1 份",
                "status", "ACCEPTED",
                "remark", "外包装完好，手套10箱/1000双、安全帽50顶全数合格；A4纸分两批，本批100包。"));
        ari(c, 1, 1, 1, 4, "1000.000", "2.80", "13.00");
        ari(c, 2, 1, 2, 5, "50.000", "28.00", "13.00");
        ari(c, 3, 1, 3, 1, "100.000", "21.50", "13.00");

        insert(c, "purchase_arrival", map(
                "id", 2, "arrival_no", "ARR-20260318-002", "order_id", 2, "supplier_id", 4,
                "arrival_time", LocalDateTime.of(2026, 3, 18, 10, 5), "operator_id", 3,
                "transport_info", "德邦快递 DB9876543210；开箱验收需IT在场",
                "status", "ACCEPTED",
                "remark", "3台T14外观完好，序列号已登记清单附后；未拆封。"));
        ari(c, 4, 2, 4, 9, "3.000", "6899.00", "13.00");

        // 入库（已确认，走按单收货）
        insert(c, "inbound_order", map(
                "id", 1, "inbound_no", "IN-20260310-001", "inbound_type", "PURCHASE",
                "warehouse_id", 1, "supplier_id", 2, "source_no", "PO-20260305-001", "source_id", 1,
                "operator_id", 2, "inbound_time", LocalDateTime.of(2026, 3, 10, 16, 0),
                "status", "CONFIRMED",
                "remark", "按单收货：扫箱码定位行、整行填数量后整单确认；未逐件扫码。"));
        ini(c, 1, 1, 1, 4, 3, "1000.000", "2.80", "13.00", "GLV260310", LocalDate.of(2026, 3, 1), null);
        ini(c, 2, 1, 2, 5, 3, "50.000", "28.00", "13.00", null, null, null);
        ini(c, 3, 1, 3, 1, 1, "100.000", "21.50", "13.00", "B20260308", LocalDate.of(2026, 3, 8), null);

        insert(c, "inbound_order", map(
                "id", 2, "inbound_no", "IN-20260318-002", "inbound_type", "PURCHASE",
                "warehouse_id", 2, "supplier_id", 4, "source_no", "PO-20260308-002", "source_id", 2,
                "operator_id", 2, "inbound_time", LocalDateTime.of(2026, 3, 18, 15, 30),
                "status", "CONFIRMED",
                "remark", "件管物资：数量3台 + SN清单登记后整单确认（ThinkPad×3）。"));
        ini(c, 4, 2, 4, 9, 5, "3.000", "6899.00", "13.00", null, null, null);

        // 待收草稿单（多单场景，验证扫码反查）
        insert(c, "inbound_order", map(
                "id", 3, "inbound_no", "IN-20260320-003", "inbound_type", "PURCHASE",
                "warehouse_id", 1, "supplier_id", 1, "source_no", "PO-20260305-001", "source_id", 1,
                "operator_id", 2, "inbound_time", LocalDateTime.of(2026, 3, 20, 9, 0),
                "status", "DRAFT",
                "remark", "A4纸第二批100包待收；扫码反查应命中本单。"));
        ini(c, 5, 3, 3, 1, 1, "100.000", "21.50", "13.00", "B20260308", LocalDate.of(2026, 3, 8), null);

        // 采购退货（红冲样例）
        insert(c, "purchase_return", map(
                "id", 1, "return_no", "PR-20260312-001", "order_id", 1, "supplier_id", 2,
                "reason", "安全帽2顶注塑缺陷（帽壳裂纹），拍照已传供应商",
                "status", "CONFIRMED", "amount_sign", -1, "tax_incl_amount", "56.00",
                "remark", "按原订单含税价红冲，金额存正数+amount_sign=-1。"));
        pri(c, 1, 1, 2, 5, "2.000", "28.00", "13.00");
    }

    static void poi(Connection c, int id, int order, int mat, int sp, String qty,
                    String incl, String tax, String remark) throws SQLException {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal t = new BigDecimal(tax);
        BigDecimal pn = excl(pi, t);
        BigDecimal ai = amt(pi, q);
        BigDecimal an = amt(pn, q);
        insert(c, "purchase_order_item", map(
                "id", id, "order_id", order, "material_id", mat, "supplier_product_id", sp,
                "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn,
                "tax_unit_amount", pi.subtract(pn).setScale(6, RoundingMode.HALF_UP),
                "qty", qty, "received_qty", "0.000", "qualified_qty", "0.000",
                "tax_inclusive_amount", ai, "tax_exclusive_amount", an,
                "tax_amount", taxOf(ai, an), "currency", "CNY",
                "unit", mat == 4 ? "双" : (mat == 1 ? "包" : (mat == 5 ? "顶" : (mat == 9 ? "台" : "个"))),
                "price_source", "INQUIRY", "price_source_id", 1,
                "tax_calc_flag", "AUTO", "version", 0, "remark", remark));
    }

    static void ari(Connection c, int id, int arr, int poi, int mat, String qty,
                    String incl, String tax) throws SQLException {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal pn = excl(pi, new BigDecimal(tax));
        BigDecimal ai = amt(pi, q);
        BigDecimal an = amt(pn, q);
        insert(c, "purchase_arrival_item", map(
                "id", id, "arrival_id", arr, "purchase_order_item_id", poi, "material_id", mat,
                "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn, "arrive_qty", qty,
                "tax_inclusive_amount", ai, "tax_exclusive_amount", an,
                "tax_amount", taxOf(ai, an)));
    }

    static void ini(Connection c, int id, int inbound, int poi, int mat, int loc,
                    String qty, String incl, String tax, String batch,
                    LocalDate prod, LocalDate exp) throws SQLException {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal pn = excl(pi, new BigDecimal(tax));
        BigDecimal ai = amt(pi, q);
        BigDecimal an = amt(pn, q);
        Map<String, Object> m = map(
                "id", id, "inbound_id", inbound, "purchase_order_item_id", poi, "material_id", mat,
                "location_id", loc, "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn, "in_qty", qty,
                "cost_tax_incl", ai, "cost_tax_excl", an, "tax_amount", taxOf(ai, an));
        if (batch != null) m.put("batch_no", batch);
        if (prod != null) m.put("production_date", prod);
        if (exp != null) m.put("expire_date", exp);
        insert(c, "inbound_order_item", m);
    }

    static void pri(Connection c, int id, int ret, int poi, int mat, String qty,
                    String incl, String tax) throws SQLException {
        BigDecimal q = new BigDecimal(qty);
        BigDecimal pi = new BigDecimal(incl);
        BigDecimal pn = excl(pi, new BigDecimal(tax));
        BigDecimal ai = amt(pi, q);
        BigDecimal an = amt(pn, q);
        insert(c, "purchase_return_item", map(
                "id", id, "return_id", ret, "purchase_order_item_id", poi, "material_id", mat,
                "tax_code", "TAX-13", "tax_rate", tax,
                "tax_inclusive_price", pi.setScale(6, RoundingMode.HALF_UP),
                "tax_exclusive_price", pn, "return_qty", qty,
                "tax_inclusive_amount", ai, "tax_exclusive_amount", an,
                "tax_amount", taxOf(ai, an), "amount_sign", -1));
    }

    // ===================== 库存与领用出库 =====================
    static void seedStockAndIssue(Connection c) throws Exception {
        // 库存
        stock(c, 1, 1, 3, 4, "GLV260310", "1000.000", "2800.00", "2477.88", "13.00", "2.80");
        stock(c, 2, 1, 3, 5, null, "50.000", "1400.00", "1238.94", "13.00", "28.00");
        stock(c, 3, 1, 1, 1, "B20260308", "100.000", "2150.00", "1902.65", "13.00", "21.50");
        stock(c, 4, 2, 5, 9, null, "3.000", "20697.00", "18315.93", "13.00", "6899.00");

        // 批次台账
        batch(c, 1, "GLV260310", 4, 1, 2, 1, 1,
                LocalDate.of(2026, 3, 1), null, "2.80", "1000.000", "2800.00", "2477.88");
        batch(c, 2, "B20260308", 1, 1, 1, 1, 1,
                LocalDate.of(2026, 3, 8), null, "21.50", "100.000", "2150.00", "1902.65");

        // 件码
        serial(c, 1, "SN-T14-2026-0001", 9, 2, 5, null, "IN_STOCK", null);
        serial(c, 2, "SN-T14-2026-0002", 9, 2, 5, null, "IN_STOCK", null);
        serial(c, 3, "SN-T14-2026-0003", 9, 2, 5, null, "IN_STOCK", null);

        // 流水
        rec(c, 1, "INBOUND", "IN-20260310-001", 1, 1, 3, 4, "GLV260310", null,
                "1000.000", "0.000", "1000.000", 1, "2800.00", 2,
                "采购入库·按单收货确认；箱码定位行后整行1000双。");
        rec(c, 2, "INBOUND", "IN-20260310-001", 1, 1, 3, 5, null, null,
                "50.000", "0.000", "50.000", 1, "1400.00", 2, "安全帽入库");
        rec(c, 3, "INBOUND", "IN-20260310-001", 1, 1, 1, 1, "B20260308", null,
                "100.000", "0.000", "100.000", 1, "2150.00", 2, "A4纸首批入库");
        rec(c, 4, "INBOUND", "IN-20260318-002", 2, 2, 5, 9, null, "SN-T14-2026-0001",
                "3.000", "0.000", "3.000", 1, "20697.00", 2, "IT设备入库（3台，SN已登记）");
        rec(c, 5, "PURCHASE_RETURN", "PR-20260312-001", 1, 1, 3, 5, null, null,
                "-2.000", "50.000", "48.000", -1, "56.00", 2, "安全帽质量退货红冲2顶");

        // 领用申请 + 出库
        insert(c, "material_use_apply", map(
                "id", 1, "apply_no", "USE-20260315-001", "apply_user_id", 4, "dept_id", 4,
                "use_type", "PROJECT", "project_name", "苏州智能工厂改造",
                "need_date", LocalDate.of(2026, 3, 16),
                "purpose", "现场施工劳保配发（3月上旬班组）",
                "status", "ISSUED",
                "remark", "班组12人，手套每人2双、安全帽每人1顶；剩余回库。"));
        insert(c, "material_use_apply_item", map(
                "id", 1, "apply_id", 1, "material_id", 4, "qty", "24.000", "unit", "双",
                "remark", "12人×2双"));
        insert(c, "material_use_apply_item", map(
                "id", 2, "apply_id", 1, "material_id", 5, "qty", "12.000", "unit", "顶",
                "remark", "12人×1顶，旧帽回收"));

        insert(c, "outbound_order", map(
                "id", 1, "outbound_no", "OUT-20260316-001", "outbound_type", "USE",
                "warehouse_id", 1, "apply_id", 1, "dept_id", 4, "user_id", 4, "operator_id", 2,
                "outbound_time", LocalDateTime.of(2026, 3, 16, 11, 0), "status", "CONFIRMED",
                "remark", "按领用单发料；超发需红字审批（本单未超发）。"));
        insert(c, "outbound_order_item", map(
                "id", 1, "outbound_id", 1, "material_id", 4, "batch_no", "GLV260310",
                "location_id", 3, "qty", "24.000", "actual_qty", "24.000",
                "tax_incl_price", "2.800000", "tax_rate", "13.00"));
        insert(c, "outbound_order_item", map(
                "id", 2, "outbound_id", 1, "material_id", 5, "batch_no", null,
                "location_id", 3, "qty", "12.000", "actual_qty", "12.000",
                "tax_incl_price", "28.000000", "tax_rate", "13.00"));

        rec(c, 6, "USE_ISSUE", "OUT-20260316-001", 1, 1, 3, 4, "GLV260310", null,
                "-24.000", "1000.000", "976.000", -1, "67.20", 2, "领用发料·苏州项目");
        rec(c, 7, "USE_ISSUE", "OUT-20260316-001", 1, 1, 3, 5, null, null,
                "-12.000", "48.000", "36.000", -1, "336.00", 2, "安全帽发料");

        // 同步库存
        updateQty(c, 1, "976.000", "2732.80", "2426.43");
        updateQty(c, 2, "36.000", "1008.00", "893.81");

        // 退库
        insert(c, "return_store_order", map(
                "id", 1, "return_no", "RS-20260322-001", "apply_id", 1, "warehouse_id", 1,
                "apply_user_id", 4, "accept_user_id", 2, "confirm_user_id", 2,
                "status", "CONFIRMED",
                "apply_remark", "现场完工退回未使用手套8双、安全帽1顶（完好）。",
                "accept_remark", "外观完好，无油污，可再发放。",
                "confirm_remark", "已入主仓A/B区，批次沿用GLV260310。",
                "apply_time", LocalDateTime.of(2026, 3, 22, 9, 0),
                "accept_time", LocalDateTime.of(2026, 3, 22, 10, 0),
                "confirm_time", LocalDateTime.of(2026, 3, 22, 10, 30),
                "remark", "退库三段备注齐全，可审计。"));
        insert(c, "return_store_order_item", map(
                "id", 1, "return_id", 1, "material_id", 4, "batch_no", "GLV260310",
                "location_id", 3, "apply_qty", "8.000", "accept_qty", "8.000",
                "actual_qty", "8.000", "tax_incl_price", "2.800000", "tax_rate", "13.00",
                "condition_status", "GOOD", "remark", "整双未拆封"));
        insert(c, "return_store_order_item", map(
                "id", 2, "return_id", 1, "material_id", 5, "batch_no", null,
                "location_id", 3, "apply_qty", "1.000", "accept_qty", "1.000",
                "actual_qty", "1.000", "tax_incl_price", "28.000000", "tax_rate", "13.00",
                "condition_status", "GOOD", "remark", "帽壳无划痕"));
        rec(c, 8, "RETURN_IN", "RS-20260322-001", 1, 1, 3, 4, "GLV260310", null,
                "8.000", "976.000", "984.000", 1, "22.40", 2, "退库入库");
        rec(c, 9, "RETURN_IN", "RS-20260322-001", 1, 1, 3, 5, null, null,
                "1.000", "36.000", "37.000", 1, "28.00", 2, "退库入库");
        updateQty(c, 1, "984.000", "2755.20", "2446.21");
        updateQty(c, 2, "37.000", "1036.00", "919.18");
    }

    static void stock(Connection c, int id, int wh, int loc, int mat, String batch,
                      String qty, String costIn, String costEx, String tax, String price)
            throws SQLException {
        Map<String, Object> m = map(
                "id", id, "warehouse_id", wh, "location_id", loc, "material_id", mat,
                "qty", qty, "frozen_qty", "0.000",
                "cost_tax_incl", costIn, "cost_tax_excl", costEx,
                "last_tax_rate", tax, "last_tax_incl_price", price, "version", 0);
        if (batch != null) m.put("batch_no", batch);
        insert(c, "stock", m);
    }

    static void batch(Connection c, int id, String batch, int mat, int wh, int sup, int po, int in,
                      LocalDate prod, LocalDate exp, String price, String qty,
                      String costIn, String costEx) throws SQLException {
        Map<String, Object> m = map(
                "id", id, "batch_no", batch, "material_id", mat, "warehouse_id", wh,
                "supplier_id", sup, "purchase_order_id", po, "inbound_order_id", in,
                "production_date", prod, "tax_code", "TAX-13", "tax_rate", "13.00",
                "tax_inclusive_price", price, "tax_exclusive_price", excl(new BigDecimal(price), new BigDecimal("13")),
                "cost_tax_incl", costIn, "cost_tax_excl", costEx, "qty", qty);
        if (exp != null) m.put("expire_date", exp);
        insert(c, "stock_batch", m);
    }

    static void serial(Connection c, int id, String sn, int mat, int wh, int loc,
                       String batch, String status, Long user) throws SQLException {
        Map<String, Object> m = map(
                "id", id, "serial_no", sn, "material_id", mat, "warehouse_id", wh,
                "location_id", loc, "status", status);
        if (batch != null) m.put("batch_no", batch);
        if (user != null) m.put("user_id", user);
        insert(c, "stock_serial", m);
    }

    static void rec(Connection c, int id, String type, String no, int biz, int wh, int loc,
                    int mat, String batch, String sn, String change, String before, String after,
                    int sign, String amount, int op, String remark) throws SQLException {
        Map<String, Object> m = map(
                "id", id, "biz_type", type, "biz_no", no, "biz_id", biz,
                "warehouse_id", wh, "location_id", loc, "material_id", mat,
                "change_qty", change, "before_qty", before, "after_qty", after,
                "amount_sign", sign, "tax_incl_amount", amount,
                "operator_id", op, "remark", remark,
                "created_at", LocalDateTime.of(2026, 3, 1 + id, 10, 0));
        if (batch != null) m.put("batch_no", batch);
        if (sn != null) m.put("serial_no", sn);
        insert(c, "stock_record", m);
    }

    static void updateQty(Connection c, int stockId, String qty, String costIn, String costEx)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE stock SET qty=?, cost_tax_incl=?, cost_tax_excl=? WHERE id=?")) {
            ps.setString(1, qty);
            ps.setString(2, costIn);
            ps.setString(3, costEx);
            ps.setInt(4, stockId);
            ps.executeUpdate();
        }
    }

    // ===================== 借用 / 调拨 / 盘点 / 报废 =====================
    static void seedBorrowTransferScrap(Connection c) throws Exception {
        // 借用（长期借用 + 扫码归还场景）
        insert(c, "borrow_order", map(
                "id", 1, "borrow_no", "BR-20250601-003", "apply_user_id", 4, "dept_id", 4,
                "borrow_type", "LONG", "project_name", "苏州智能工厂改造",
                "borrow_date", LocalDate.of(2025, 6, 1),
                "expect_return_date", LocalDate.of(2025, 12, 1),
                "purpose", "现场验收拍照与记录，影像资料归档",
                "status", "LENT",
                "remark", "长期借用：扫件码应显示借出日与已借天数，可直接登记归还。",
                "created_by", 4));
        insert(c, "borrow_order_item", map(
                "id", 1, "borrow_id", 1, "material_id", 10, "qty", "1.000",
                "lent_qty", "1.000", "returned_qty", "0.000", "unit", "台",
                "remark", "EOS R6套机一台（待入库SN，当前按数量借出）"));

        insert(c, "borrow_order", map(
                "id", 2, "borrow_no", "BR-20260316-001", "apply_user_id", 4, "dept_id", 4,
                "borrow_type", "TEMP", "project_name", "苏州智能工厂改造",
                "borrow_date", LocalDate.of(2026, 3, 16),
                "expect_return_date", LocalDate.of(2026, 3, 25),
                "purpose", "调试期临时借笔记本写程序",
                "status", "PART_RETURNED",
                "remark", "2台已借，1台提前归还（2026-03-20）。",
                "created_by", 4));
        insert(c, "borrow_order_item", map(
                "id", 2, "borrow_id", 2, "material_id", 9, "qty", "2.000",
                "lent_qty", "2.000", "returned_qty", "1.000", "unit", "台",
                "remark", "SN见流水：0001/0002；0002已还"));
        insert(c, "borrow_return_record", map(
                "id", 1, "borrow_id", 2, "borrow_item_id", 2, "material_id", 9,
                "return_qty", "1.000", "condition_status", "GOOD", "operator_id", 2,
                "remark", "扫码归还：SN-T14-2026-0002，电池健康98%",
                "return_time", LocalDateTime.of(2026, 3, 20, 17, 10)));

        // 调拨
        insert(c, "transfer_order", map(
                "id", 1, "transfer_no", "TR-20260319-001",
                "from_warehouse_id", 1, "to_warehouse_id", 3, "operator_id", 2,
                "transfer_time", LocalDateTime.of(2026, 3, 19, 13, 0), "status", "CONFIRMED",
                "remark", "主仓→苏州项目临时仓；在途20双手套，实到18双（路损2双已登记）。"));
        insert(c, "transfer_order_item", map(
                "id", 1, "transfer_id", 1, "material_id", 4, "batch_no", "GLV260310",
                "from_location_id", 3, "to_location_id", 7, "qty", "20.000",
                "actual_qty", "18.000", "tax_incl_price", "2.800000", "tax_rate", "13.00"));

        // 盘点
        insert(c, "inventory_check", map(
                "id", 1, "check_no", "IC-20260325-001", "warehouse_id", 1, "operator_id", 2,
                "check_time", LocalDateTime.of(2026, 3, 25, 9, 0), "status", "CONFIRMED",
                "remark", "3月末例行盘点：账面984双手套，实盘982双，盘亏2双（发料错记）。"));
        insert(c, "inventory_check_item", map(
                "id", 1, "check_id", 1, "material_id", 4, "batch_no", "GLV260310",
                "location_id", 3, "book_qty", "984.000", "actual_qty", "982.000",
                "diff_qty", "-2.000", "tax_incl_price", "2.800000",
                "remark", "盘亏2双，原因：出库扫码未记账，已开差异单"));

        // 报废
        insert(c, "scrap_order", map(
                "id", 1, "scrap_no", "SC-20260326-001", "apply_user_id", 4, "dept_id", 4,
                "scrap_reason", "安全帽受冲击后帽壳裂纹，强制报废（安监要求）",
                "scrap_date", LocalDate.of(2026, 3, 26), "status", "CONFIRMED",
                "remark", "报废3顶，已拍照上传；不得再发放。",
                "created_by", 4));
        insert(c, "scrap_order_item", map(
                "id", 1, "scrap_id", 1, "material_id", 5, "qty", "3.000",
                "confirmed_qty", "3.000", "unit", "顶",
                "reason", "冲击损坏", "remark", "编号 HA-007/012/019"));
    }

    // ===================== 预算 / 合同 / 付款 =====================
    static void seedFinance(Connection c) throws Exception {
        insert(c, "budget", map(
                "id", 1, "budget_no", "BG-2026-PUR-001", "budget_year", 2026, "dept_id", 3,
                "project_code", "PRJ-2026-HQ", "category", "PURCHASE",
                "budget_amount", "120000.00", "used_amount", "29937.00", "frozen_amount", "0.00",
                "status", "ACTIVE",
                "remark", "总部办公升级采购预算；已用=PO-001+PO-002含税金额。",
                "created_by", 5));
        insert(c, "budget_control_log", map(
                "id", 1, "budget_id", 1, "action", "CONSUME", "amount", "9240.00",
                "biz_type", "PURCHASE_ORDER", "biz_id", 1,
                "before_used", "0.00", "before_frozen", "0.00",
                "after_used", "9240.00", "after_frozen", "0.00",
                "remark", "PO-20260305-001下账", "created_by", 5));
        insert(c, "budget_control_log", map(
                "id", 2, "budget_id", 1, "action", "CONSUME", "amount", "20697.00",
                "biz_type", "PURCHASE_ORDER", "biz_id", 2,
                "before_used", "9240.00", "before_frozen", "0.00",
                "after_used", "29937.00", "after_frozen", "0.00",
                "remark", "PO-20260308-002下账", "created_by", 5));

        insert(c, "purchase_contract", map(
                "id", 1, "contract_no", "HT-2026-001", "title", "2026年度办公劳保框架采购合同",
                "contract_type", "FRAMEWORK", "supplier_id", 2,
                "sign_date", LocalDate.of(2026, 1, 15),
                "start_date", LocalDate.of(2026, 1, 1), "end_date", LocalDate.of(2026, 12, 31),
                "contract_amount", "80000.00", "paid_amount", "0.00",
                "tax_inclusive_amount", "80000.00", "tax_amount", "9203.54",
                "status", "EFFECTIVE", "budget_id", 1, "dept_id", 3, "owner_id", 3,
                "remark", "框架协议，按实际下单结算；价目见附件二。",
                "created_by", 3));
        insert(c, "purchase_contract_item", map(
                "id", 1, "contract_id", 1, "material_id", 4, "material_name", "丁腈防护手套",
                "spec", "M码 100双/箱", "unit", "双", "qty", "20000.000",
                "price", "2.800000", "tax_rate", "13.00",
                "tax_exclusive_amount", "49557.52", "tax_inclusive_amount", "56000.00",
                "tax_amount", "6442.48", "remark", "年度预估量，多退少补"));
        insert(c, "purchase_contract_item", map(
                "id", 2, "contract_id", 1, "material_id", 5, "material_name", "ABS安全帽",
                "spec", "Y类 红色", "unit", "顶", "qty", "800.000",
                "price", "28.000000", "tax_rate", "13.00",
                "tax_exclusive_amount", "19823.01", "tax_inclusive_amount", "22400.00",
                "tax_amount", "2576.99", "remark", "含LOGO丝印"));

        insert(c, "purchase_contract", map(
                "id", 2, "contract_no", "HT-2026-002", "title", "总部IT设备采购及部署合同",
                "contract_type", "ORDINARY", "supplier_id", 4,
                "sign_date", LocalDate.of(2026, 3, 8),
                "start_date", LocalDate.of(2026, 3, 8), "end_date", LocalDate.of(2026, 6, 30),
                "contract_amount", "20697.00", "paid_amount", "10348.50",
                "tax_inclusive_amount", "20697.00", "tax_amount", "2381.07",
                "status", "EFFECTIVE", "budget_id", 1, "dept_id", 1, "owner_id", 3,
                "remark", "到货验收后付50%，部署完成付尾款；对应PO-20260308-002。",
                "created_by", 3));
        insert(c, "purchase_contract_item", map(
                "id", 3, "contract_id", 2, "material_id", 9, "material_name", "ThinkPad T14 笔记本",
                "spec", "i7/32G/1T", "unit", "台", "qty", "3.000",
                "price", "6899.000000", "tax_rate", "13.00",
                "tax_exclusive_amount", "18315.93", "tax_inclusive_amount", "20697.00",
                "tax_amount", "2381.07", "remark", "含3年上门服务"));

        insert(c, "contract_payment_node", map(
                "id", 1, "contract_id", 2, "node_name", "预付款50%",
                "plan_date", LocalDate.of(2026, 3, 20), "plan_amount", "10348.50",
                "paid_amount", "10348.50", "status", "PAID",
                "remark", "到货验收通过后支付"));
        insert(c, "contract_payment_node", map(
                "id", 2, "contract_id", 2, "node_name", "尾款50%",
                "plan_date", LocalDate.of(2026, 4, 30), "plan_amount", "10348.50",
                "paid_amount", "0.00", "status", "PENDING",
                "remark", "部署验收后支付，需IT签字"));

        insert(c, "payment_order", map(
                "id", 1, "payment_no", "PAY-20260320-001", "supplier_id", 4, "contract_id", 2,
                "pay_node_id", 1, "budget_id", 1, "amount", "10348.50",
                "pay_method", "BANK", "pay_date", LocalDate.of(2026, 3, 20),
                "status", "PAID", "dept_id", 5,
                "remark", "合同HT-2026-002首期；银行回单号 BOC2026032000123。",
                "created_by", 5));
    }

    // ===================== 溯源码 / 预警 =====================
    static void seedTraceAndAlert(Connection c) throws Exception {
        qr(c, 1, "SKU-MAT-1", "SKU", "MAT:A4-001", "tk-a4-sku-0001", 1, null, null,
                "A4-001", "A4打印纸", "SKU", "ACTIVE");
        qr(c, 2, "BATCH-A4-B20260308", "BATCH", "MAT:A4-001|BATCH:B20260308",
                "tk-a4-batch-20260308", 1, "B20260308", null,
                "A4-001", "A4打印纸", "BATCH", "IN_STOCK");
        qr(c, 3, "BATCH-GLV-GLV260310", "BATCH", "MAT:GLV-001|BATCH:GLV260310",
                "tk-glv-batch-260310", 4, "GLV260310", null,
                "GLV-001", "丁腈防护手套", "BATCH", "IN_STOCK");
        qr(c, 4, "SERIAL-T14-0001", "SERIAL", "MAT:NB-001|SN:SN-T14-2026-0001",
                "tk-nb-sn-0001", 9, null, "SN-T14-2026-0001",
                "NB-001", "ThinkPad T14 笔记本", "SERIAL", "BORROWED");
        qr(c, 5, "SERIAL-T14-0002", "SERIAL", "MAT:NB-001|SN:SN-T14-2026-0002",
                "tk-nb-sn-0002", 9, null, "SN-T14-2026-0002",
                "NB-001", "ThinkPad T14 笔记本", "SERIAL", "IN_STOCK");
        qr(c, 6, "SERIAL-CAM-001", "SERIAL", "MAT:CAM-001|SN:SN-CAM-2025-0001",
                "tk-cam-sn-0001", 10, null, "SN-CAM-2025-0001",
                "CAM-001", "单反相机套机", "SERIAL", "BORROWED");

        try {
            insert(c, "trace_qr_print_log", map(
                    "qr_id", 2, "qr_code", "BATCH-A4-B20260308", "print_batch_no", "PB-20260310-01",
                    "template_code", "DEFAULT_50X30",
                    "printer_name", "Zebra-GK420d", "operator_id", 2, "print_count", 2,
                    "print_time", LocalDateTime.of(2026, 3, 10, 16, 20),
                    "remark", "入库后贴签；补打1张（首张卡纸）"));
            insert(c, "trace_qr_print_log", map(
                    "qr_id", 3, "qr_code", "BATCH-GLV-GLV260310", "print_batch_no", "PB-20260310-02",
                    "template_code", "DEFAULT_50X30",
                    "printer_name", "Zebra-GK420d", "operator_id", 2, "print_count", 10,
                    "print_time", LocalDateTime.of(2026, 3, 10, 16, 25),
                    "remark", "手套箱码10箱"));
        } catch (SQLException e) {
            System.out.println("skip print_log: " + e.getMessage());
        }

        // 扫码日志样例
        try {
            insert(c, "trace_scan_log", map(
                    "qr_id", 4, "qr_code", "SERIAL-T14-0001", "scan_type", "RETURN",
                    "biz_type", "BORROW", "biz_id", 2, "operator_id", 2,
                    "operator_name", "张仓管", "channel", "H5", "warehouse_id", 2,
                    "result", "SUCCESS",
                    "scan_time", LocalDateTime.of(2026, 3, 20, 17, 5)));
            insert(c, "trace_scan_log", map(
                    "qr_id", 3, "qr_code", "BATCH-GLV-GLV260310", "scan_type", "IN",
                    "biz_type", "INBOUND", "biz_id", 1, "operator_id", 2,
                    "operator_name", "张仓管", "channel", "PDA", "warehouse_id", 1,
                    "result", "SUCCESS",
                    "scan_time", LocalDateTime.of(2026, 3, 10, 15, 55)));
        } catch (SQLException e) {
            System.out.println("skip scan_log: " + e.getMessage());
        }

        insert(c, "alert_record", map(
                "id", 1, "alert_type", "LOW_STOCK", "alert_level", "WARN",
                "title", "中性签字笔库存低于安全库存",
                "content", "当前库存0，安全库存40盒，建议按3月计划补货。",
                "material_id", 2, "warehouse_id", 1, "qty", "0.000",
                "threshold_val", "40.000", "status", "OPEN",
                "remark", "演示预警"));
        insert(c, "alert_record", map(
                "id", 2, "alert_type", "EXPIRY", "alert_level", "INFO",
                "title", "润滑油批次临期提醒",
                "content", "OIL批次若启用请关注有效期；本演示无到期批次，占位展示。",
                "material_id", 7, "warehouse_id", 1,
                "expire_date", LocalDate.of(2027, 3, 1), "status", "OPEN",
                "remark", "临期预警模板"));
    }

    // ===================== SQL helpers =====================
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static final Map<String, Set<String>> COLS = new HashMap<>();

    static Set<String> colsOf(Connection c, String table) throws SQLException {
        Set<String> s = COLS.get(table);
        if (s != null) return s;
        s = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) s.add(rs.getString(1).toLowerCase());
            }
        }
        COLS.put(table, s);
        return s;
    }

    static void insert(Connection c, String table, Map<String, Object> row) throws SQLException {
        Set<String> cols = colsOf(c, table);
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (cols.contains(e.getKey().toLowerCase()) && e.getValue() != null) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        if (filtered.isEmpty()) return;
        String colList = String.join(",", filtered.keySet());
        String qs = String.join(",", Collections.nCopies(filtered.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + qs + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : filtered.values()) {
                if (v instanceof Integer n) ps.setInt(i++, n);
                else if (v instanceof Long n) ps.setLong(i++, n);
                else if (v instanceof BigDecimal n) ps.setBigDecimal(i++, n);
                else if (v instanceof LocalDate d) ps.setDate(i++, java.sql.Date.valueOf(d));
                else if (v instanceof LocalDateTime t) ps.setTimestamp(i++, Timestamp.valueOf(t));
                else ps.setString(i++, String.valueOf(v));
            }
            try {
                ps.executeUpdate();
            } catch (SQLException e) {
                System.out.println("INSERT FAIL " + table + ": " + e.getMessage());
                System.out.println("  " + filtered);
                throw e;
            }
        }
    }
}
