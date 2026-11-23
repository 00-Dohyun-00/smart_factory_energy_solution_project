import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 공용 DB 읽기 전용 조회. backend/.env의 PRIMARY_DB_* 값으로 접속하고, 접속 정보는 출력하지 않음.
 * SELECT / WITH 로 시작하는 조회만 받고, 연결도 읽기 전용으로 엶 (공용 DB 보호).
 * 사용법: java -cp <postgresql.jar>;. DbQuery <.env 경로> <출력 csv 또는 -> "<SQL>"
 */
public class DbQuery {
    public static void main(String[] args) throws Exception {
        String sql = args[2].trim();
        String head = sql.toLowerCase();
        if (!(head.startsWith("select") || head.startsWith("with")) || sql.contains(";")) {
            throw new IllegalArgumentException("SELECT / WITH 조회 한 문장만 실행합니다.");
        }
        Map<String, String> env = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.trim().startsWith("#")) {
                env.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim().replaceAll("^\"|\"$", ""));
            }
        }
        String url = env.get("PRIMARY_DB_URL");
        if (!url.startsWith("jdbc:")) {
            url = "jdbc:" + url;
        }
        Properties props = new Properties();
        props.setProperty("user", env.get("PRIMARY_DB_USERNAME"));
        props.setProperty("password", env.get("PRIMARY_DB_PASSWORD"));
        props.setProperty("readOnly", "true");

        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setReadOnly(true);
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SET TRANSACTION READ ONLY");
                st.setFetchSize(5000);
                try (ResultSet rs = st.executeQuery(sql);
                     PrintWriter w = args[1].equals("-")
                             ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
                             : new PrintWriter(args[1], StandardCharsets.UTF_8)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    StringBuilder header = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        header.append(i > 1 ? "," : "").append(md.getColumnLabel(i));
                    }
                    w.println(header);
                    long rows = 0;
                    while (rs.next()) {
                        StringBuilder row = new StringBuilder();
                        for (int i = 1; i <= n; i++) {
                            String v = rs.getString(i);
                            row.append(i > 1 ? "," : "").append(v == null ? "" : v.replace(",", " "));
                        }
                        w.println(row);
                        rows++;
                    }
                    w.flush();
                    if (!args[1].equals("-")) {
                        System.out.println(rows + "행 저장");
                    }
                }
            }
            conn.rollback();
        }
    }
}
