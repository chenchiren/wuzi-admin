import java.sql.*;

public class EncCheck {

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
    public static void main(String[] a) throws Exception {
        System.out.println("file.encoding=" + System.getProperty("file.encoding"));
        System.out.println("native.encoding=" + System.getProperty("native.encoding"));
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/wuzi_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false",
                dbUser(), dbPassword());
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SHOW VARIABLES LIKE 'character_set%'");
            while (rs.next()) {
                System.out.println(rs.getString(1) + "=" + rs.getString(2));
            }
            rs = st.executeQuery("SELECT material_code, material_name, HEX(material_name) FROM material ORDER BY id");
            while (rs.next()) {
                System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
            }
            rs = st.executeQuery("SELECT category_name, HEX(category_name) FROM material_category ORDER BY id");
            while (rs.next()) {
                System.out.println("cat " + rs.getString(1) + " | " + rs.getString(2));
            }
            rs = st.executeQuery("SELECT warehouse_name, HEX(warehouse_name) FROM warehouse ORDER BY id");
            while (rs.next()) {
                System.out.println("wh " + rs.getString(1) + " | " + rs.getString(2));
            }
        }
    }
}
