import java.sql.*;

/** Repair stock_record.change_qty so before_qty + change_qty = after_qty (legacy abs() bug). */
public class FixStockRecordSign {

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
        try (Connection c = DriverManager.getConnection(url, dbUser(), dbPassword());
             Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT id, change_qty, before_qty, after_qty, amount_sign FROM stock_record");
            int fixed = 0;
            PreparedStatement up = c.prepareStatement(
                    "UPDATE stock_record SET change_qty = ? WHERE id = ?");
            while (rs.next()) {
                long id = rs.getLong(1);
                double cq = rs.getDouble(2);
                double before = rs.getDouble(3);
                double after = rs.getDouble(4);
                int sign = rs.getInt(5);
                if (Math.abs((before + cq) - after) <= 0.001) {
                    continue;
                }
                double correct = after - before;
                up.setDouble(1, correct);
                up.setLong(2, id);
                up.executeUpdate();
                fixed++;
                System.out.println("fix id=" + id + " cq " + cq + " -> " + correct
                        + " (before=" + before + " after=" + after + " sign=" + sign + ")");
            }
            System.out.println("TOTAL_FIXED=" + fixed);
        }
    }
}
