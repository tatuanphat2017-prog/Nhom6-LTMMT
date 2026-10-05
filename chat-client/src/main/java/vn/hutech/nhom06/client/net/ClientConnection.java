package vn.hutech.nhom06.client.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.Protocol;

/**
 * Ket noi TCP tu Client den Server chat.
 *
 * Cach dung (phia giao dien):
 *     ClientConnection conn = new ClientConnection();
 *     conn.setListener(this);              // this implements ClientEventListener
 *     conn.connect("localhost", 5000);     // nem IOException neu khong ket noi duoc
 *     conn.login("Lang");                  // ket qua ve qua onLoginOk / onError
 *     conn.join("Phong chung");            // -> onJoined, onHistoryItem..., onHistoryEnd, onMembers
 *     conn.sendMessage("xin chao");        // -> onMessage (minh cung nhan lai)
 *     conn.logout();                       // -> onDisconnected
 *
 * Cac ham gui (login, join, sendMessage...) KHONG cho ket qua: chung chi gui goi tin di.
 * Ket qua do server tra ve se den qua {@link ClientEventListener}, tren THREAD NHAN TIN.
 *
 * Mot doi tuong chi dung cho mot lan ket noi. Muon ket noi lai thi tao doi tuong moi.
 *
 * @author Tran Lang
 */
public class ClientConnection {

    /** Thoi gian toi da cho ket noi (ms), tranh treo giao dien khi sai IP. */
    public static final int CONNECT_TIMEOUT_MS = 5000;

    private static final ClientEventListener NO_LISTENER = new ClientEventAdapter();

