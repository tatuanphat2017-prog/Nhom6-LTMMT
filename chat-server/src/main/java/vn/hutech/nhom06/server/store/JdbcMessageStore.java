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
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Properties;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.FileInfo;
import vn.hutech.nhom06.common.PrivateMessage;

/**
 * Luu tru vao SQL Server qua JDBC (CSDL ChatNhom06, xem database/schema.sql).
 *
 * Bang su dung:
 *   Users(UserId, Username, CreatedAt, PasswordHash, PasswordSalt)
 *   (mat khau KHONG luu truc tiep: chi luu ket qua bam PBKDF2 + salt, xem PasswordHasher)
 *   Rooms(RoomId, RoomName, CreatedBy, CreatedAt)
 *   Messages(MessageId, RoomId, Sender, SentAt, Content, FileId, FileName, FileSize)
 *   (3 cot File* = NULL voi tin chu; tin gui file thi Content = "[File] ten file".
 *    Noi dung file nam trong thu muc uploads/ cua server, ten file = FileId.)
 *   PrivateMessages(PmId, Sender, Receiver, SentAt, Content, FileId, FileName, FileSize, IsRead)
 *   (tin nhan rieng giua 2 nguoi; IsRead = 0 khi nguoi nhan chua mo khung chat -> hien so do)
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
            "INSERT INTO Messages (RoomId, Sender, SentAt, Content, FileId, FileName, FileSize) "
            + "SELECT RoomId, ?, ?, ?, ?, ?, ? FROM Rooms WHERE RoomName = ?";

    // CSDL tao tu ban schema.sql cu chua co 3 cot file -> tu them (chi chay 1 lan)
    private static final String SQL_ADD_FILE_COLUMNS =
            "IF COL_LENGTH('dbo.Messages', 'FileId') IS NULL "
            + "ALTER TABLE dbo.Messages ADD FileId CHAR(32) NULL, FileName NVARCHAR(255) NULL, FileSize BIGINT NULL";

    // CSDL cu chua co cot mat khau -> tu them (ten cu chua co mat khau thi duoc dang ky lai)
    private static final String SQL_ADD_PASSWORD_COLUMNS =
            "IF COL_LENGTH('dbo.Users', 'PasswordHash') IS NULL "
            + "ALTER TABLE dbo.Users ADD PasswordHash VARCHAR(64) NULL, PasswordSalt VARCHAR(32) NULL";

    private static final String SQL_FIND_ACCOUNT =
            "SELECT Username, PasswordHash, PasswordSalt FROM Users WHERE Username = ?";

    // Ten cu (chua co mat khau) -> dat mat khau
    private static final String SQL_CLAIM_ACCOUNT =
            "UPDATE Users SET PasswordHash = ?, PasswordSalt = ? WHERE Username = ? AND PasswordHash IS NULL";

    private static final String SQL_INSERT_ACCOUNT =
            "INSERT INTO Users (Username, PasswordHash, PasswordSalt) "
            + "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM Users WHERE Username = ?)";

    // CSDL cu chua co bang tin rieng -> tu tao (chi chay 1 lan)
    private static final String SQL_CREATE_PRIVATE_TABLE =
            "IF OBJECT_ID(N'dbo.PrivateMessages', N'U') IS NULL "
            + "BEGIN "
            + "CREATE TABLE dbo.PrivateMessages ("
            + " PmId BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_PrivateMessages PRIMARY KEY,"
            + " Sender NVARCHAR(20) NOT NULL, Receiver NVARCHAR(20) NOT NULL,"
            + " SentAt BIGINT NOT NULL, Content NVARCHAR(1000) NOT NULL,"
            + " FileId CHAR(32) NULL, FileName NVARCHAR(255) NULL, FileSize BIGINT NULL); "
            + "CREATE INDEX IX_PrivateMessages_Pair ON dbo.PrivateMessages (Sender, Receiver, PmId); "
            + "END";

    // CSDL cu chua co cot da doc -> them; tin cu coi nhu da doc (khong hien so do bat ngo)
    private static final String SQL_ADD_ISREAD_COLUMN =
            "IF COL_LENGTH('dbo.PrivateMessages', 'IsRead') IS NULL "
            + "BEGIN "
            + "ALTER TABLE dbo.PrivateMessages ADD IsRead BIT NOT NULL "
            + "CONSTRAINT DF_PrivateMessages_IsRead DEFAULT 0; "
            + "EXEC('UPDATE dbo.PrivateMessages SET IsRead = 1'); "
            + "END";

    private static final String SQL_COUNT_UNREAD =
            "SELECT Sender, COUNT(*) FROM PrivateMessages "
            + "WHERE Receiver = ? AND IsRead = 0 GROUP BY Sender";

    private static final String SQL_MARK_READ =
            "UPDATE PrivateMessages SET IsRead = 1 WHERE Receiver = ? AND Sender = ? AND IsRead = 0";

    private static final String SQL_LOAD_ACCOUNTS =
            "SELECT Username FROM Users WHERE PasswordHash IS NOT NULL ORDER BY Username";

    private static final String SQL_SAVE_PRIVATE =
            "INSERT INTO PrivateMessages (Sender, Receiver, SentAt, Content, FileId, FileName, FileSize) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    // Tin giua A va B theo ca 2 chieu; lay limit tin moi nhat roi dao lai trong Java
    private static final String SQL_LOAD_PRIVATE =
            "SELECT TOP (?) Sender, Receiver, SentAt, Content, FileId, FileName, FileSize "
            + "FROM PrivateMessages "
            + "WHERE (Sender = ? AND Receiver = ?) OR (Sender = ? AND Receiver = ?) "
            + "ORDER BY PmId DESC";

    // Lay limit tin MOI NHAT (MessageId giam dan), sau do dao lai trong Java thanh cu -> moi
    private static final String SQL_LOAD_HISTORY =
            "SELECT TOP (?) r.RoomName, m.Sender, m.SentAt, m.Content, m.FileId, m.FileName, m.FileSize "
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
        upgradeSchema();
    }

    /**
     * CSDL tao tu schema.sql ban cu: tu them 3 cot file vao Messages va tao bang PrivateMessages.
     * Lenh co IF ... IS NULL nen chay moi lan khoi dong cung khong sao, khong mat du lieu.
     */
    private void upgradeSchema() {
        try (Connection c = open();
             Statement st = c.createStatement()) {
            st.execute(SQL_ADD_FILE_COLUMNS);
            st.execute(SQL_CREATE_PRIVATE_TABLE);
            st.execute(SQL_ADD_ISREAD_COLUMN);
            st.execute(SQL_ADD_PASSWORD_COLUMNS);
        } catch (SQLException e) {
            throw new IllegalStateException("CSDL chua duoc nang cap va tai khoan khong du quyen tu nang cap. "
                    + "Hay mo SSMS chay file database/nang-cap-csdl.sql. Chi tiet: " + e.getMessage(), e);
        }
    }

    // ===================== Cac ham cua MessageStore =====================

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
    public Account findAccount(String username) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_FIND_ACCOUNT)) {
            ps.setNString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(2) != null) {
                    return new Account(rs.getNString(1), rs.getString(2), rs.getString(3));
                }
                return null;
            }
        } catch (SQLException e) {
            throw error("tim tai khoan", e);
        }
    }

    @Override
    public boolean createAccount(String username, String passwordHash, String passwordSalt) {
        try (Connection c = open()) {
            try (PreparedStatement ps = c.prepareStatement(SQL_CLAIM_ACCOUNT)) {
                ps.setString(1, passwordHash);
                ps.setString(2, passwordSalt);
                ps.setNString(3, username);
                if (ps.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(SQL_INSERT_ACCOUNT)) {
                ps.setNString(1, username);
                ps.setString(2, passwordHash);
                ps.setString(3, passwordSalt);
                ps.setNString(4, username);
                return ps.executeUpdate() > 0;   // 0 = ten da co nguoi dang ky
            }
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                return false;   // 2 nguoi dang ky cung ten cung luc
            }
            throw error("dang ky tai khoan", e);
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
                    String fileId = rs.getString(5);
                    FileInfo file = fileId == null ? null
                            : new FileInfo(fileId.trim(), rs.getNString(6), rs.getLong(7));
                    list.add(new ChatMessage(rs.getNString(1), rs.getNString(2),
                            rs.getLong(3), rs.getNString(4), file));
                }
            }
        } catch (SQLException e) {
            throw error("doc lich su", e);
        }
        Collections.reverse(list);   // moi -> cu  thanh  cu -> moi
        return list;
    }

    @Override
    public void savePrivateMessage(PrivateMessage m) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_SAVE_PRIVATE)) {
            ps.setNString(1, m.sender());
            ps.setNString(2, m.receiver());
            ps.setLong(3, m.timestamp());
            String content = m.content();
            ps.setNString(4, content.length() > 1000 ? content.substring(0, 1000) : content);
            setFile(ps, 5, m.file());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw error("luu tin rieng", e);
        }
    }

    @Override
    public List<PrivateMessage> loadPrivateHistory(String userA, String userB, int limit) {
        List<PrivateMessage> list = new ArrayList<>();
        if (limit <= 0) {
            return list;
        }
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_LOAD_PRIVATE)) {
            ps.setInt(1, limit);
            ps.setNString(2, userA);
            ps.setNString(3, userB);
            ps.setNString(4, userB);
            ps.setNString(5, userA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String fileId = rs.getString(5);
                    FileInfo file = fileId == null ? null
                            : new FileInfo(fileId.trim(), rs.getNString(6), rs.getLong(7));
                    list.add(new PrivateMessage(rs.getNString(1), rs.getNString(2),
                            rs.getLong(3), rs.getNString(4), file));
                }
            }
        } catch (SQLException e) {
            throw error("doc tin rieng", e);
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public Map<String, Integer> countUnread(String receiver) {
        Map<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_COUNT_UNREAD)) {
            ps.setNString(1, receiver);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.merge(rs.getNString(1), rs.getInt(2), Integer::sum);
                }
            }
            return map;
        } catch (SQLException e) {
            throw error("dem tin chua doc", e);
        }
    }

    @Override
    public void markRead(String receiver, String sender) {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_MARK_READ)) {
            ps.setNString(1, receiver);
            ps.setNString(2, sender);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw error("danh dau da doc", e);
        }
    }

    @Override
    public List<String> loadAccountNames() {
        List<String> names = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(SQL_LOAD_ACCOUNTS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                names.add(rs.getNString(1));
            }
            return names;
        } catch (SQLException e) {
            throw error("doc danh sach tai khoan", e);
        }
    }

    // ===================== Ham phu =====================

    /** Ghi 3 cot FileId, FileName, FileSize bat dau tu vi tri {@code index} (NULL neu khong co file). */
    private static void setFile(PreparedStatement ps, int index, FileInfo f) throws SQLException {
        if (f != null) {
            ps.setString(index, f.id());
            ps.setNString(index + 1, f.name());
            ps.setLong(index + 2, f.size());
        } else {
            ps.setNull(index, Types.CHAR);
            ps.setNull(index + 1, Types.NVARCHAR);
            ps.setNull(index + 2, Types.BIGINT);
        }
    }

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
            String content = m.content();
            ps.setNString(3, content.length() > 1000 ? content.substring(0, 1000) : content);
            setFile(ps, 4, m.file());
            ps.setNString(7, m.room());
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
