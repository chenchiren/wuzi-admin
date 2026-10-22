import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class InitDb {

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
        String sqlFile = args[0];
        byte[] bytes = Files.readAllBytes(Paths.get(sqlFile));
        // Prefer UTF-8. Only fall back per-line for legacy GBK segments —
        // re-decoding the whole file as GBK corrupts valid UTF-8 Chinese.
        String sql = decodeSqlBytes(bytes);
        String url = "jdbc:mysql://localhost:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        List<String> stmts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\r?\n")) {
            String trim = line.trim();
            if (trim.startsWith("--") || trim.startsWith("#")) continue;
            sb.append(line).append('\n');
            if (trim.endsWith(";")) {
                stmts.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.toString().trim().length() > 0) stmts.add(sb.toString());

        try (Connection c = DriverManager.getConnection(url, dbUser(), dbPassword()); Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS wuzi_test DEFAULT CHARACTER SET utf8mb4");
            st.execute("USE wuzi_test");
            int ok = 0, fail = 0;
            for (String s : stmts) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                try {
                    st.execute(t);
                    ok++;
                } catch (SQLException e) {
                    fail++;
                    System.out.println("FAIL: " + e.getMessage());
                    System.out.println("  head: " + t.substring(0, Math.min(100, t.length())).replace('\n', ' '));
                }
            }
            System.out.println("OK=" + ok + " FAIL=" + fail);
            ResultSet rs = st.executeQuery("SHOW TABLES");
            while (rs.next()) System.out.println("TABLE " + rs.getString(1));
        }
    }

    /** Decode line-by-line: UTF-8 first, GBK only for invalid UTF-8 lines. */
    static String decodeSqlBytes(byte[] bytes) {
        String latin = new String(bytes, StandardCharsets.ISO_8859_1);
        String[] lines = latin.split("\r?\n", -1);
        Charset gbk = Charset.forName("GBK");
        StringBuilder sb = new StringBuilder(bytes.length + 64);
        for (int i = 0; i < lines.length; i++) {
            byte[] lineBytes = lines[i].getBytes(StandardCharsets.ISO_8859_1);
            String decoded = null;
            boolean utfOk = true;
            try {
                decoded = new String(lineBytes, StandardCharsets.UTF_8);
                if (decoded.indexOf((char) 0xFFFD) >= 0) utfOk = false;
            } catch (Exception ex) {
                utfOk = false;
            }
            if (!utfOk) {
                decoded = new String(lineBytes, gbk);
            }
            if (i > 0) sb.append('\n');
            sb.append(decoded);
        }
        return sb.toString();
    }
}
