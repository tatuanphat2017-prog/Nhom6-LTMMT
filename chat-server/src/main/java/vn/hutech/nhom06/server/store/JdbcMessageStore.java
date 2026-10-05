package vn.hutech.nhom06.server.store;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import vn.hutech.nhom06.common.ChatMessage;

/**
 * Luu tru vao SQL Server qua JDBC (CSDL ChatNhom06, xem database/schema.sql).
 *
 * Bang su dung:
 *   Users(UserId, Username, CreatedAt)
 *   Rooms(RoomId, RoomName, CreatedBy, CreatedAt)
 *   Messages(MessageId, RoomId, Sender, SentAt, Content)
 *
 * An toan da luong: lop nay KHONG giu Connection dung chung. Moi lan goi ham
 * deu mo 1 Connection rieng (try-with-resources tu dong dong), nen nhieu thread
 * client goi cung luc khong anh huong nhau.
 *
 * Moi cau SQL deu dung PreparedStatement (tham so "?"), khong noi chuoi,
 * de tranh SQL Injection va luu dung tieng Viet co dau (setNString).
 *
 * Khi loi CSDL, cac ham nem RuntimeException; ChatServer da bat va ghi log.
 *
 * @author Tran Lang
 */
public class JdbcMessageStore implements MessageStore {

    /** Ten file cau hinh (khong day len GitHub, xem db.properties.example). */
    public static final String CONFIG_FILE = "db.properties";

    // Ma loi SQL Server khi vi pham UNIQUE / khoa chinh. Xay ra khi 2 thread cung them
    // 1 ten cung luc: ca hai deu qua duoc NOT EXISTS, thread sau bi UNIQUE chan -> bo qua.
    private static final int ERR_DUPLICATE_KEY = 2627;
    private static final int ERR_DUPLICATE_INDEX = 2601;

    private static final String SQL_LOAD_ROOMS =
            "SELECT RoomName FROM Rooms ORDER BY RoomId";

    // Chi them neu chua co (so sanh ten theo collation cua cot: khong phan biet hoa/thuong)
    private static final String SQL_SAVE_USER =
            "INSERT INTO Users (Username) "
            + "SELECT ? WHERE NOT EXISTS (SELECT 1 FROM Users WHERE Username = ?)";

    private static final String SQL_SAVE_ROOM =
            "INSERT INTO Rooms (RoomName, CreatedBy) "
            + "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM Rooms WHERE RoomName = ?)";

    // Tim RoomId theo ten phong ngay trong cau INSERT (khong can truy van 2 lan)
    private static final String SQL_SAVE_MESSAGE =
            "INSERT INTO Messages (RoomId, Sender, SentAt, Content) "
            + "SELECT RoomId, ?, ?, ? FROM Rooms WHERE RoomName = ?";

    // Lay limit tin MOI NHAT (MessageId giam dan), sau do dao lai trong Java thanh cu -> moi
    private static final String SQL_LOAD_HISTORY =
            "SELECT TOP (?) r.RoomName, m.Sender, m.SentAt, m.Content "
            + "FROM Messages m JOIN Rooms r ON r.RoomId = m.RoomId "
            + "WHERE r.RoomName = ? "
            + "ORDER BY m.MessageId DESC";

    private final String url;
    private final String user;
    private final String password;

    /** Doc cau hinh tu file db.properties va kiem tra ket noi. */
    public JdbcMessageStore() {
        this(loadConfig());
    }

    private JdbcMessageStore(Properties config) {
        this(require(config, "url"), config.getProperty("user", "").trim(),
                config.getProperty("password", ""));
    }

    /**
     * @throws IllegalStateException neu khong ket noi duoc CSDL
     */
    public JdbcMessageStore(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        // Thu ket noi ngay tu dau de bao loi som (sai mat khau, chua bat SQL Server...)
        try {
            open().close();
        } catch (SQLException e) {
            throw new IllegalStateException("Khong ket noi duoc CSDL: " + e.getMessage(), e);
        }
    }

    // ===================== 5 ham cua MessageStore =====================

