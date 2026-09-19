package cn.luotiany1.NeteasePets.storage;

import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

public final class MySqlStorage implements Storage {
    private static final Pattern IDENT = Pattern.compile("[A-Za-z0-9_]+");

    private final String url, user, pass, table;
    private final int timeout;
    private Connection con;

    public MySqlStorage(ConfigurationSection c) {
        String host = req(c, "host").trim();
        host = host.replace("%", "%25");
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) host = '[' + host + ']';
        int port = c.getInt("port", 3306);
        String base = req(c, "database"), tab = c.getString("table", "npets_inventory");
        if (!IDENT.matcher(base).matches()) throw new IllegalArgumentException("mysql.database 只能包含字母、数字和下划线");
        if (tab == null || !IDENT.matcher(tab).matches()) throw new IllegalArgumentException("mysql.table 只能包含字母、数字和下划线");
        table = '`' + tab + '`';
        user = req(c, "username");
        pass = c.getString("password", "");
        timeout = Math.max(1, c.getInt("connect-timeout-seconds", 10));
        String q = "useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                + "&connectTimeout=" + timeout * 1000
                + "&socketTimeout=" + Math.max(1, c.getInt("socket-timeout-seconds", 15)) * 1000
                + "&useSSL=" + c.getBoolean("ssl", true)
                + "&allowPublicKeyRetrieval=" + c.getBoolean("allow-public-key-retrieval", false);
        url = "jdbc:mysql://" + host + ':' + port + '/' + base + '?' + q;
    }

    private static String req(ConfigurationSection c, String k) {
        String s = c.getString(k);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("缺少配置 mysql." + k);
        return s;
    }

    private synchronized Connection conn() throws Exception {
        if (con != null && !con.isClosed() && con.isValid(2)) return con;
        if (con != null) con.close();
        var p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pass);
        p.setProperty("loginTimeout", String.valueOf(timeout));
        con = DriverManager.getConnection(url, p);
        return con;
    }

    @Override
    public void init() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (var s = conn().createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "player_uuid CHAR(36) NOT NULL PRIMARY KEY,"
                    + "inventory LONGBLOB NOT NULL,"
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    @Override
    public synchronized byte[] load(UUID id) throws Exception {
        try (var s = conn().prepareStatement("SELECT inventory FROM " + table + " WHERE player_uuid=?")) {
            s.setString(1, id.toString());
            try (var rs = s.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    @Override
    public synchronized void save(UUID id, byte[] data) throws Exception {
        String sql = "INSERT INTO " + table + " (player_uuid,inventory) VALUES (?,?) "
                + "ON DUPLICATE KEY UPDATE inventory=VALUES(inventory)";
        try (PreparedStatement s = conn().prepareStatement(sql)) {
            s.setString(1, id.toString());
            s.setBytes(2, data);
            s.executeUpdate();
        }
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public synchronized void close() throws Exception {
        if (con != null) con.close();
        con = null;
    }
}
