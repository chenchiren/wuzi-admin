import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Normalize mixed-encoding Chinese seed data in wuzi_test.
 * Also re-applies known-good Chinese labels for core tables.
 */
public class FixDbEncoding {

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
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/wuzi_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        String user = args.length > 0 ? args[0] : dbUser();
        String pass = args.length > 1 ? args[1] : dbPassword();

        try (Connection c = DriverManager.getConnection(url, user, pass); Statement st = c.createStatement()) {
            System.out.println("connected");

            // Show current corrupted values
            dump(st, "SELECT id, username, real_name, HEX(real_name) FROM sys_user ORDER BY id");
            dump(st, "SELECT id, role_code, role_name, HEX(role_name) FROM sys_role ORDER BY id");
            dump(st, "SELECT id, dept_name FROM sys_dept ORDER BY id");
            dump(st, "SELECT id, category_name FROM material_category ORDER BY id");

            // Fix core user real names
            exec(st, "UPDATE sys_user SET real_name='系统管理员' WHERE id=1 AND username='admin'");
            exec(st, "UPDATE sys_user SET real_name='张仓管' WHERE id=2 AND username='wh01'");
            exec(st, "UPDATE sys_user SET real_name='李采购' WHERE id=3 AND username='pur01'");
            exec(st, "UPDATE sys_user SET real_name='王研发' WHERE id=4 AND username='rd01'");

            // Fix roles
            exec(st, "UPDATE sys_role SET role_name='系统管理员' WHERE role_code='ADMIN'");
            exec(st, "UPDATE sys_role SET role_name='仓库管理员' WHERE role_code='WAREHOUSE'");
            exec(st, "UPDATE sys_role SET role_name='采购员' WHERE role_code='PURCHASER'");
            exec(st, "UPDATE sys_role SET role_name='普通员工' WHERE role_code='EMPLOYEE'");
            exec(st, "UPDATE sys_role SET role_name='财务人员' WHERE role_code='FINANCE'");

            // Fix depts
            exec(st, "UPDATE sys_dept SET dept_name='总公司' WHERE dept_code='ROOT'");
            exec(st, "UPDATE sys_dept SET dept_name='仓储部' WHERE dept_code='WH'");
            exec(st, "UPDATE sys_dept SET dept_name='采购部' WHERE dept_code='PUR'");
            exec(st, "UPDATE sys_dept SET dept_name='研发部' WHERE dept_code='RD'");
            exec(st, "UPDATE sys_dept SET dept_name='财务部' WHERE dept_code='FIN'");

            // Fix material categories
            exec(st, "UPDATE material_category SET category_name='办公用品' WHERE category_code='OFFICE'");
            exec(st, "UPDATE material_category SET category_name='纸张' WHERE category_code='PAPER'");
            exec(st, "UPDATE material_category SET category_name='文具' WHERE category_code='PEN'");
            exec(st, "UPDATE material_category SET category_name='生产物资' WHERE category_code='PROD'");
            exec(st, "UPDATE material_category SET category_name='劳保用品' WHERE category_code='PPE'");

            // Fix common materials/suppliers/warehouses if present
            tryUpdate(st, "UPDATE material SET material_name='A4打印纸', unit='包' WHERE material_code='A4-001'");
            tryUpdate(st, "UPDATE material SET material_name='黑色签字笔', unit='盒' WHERE material_code='PEN-001'");
            tryUpdate(st, "UPDATE material SET material_name='硒鼓', unit='个' WHERE material_code='TNR-001'");
            tryUpdate(st, "UPDATE supplier SET supplier_name='XX办公用品有限公司', contact_name='张三' WHERE supplier_code='GYS001'");
            tryUpdate(st, "UPDATE supplier SET supplier_name='XX商贸有限公司', contact_name='李四' WHERE supplier_code='GYS002'");
            tryUpdate(st, "UPDATE warehouse SET warehouse_name='主仓库' WHERE warehouse_code='WH01'");
            tryUpdate(st, "UPDATE warehouse SET warehouse_name='设备仓' WHERE warehouse_code='WH02'");

            // Fix tax rate dict (seed Chinese names)
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='零税率' WHERE tax_code='TAX-00'");
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='简易征收1%' WHERE tax_code='TAX-01'");
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='简易征收3%' WHERE tax_code='TAX-03'");
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='服务6%' WHERE tax_code='TAX-06'");
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='运输建筑9%' WHERE tax_code='TAX-09'");
            tryUpdate(st, "UPDATE tax_rate_dict SET tax_name='货物13%' WHERE tax_code='TAX-13'");
            dump(st, "SELECT tax_code, tax_name, HEX(tax_name) FROM tax_rate_dict ORDER BY id");

            System.out.println("--- after ---");
            dump(st, "SELECT id, username, real_name FROM sys_user ORDER BY id");
            dump(st, "SELECT id, role_code, role_name FROM sys_role ORDER BY id");
            System.out.println("DONE");
        }
    }

    static void exec(Statement st, String sql) throws SQLException {
        int n = st.executeUpdate(sql);
        System.out.println("OK rows=" + n + " :: " + sql);
    }

    static void tryUpdate(Statement st, String sql) {
        try {
            int n = st.executeUpdate(sql);
            System.out.println("OK rows=" + n + " :: " + sql);
        } catch (SQLException e) {
            System.out.println("SKIP :: " + sql + " :: " + e.getMessage());
        }
    }

    static void dump(Statement st, String sql) throws SQLException {
        System.out.println("QUERY " + sql);
        try (ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder("  ");
                for (int i = 1; i <= n; i++) {
                    if (i > 1) sb.append(" | ");
                    sb.append(md.getColumnLabel(i)).append('=').append(rs.getString(i));
                }
                System.out.println(sb);
            }
        }
    }
}