    private final Object writeLock = new Object();
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);

    private volatile ClientEventListener listener = NO_LISTENER;
    private volatile Socket socket;
    private volatile BufferedWriter out;
    private volatile String username;       // null = chua dang nhap
    private volatile String currentRoom;    // null = chua vao phong nao
    private volatile String closeReason;    // ly do dong ket noi (neu biet)

    // ===================== Cau hinh =====================

    /** Dang ky noi nhan su kien. Co the doi listener bat ky luc nao (vd LoginFrame -> MainFrame). */
    public void setListener(ClientEventListener listener) {
        this.listener = listener == null ? NO_LISTENER : listener;
    }

    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    /** Ten da dang nhap, hoac null neu chua dang nhap. */
    public String getUsername() {
        return username;
    }

    /** Phong dang o, hoac null neu chua vao phong nao. */
    public String getCurrentRoom() {
        return currentRoom;
    }

    // ===================== Ket noi =====================

    /**
     * Mo ket noi den server va khoi dong thread nhan tin.
     *
     * @throws IOException neu khong ket noi duoc (sai IP/cong, server chua bat, het thoi gian cho)
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (socket != null) {
            throw new IllegalStateException("Doi tuong nay da ket noi roi, hay tao ClientConnection moi");
        }
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            socket = s;

            Thread reader = new Thread(() -> readLoop(in), "client-reader");
            reader.setDaemon(true);   // khong giu chuong trinh song khi da dong cua so
            reader.start();
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /** Dong ket noi ngay (khong gui LOGOUT). Goi nhieu lan cung khong sao. */
    public void close() {
        disconnect("Da dong ket noi");
    }

    // ===================== Lenh gui len server =====================

    public void login(String name) {
        send(Protocol.build(Protocol.LOGIN, name == null ? "" : name.trim()));
    }

    public void listRooms() {
        send(Protocol.build(Protocol.LIST_ROOMS));
    }

    public void createRoom(String room) {
        send(Protocol.build(Protocol.CREATE_ROOM, room == null ? "" : room.trim()));
    }

    /** Vao phong. Neu dang o phong khac, server tu cho roi phong cu. */
    public void join(String room) {
        send(Protocol.build(Protocol.JOIN, room == null ? "" : room.trim()));
    }

    /** Roi phong dang o. */
    public void leave() {
        String room = currentRoom;
        if (room == null) {
            listener.onError(Protocol.ERR_NOT_IN_ROOM, "Ban chua vao phong nao");
            return;
        }
        send(Protocol.build(Protocol.LEAVE, room));
    }

    /** Gui tin nhan vao phong dang o. */
    public void sendMessage(String content) {
        String room = currentRoom;
        if (room == null) {
            listener.onError(Protocol.ERR_NOT_IN_ROOM, "Ban chua vao phong nao");
            return;
        }
        send(Protocol.build(Protocol.MSG, room, content == null ? "" : content));
    }

    /** Xin lai lich su phong dang o (limit: 1..200). Ket qua: onHistoryItem..., onHistoryEnd. */
    public void requestHistory(int limit) {
        String room = currentRoom;
        if (room != null) {
            send(Protocol.build(Protocol.HISTORY, room, limit));
        }
    }

    /** Xin lai danh sach thanh vien phong dang o. Ket qua: onMembers. */
    public void requestMembers() {
        String room = currentRoom;
        if (room != null) {
            send(Protocol.build(Protocol.MEMBERS, room));
        }
    }

    /** Dang xuat: server tra BYE roi dong ket noi -> onDisconnected. */
    public void logout() {
        if (!isConnected()) {
            return;
        }
        closeReason = "Da dang xuat";
        send(Protocol.LOGOUT);
    }

    /** Gui 1 dong len server. Nhieu thread goi cung luc van an toan. */
    private void send(String line) {
        BufferedWriter w = out;
        if (w == null || !isConnected()) {
            listener.onError("NOT_CONNECTED", "Chua ket noi den server");
            return;
        }
        try {
            synchronized (writeLock) {
                w.write(line);
                w.write('\n');
                w.flush();
            }
        } catch (IOException e) {
            disconnect("Mat ket noi voi server");
        }
    }

    // ===================== Thread nhan tin =====================

    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    dispatch(Protocol.parse(line));
                } catch (RuntimeException e) {
                    // Goi tin loi hoac giao dien nem loi: bo qua goi nay, KHONG de thread nhan tin chet
                    System.err.println("[ClientConnection] Loi khi xu ly goi tin: " + line);
                    e.printStackTrace();
                }
            }
        } catch (IOException ignored) {
            // socket bi dong hoac rot mang
        }
        disconnect("Mat ket noi voi server");
    }

    /** Tach goi tin da parse thanh su kien cho giao dien. p[0] = lenh, p[1..] = truong. */
    private void dispatch(String[] p) {
        ClientEventListener l = listener;
        switch (p[0]) {
            case Protocol.LOGIN_OK -> {
                username = field(p, 1);
                l.onLoginOk(username);
            }
            case Protocol.ROOM_LIST -> l.onRoomList(Protocol.parseRoomList(p));
            case Protocol.ROOM_CREATED -> l.onRoomCreated(field(p, 1));
            case Protocol.JOINED -> {
                currentRoom = field(p, 1);
                l.onJoined(currentRoom);
            }
            case Protocol.HISTORY_ITEM -> l.onHistoryItem(toMessage(p));
            case Protocol.HISTORY_END -> l.onHistoryEnd(field(p, 1), toInt(field(p, 2)));
            case Protocol.MSG -> l.onMessage(toMessage(p));
            case Protocol.MEMBERS -> {
                List<String> members = new ArrayList<>();
                for (int i = 2; i < p.length; i++) {
                    if (!p[i].isEmpty()) {
                        members.add(p[i]);
                    }
                }
                l.onMembers(field(p, 1), members);
            }
            case Protocol.USER_JOINED -> l.onUserJoined(field(p, 1), field(p, 2));
            case Protocol.USER_LEFT -> l.onUserLeft(field(p, 1), field(p, 2));
            case Protocol.LEFT -> {
                String room = field(p, 1);
                if (room.equalsIgnoreCase(currentRoom)) {
                    currentRoom = null;
                }
                l.onLeft(room);
            }
            case Protocol.ERROR -> l.onError(field(p, 1), field(p, 2));
            case Protocol.BYE -> {
                // Server sap dong ket noi; onDisconnected se duoc goi khi socket dong han
                if (closeReason == null) {
                    closeReason = field(p, 1);
                }
            }
            default -> System.err.println("[ClientConnection] Goi tin la: " + String.join("|", p));
        }
    }

    /** MSG / HISTORY_ITEM: LENH|phong|nguoiGui|thoiGian|noiDung */
    private static ChatMessage toMessage(String[] p) {
        if (p.length < 5) {
            throw new IllegalArgumentException("Goi tin thieu truong");
        }
        return new ChatMessage(p[1], p[2], Long.parseLong(p[3]), p[4]);
    }

    private static String field(String[] p, int index) {
        return index < p.length ? p[index] : "";
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Dong socket va bao onDisconnected dung 1 lan, du duoc goi tu thread nao. */
    private void disconnect(String defaultReason) {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        if (s != null && disconnectNotified.compareAndSet(false, true)) {
            String reason = closeReason != null && !closeReason.isEmpty() ? closeReason : defaultReason;
            username = null;
            currentRoom = null;
            try {
                listener.onDisconnected(reason);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
