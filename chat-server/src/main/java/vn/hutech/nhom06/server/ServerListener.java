package vn.hutech.nhom06.server;

import java.util.List;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * "Cong noi" cho giao dien quan tri server (phan cua BAO).
 *
 * Cach dung:
 *     server.addListener(new ServerListener() {
 *         public void onLog(String msg) {
 *             SwingUtilities.invokeLater(() -> txtLog.append(msg + "\n"));
 *         }
 *     });
 *
 * LUU Y: cac ham nay duoc goi tu thread mang, KHONG phai thread giao dien.
 * Muon cap nhat Swing phai boc trong SwingUtilities.invokeLater(...).
 * Chi can override nhung ham can dung (cac ham deu co than mac dinh rong).
 *
 * @author Ta Tuan Phat
 */
public interface ServerListener {

    default void onServerStarted(int port) {
    }

    default void onServerStopped() {
    }

    /** Mot dong nhat ky (da kem gio). */
    default void onLog(String message) {
    }

    /** Co ket noi TCP moi (chua dang nhap). address dang "ip:port". */
    default void onClientConnected(String address) {
    }

    /** Ket noi bi dong. username = null neu chua dang nhap. */
    default void onClientDisconnected(String address, String username) {
    }

    default void onUserLoggedIn(String username, String address) {
    }

    /** Danh sach phong hoac so nguoi trong phong thay doi. */
    default void onRoomsChanged(List<RoomInfo> rooms) {
    }
}
