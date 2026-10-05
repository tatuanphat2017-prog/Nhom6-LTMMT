package vn.hutech.nhom06.client.net;

import java.util.List;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Cac su kien ma tang mang (ClientConnection) bao len cho giao dien.
 *
 * HUY: cho MainFrame / LoginFrame "implements ClientEventListener"
 * (hoac "extends ClientEventAdapter" neu chi can vai ham), roi goi
 * connection.setListener(this).
 *
 * !!! QUAN TRONG: tat ca cac ham duoi day duoc goi tu THREAD NHAN TIN,
 * KHONG phai thread giao dien. Moi lenh cap nhat Swing phai boc trong
 *     SwingUtilities.invokeLater(() -> { ... });
 *
 * Thu tu su kien khi vao phong thanh cong (xem docs/protocol.md):
 *     onJoined -> onHistoryItem (0..n lan, cu -> moi) -> onHistoryEnd -> onMembers
 *     -> onRoomList (so nguoi moi)
 *
 * @author Tran Lang
 */
public interface ClientEventListener {

    /** Dang nhap thanh cong. Ngay sau do se co onRoomList. */
    void onLoginOk(String username);

    /** Danh sach phong + so nguoi. Server tu gui lai moi khi co thay doi. */
    void onRoomList(List<RoomInfo> rooms);

    /** Minh da vao phong. Nen xoa khung chat cu de chuan bi hien lich su. */
    void onJoined(String room);

    /** Mot tin nhan trong lich su (goi lan luot tu cu den moi). */
    void onHistoryItem(ChatMessage message);

    /** Da gui het lich su cua phong; count = so tin lich su vua gui. */
    void onHistoryEnd(String room, int count);

    /** Tin nhan moi trong phong (ca tin do chinh minh gui cung nhan lai o day). */
    void onMessage(ChatMessage message);

    /** Danh sach thanh vien dang o trong phong. */
    void onMembers(String room, List<String> members);

    /** Co nguoi khac vao phong minh dang o. */
    void onUserJoined(String room, String username);

    /** Co nguoi roi phong / mat ket noi. */
    void onUserLeft(String room, String username);

    /** Minh da roi phong. */
    void onLeft(String room);

    /**
     * Server bao loi. code la ma loi trong Protocol (vd Protocol.ERR_NAME_TAKEN),
     * message la mo ta de hien cho nguoi dung.
     */
    void onError(String code, String message);

    /**
     * Ket noi da dong (dang xuat, server dung, rot mang...). Chi goi DUNG 1 LAN.
     * reason: ly do de hien cho nguoi dung.
     */
    void onDisconnected(String reason);

    /**
     * Tao phong thanh cong (goi tin ROOM_CREATED). Khong bat buoc xu ly
     * vi ngay sau do onRoomList se co phong moi.
     */
    default void onRoomCreated(String room) {
    }
}
