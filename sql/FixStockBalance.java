import java.math.BigDecimal;
import java.sql.*;

/** Insert BALANCE_FIX stock_record so SUM(change_qty) matches stock.qty per warehouse/material/batch. */
public class FixStockBalance {

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

            ResultSet stocks = st.executeQuery(
                    "SELECT id, warehouse_id, material_id, IFNULL(batch_no,''), qty FROM stock WHERE is_deleted=0");
            PreparedStatement sumQ = c.prepareStatement(
                    "SELECT IFNULL(SUM(change_qty),0) FROM stock_record WHERE warehouse_id=? AND material_id=? AND IFNULL(batch_no,'')=?");
            PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO stock_record(biz_type,biz_no,biz_id,warehouse_id,material_id,batch_no,change_qty,before_qty,after_qty,amount_sign,tax_incl_amount,operator_id,remark,created_at) "
                            + "VALUES('BALANCE_FIX',?,0,?,?,?,?,?,?,?,?,1,?,NOW())");
            int n = 0;
            while (stocks.next()) {
                long stockId = stocks.getLong(1);
                long wh = stocks.getLong(2);
                long mat = stocks.getLong(3);
                String batch = stocks.getString(4);
                BigDecimal qty = stocks.getBigDecimal(5);
                sumQ.setLong(1, wh);
                sumQ.setLong(2, mat);
                sumQ.setString(3, batch);
                ResultSet rs = sumQ.executeQuery();
                rs.next();
                BigDecimal sum = rs.getBigDecimal(1);
                if (sum == null) sum = BigDecimal.ZERO;
                if (qty == null) qty = BigDecimal.ZERO;
                BigDecimal diff = qty.subtract(sum);
                if (diff.abs().compareTo(new BigDecimal("0.001")) <= 0) {
                    continue;
                }
                ins.setString(1, "BFIX-" + stockId);
                ins.setLong(2, wh);
                ins.setLong(3, mat);
                ins.setString(4, batch);
                ins.setBigDecimal(5, diff);
                ins.setBigDecimal(6, sum);
                ins.setBigDecimal(7, qty);
                ins.setInt(8, diff.compareTo(BigDecimal.ZERO) >= 0 ? 1 : -1);
                ins.setBigDecimal(9, BigDecimal.ZERO);
                ins.setString(10, "repair legacy abs()/batch mismatch");
                ins.executeUpdate();
                n++;
                System.out.println("balance_fix stockId=" + stockId + " mat=" + mat + " batch='" + batch + "' sum=" + sum + " qty=" + qty + " adj=" + diff);
            }
            System.out.println("TOTAL_BALANCE_FIX=" + n);
        }
    }
}
