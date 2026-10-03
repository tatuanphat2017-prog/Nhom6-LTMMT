package vn.hutech.nhom06.server.store;

import java.util.List;
import vn.hutech.nhom06.common.ChatMessage;

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

    /** Goi khi tao phong moi. */
    void saveRoom(String roomName, String createdBy);

    /** Luu mot tin nhan. */
    void saveMessage(ChatMessage message);

    /**
     * Lay toi da {@code limit} tin nhan MOI NHAT cua phong,
     * tra ve theo thu tu thoi gian tang dan (cu -> moi).
     */
    List<ChatMessage> loadHistory(String roomName, int limit);

    /** Giai phong tai nguyen khi server dung. */
    default void close() {
    }
}