    @Override
    public List<String> loadRoomNames() {
        List<String> names = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_LOAD_ROOMS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getNString(1));
            }
            return names;
        } catch (SQLException e) {
            throw error("nap danh sach phong", e);
        }
    }

    @Override
    public void saveUser(String username) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_SAVE_USER)) {
            ps.setNString(1, username);
            ps.setNString(2, username);
            ps.executeUpdate();     // 0 dong = da co san, khong sao
        } catch (SQLException e) {
            if (!isDuplicate(e)) {
                throw error("luu nguoi dung", e);
            }
        }
    }

    @Override
    public void saveRoom(String roomName, String createdBy) {
        try (Connection c = open()) {
            insertRoom(c, roomName, createdBy);
        } catch (SQLException e) {
            throw error("luu phong", e);
        }
    }

    @Override
    public void saveMessage(ChatMessage message) {
        try (Connection c = open()) {
            if (insertMessage(c, message) == 0) {
                // Phong chua co trong CSDL (vd lan luu phong truoc do bi loi): them phong roi luu lai
                insertRoom(c, message.room(), message.sender());
                if (insertMessage(c, message) == 0) {
                    throw new SQLException("Khong tim thay phong '" + message.room() + "'");
                }
            }
        } catch (SQLException e) {
            throw error("luu tin nhan", e);
        }
    }

    @Override
    public List<ChatMessage> loadHistory(String roomName, int limit) {
        List<ChatMessage> list = new ArrayList<>();
        if (limit <= 0) {
            return list;
        }
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_LOAD_HISTORY)) {
            ps.setInt(1, limit);
            ps.setNString(2, roomName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ChatMessage(rs.getNString(1), rs.getNString(2),
                            rs.getLong(3), rs.getNString(4)));
                }
            }
        } catch (SQLException e) {
            throw error("doc lich su", e);
        }
        Collections.reverse(list);   // moi -> cu  thanh  cu -> moi
        return list;
    }

    // ===================== Ham phu =====================

    /** Mo 1 Connection moi. Noi goi phai dong (dung try-with-resources). */
    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void insertRoom(Connection c, String roomName, String createdBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SQL_SAVE_ROOM)) {
            ps.setNString(1, roomName);
            ps.setNString(2, createdBy);
            ps.setNString(3, roomName);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (!isDuplicate(e)) {
                throw e;
            }
        }
    }

    private int insertMessage(Connection c, ChatMessage m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SQL_SAVE_MESSAGE)) {
            ps.setNString(1, m.sender());
            ps.setLong(2, m.timestamp());
            ps.setNString(3, m.content());
            ps.setNString(4, m.room());
            return ps.executeUpdate();
        }
    }

    private static boolean isDuplicate(SQLException e) {
        // 2627 / 2601: ma cua SQL Server; 23505: ma chuan SQL cho "trung khoa duy nhat"
        return e.getErrorCode() == ERR_DUPLICATE_KEY || e.getErrorCode() == ERR_DUPLICATE_INDEX
                || "23505".equals(e.getSQLState());
    }

    private static RuntimeException error(String action, SQLException e) {
        return new RuntimeException("Loi CSDL khi " + action + ": " + e.getMessage(), e);
    }

    private static String require(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("File " + CONFIG_FILE + " thieu dong '" + key + "=...'");
        }
        return value.trim();
    }

    /**
     * Tim db.properties theo thu tu:
     *   1. duong dan trong tham so JVM  -Ddb.config=...
     *   2. thu muc dang chay            ./db.properties
     *   3. thu muc cha                  ../db.properties  (khi NetBeans chay trong chat-server/)
     *   4. thu muc con                  ./chat-server/db.properties
     */
    private static Properties loadConfig() {
        List<Path> candidates = new ArrayList<>();
        String custom = System.getProperty("db.config");
        if (custom != null && !custom.isBlank()) {
            candidates.add(Path.of(custom));
        }
        candidates.add(Path.of(CONFIG_FILE));
        candidates.add(Path.of("..", CONFIG_FILE));
        candidates.add(Path.of("chat-server", CONFIG_FILE));

        for (Path path : candidates) {
            if (Files.isRegularFile(path)) {
                Properties config = new Properties();
                try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                    config.load(reader);
                    return config;
                } catch (IOException e) {
                    throw new IllegalStateException("Khong doc duoc " + path.toAbsolutePath() + ": " + e.getMessage(), e);
                }
            }
        }
        throw new IllegalStateException("Khong tim thay " + CONFIG_FILE
                + " (copy db.properties.example thanh db.properties roi sua mat khau)");
    }
}
