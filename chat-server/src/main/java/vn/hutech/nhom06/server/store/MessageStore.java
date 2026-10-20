package vn.hutech.nhom06.server.store;

import java.util.List;
import java.util.Map;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.PrivateMessage;

/**
 * "Cong noi" luu tru du lieu cua server.
 *
 * Hien tai server dung {@link InMemoryMessageStore} (luu tam trong RAM).
 * LANG se viet lop JdbcMessageStore implements MessageStore de luu vao SQL Server,
 * sau do chi can doi 1 dong trong ServerMain:
 *     new ChatServer(port, new JdbcMessageStore(...))
 *
 * LUU Y: cac ham duoc goi dong thoi tu nhieu thread (moi client 1 thread),
 * nen lop cai dat phai an toan da luong (vd: moi lan goi mo 1 Connection rieng).
 *
 * @author Ta Tuan Phat
 */
public interface MessageStore {

    /** Ten cac phong da luu, dung de nap lai khi server khoi dong. */
    List<String> loadRoomNames();

    /** Goi khi mot nguoi dang nhap thanh cong (them vao bang Users neu chua co). */
    void saveUser(String username);

    /** Tai khoan da dang ky: ten luu trong CSDL (dung hoa/thuong goc) + mat khau da bam. */
    record Account(String username, String passwordHash, String passwordSalt) {
    }

    /**
     * Tim tai khoan theo ten (khong phan biet hoa/thuong).
     * Tra ve null neu chua dang ky (khong co ten, hoac ten cu chua dat mat khau).
     */
    Account findAccount(String username);

    /**
     * Dang ky tai khoan. Ten cu da co trong bang Users nhung chua co mat khau (tu ban truoc)
     * thi duoc dat mat khau cho ten do.
     *
     * @return false neu ten da co nguoi dang ky
     */
    boolean createAccount(String username, String passwordHash, String passwordSalt);

    /** Ten tat ca tai khoan da dang ky (co mat khau), sap xep A-Z. */
    List<String> loadAccountNames();

    /** Goi khi tao phong moi. */
    void saveRoom(String roomName, String createdBy);

    /** Luu mot tin nhan. */
    void saveMessage(ChatMessage message);

    /**
     * Lay toi da {@code limit} tin nhan MOI NHAT cua phong,
     * tra ve theo thu tu thoi gian tang dan (cu -> moi).
     */
    List<ChatMessage> loadHistory(String roomName, int limit);

    /** Luu mot tin nhan rieng (tin chu hoac tin file). */
    void savePrivateMessage(PrivateMessage message);

    /**
     * Lay toi da {@code limit} tin rieng MOI NHAT giua 2 nguoi (khong phan biet ai gui),
     * tra ve theo thu tu thoi gian tang dan (cu -> moi).
     */
    List<PrivateMessage> loadPrivateHistory(String userA, String userB, int limit);

    /**
     * So tin rieng CHUA DOC gui toi {@code receiver}, theo tung nguoi gui
     * (vd {"Huy": 3, "Lang": 1}). Tin moi luu luon la chua doc.
     */
    Map<String, Integer> countUnread(String receiver);

    /** Danh dau da doc moi tin {@code sender} gui cho {@code receiver} (khi receiver mo khung chat). */
    void markRead(String receiver, String sender);

    /** Giai phong tai nguyen khi server dung. */
    default void close() {
    }
}
