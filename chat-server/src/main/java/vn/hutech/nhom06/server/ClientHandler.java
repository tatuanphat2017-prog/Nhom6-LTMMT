package vn.hutech.nhom06.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.Protocol;

/**
 * Phuc vu MOT client tren MOT thread rieng.
 * Vong lap: doc 1 dong -> tach lenh -> xu ly -> tra ket qua.
 *
 * Quy uoc: moi nguoi chi o trong 1 phong tai mot thoi diem.
 * JOIN phong moi se tu dong roi phong cu.
 *
 * Bao ve server:
 *  - Doc toi da MAX_LINE_LENGTH ky tu/dong: client gui khoi du lieu khong lo
 *    (vd 1-10GB) se bi ngat ngay, server khong bi tran bo nho.
 *  - Moi client co 1 hang doi gui + 1 thread ghi rieng: client mang cham / bi treo
 *    khong lam dung ca phong. Hang doi day -> ngat client do.
 *
 * @author Ta Tuan Phat
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final ChatServer server;
    private final String address;
    private final BufferedReader in;
    private final BufferedWriter out;

    /** So goi tin toi da cho gui toi 1 client (vuot qua = client qua cham -> ngat). */
    static final int MAX_PENDING = 5000;
    private static final String POISON = "\u0000__CLOSE__";

    private final BlockingQueue<String> outbox = new LinkedBlockingQueue<>(MAX_PENDING);
    private Thread writer;
    private volatile String username;   // null = chua dang nhap
    private volatile Room currentRoom;  // null = chua vao phong
    private volatile boolean closed;

    ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        String a = String.valueOf(socket.getRemoteSocketAddress());
        this.address = a.startsWith("/") ? a.substring(1) : a;
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public String getUsername() {
        return username;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public void run() {
        writer = new Thread(this::writeLoop, Thread.currentThread().getName() + "-writer");
        writer.setDaemon(true);
        writer.start();
        try {
            String line;
            while (!closed && (line = readLineLimited()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    handle(Protocol.parse(line));
                } catch (RuntimeException e) {
                    server.log("Loi xu ly lenh tu " + who() + ": " + e);
                    sendError(Protocol.ERR_SERVER, "Loi may chu");
                }
            }
        } catch (LineTooLongException e) {
            server.log("Ngat " + who() + ": goi tin vuot " + Protocol.MAX_LINE_LENGTH + " ky tu");
            sendError(Protocol.ERR_BAD_COMMAND, "Goi tin qua lon, ket noi bi dong");
            disconnect("Goi tin qua lon");
        } catch (IOException e) {
            // client mat ket noi dot ngot -> don dep ben duoi
        } finally {
            cleanup();
        }
    }

    /**
     * Doc 1 dong nhung KHONG qua MAX_LINE_LENGTH ky tu.
     * (BufferedReader.readLine() gom het vao RAM du dong dai bao nhieu -> de tran bo nho.)
     */
    private String readLineLimited() throws IOException {
        StringBuilder sb = new StringBuilder(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int len = sb.length();
                if (len > 0 && sb.charAt(len - 1) == '\r') {
                    sb.setLength(len - 1);
                }
                return sb.toString();
            }
            if (sb.length() >= Protocol.MAX_LINE_LENGTH) {
                throw new LineTooLongException();
            }
            sb.append((char) c);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static class LineTooLongException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** Thread ghi: lay goi tin tu hang doi va gui di (cham cung chi anh huong client nay). */
    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.take();
                if (line == POISON) {
                    break;
                }
                out.write(line);
                out.write('\n');
                if (outbox.isEmpty()) {
                    out.flush(); // gom nhieu goi roi flush 1 lan -> nhanh hon
                }
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            // mat ket noi
        } finally {
            closeSocket();
        }
    }

    // ================== Xu ly lenh ==================

    private void handle(String[] p) {
        String cmd = p[0];
        if (username == null && !cmd.equals(Protocol.LOGIN) && !cmd.equals(Protocol.LOGOUT)) {
            sendError(Protocol.ERR_NOT_LOGGED_IN, "Ban can dang nhap truoc");
            return;
        }
        switch (cmd) {
            case Protocol.LOGIN -> handleLogin(p);
            case Protocol.LIST_ROOMS -> send(server.roomListLine());
            case Protocol.CREATE_ROOM -> handleCreateRoom(p);
            case Protocol.JOIN -> handleJoin(p);
            case Protocol.LEAVE -> handleLeave(p);
            case Protocol.MSG -> handleMessage(p);
            case Protocol.HISTORY -> handleHistory(p);
            case Protocol.MEMBERS -> handleMembers(p);
            case Protocol.LOGOUT -> disconnect("Tam biet");
            default -> sendError(Protocol.ERR_UNKNOWN_COMMAND, "Lenh khong hop le: " + cmd);
        }
    }

    private void handleLogin(String[] p) {
        if (username != null) {
            sendError(Protocol.ERR_ALREADY_LOGGED_IN, "Ban da dang nhap voi ten " + username);
            return;
        }
        String name = field(p, 1).trim();
        if (!Protocol.isValidName(name)) {
            sendError(Protocol.ERR_INVALID_NAME,
                    "Ten 1-" + Protocol.MAX_NAME_LENGTH + " ky tu, chi gom chu, so, _ . - (khong khoang trang)");
            return;
        }
        if (!server.registerUser(name, this)) {
            sendError(Protocol.ERR_NAME_TAKEN, "Ten '" + name + "' dang co nguoi su dung");
            return;
        }
        username = name;
        send(Protocol.build(Protocol.LOGIN_OK, name));
        send(server.roomListLine());
        server.userLoggedIn(this);
    }

    private void handleCreateRoom(String[] p) {
        String name = field(p, 1).trim();
        if (!Protocol.isValidRoomName(name)) {
            sendError(Protocol.ERR_INVALID_ROOM,
                    "Ten phong 1-" + Protocol.MAX_ROOM_LENGTH + " ky tu, chi gom chu, so, khoang trang, _ . -");
            return;
        }
        Room room = server.rooms().create(name);
        if (room == null) {
            sendError(Protocol.ERR_ROOM_EXISTS, "Phong '" + name + "' da ton tai");
            return;
        }
        server.safeStore(() -> server.store().saveRoom(name, username));
        server.log(username + " tao phong '" + name + "'");
        send(Protocol.build(Protocol.ROOM_CREATED, name));
        server.broadcastRoomList();
    }

    private void handleJoin(String[] p) {
        Room room = server.rooms().get(field(p, 1).trim());
        if (room == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Khong tim thay phong '" + field(p, 1) + "'");
            return;
        }
        if (room == currentRoom) {
            sendError(Protocol.ERR_ALREADY_IN_ROOM, "Ban dang o trong phong nay");
            return;
        }
        leaveCurrentRoom(); // moi nguoi chi o 1 phong

        room.add(this);
        currentRoom = room;
        send(Protocol.build(Protocol.JOINED, room.getName()));
        sendHistory(room, Protocol.DEFAULT_HISTORY_LIMIT);
        sendMembers(room);
        room.broadcast(Protocol.build(Protocol.USER_JOINED, room.getName(), username), this);
        server.log(username + " vao phong '" + room.getName() + "'");
        server.broadcastRoomList();
    }

    private void handleLeave(String[] p) {
        Room room = currentRoom;
        Room requested = server.rooms().get(field(p, 1).trim());
        if (room == null || (requested != null && requested != room)) {
            sendError(Protocol.ERR_NOT_IN_ROOM, "Ban khong o trong phong nay");
            return;
        }
        leaveCurrentRoom();
        send(Protocol.build(Protocol.LEFT, room.getName()));
        server.broadcastRoomList();
    }

    private void handleMessage(String[] p) {
        Room room = currentRoom;
        Room target = server.rooms().get(field(p, 1).trim());
        if (room == null || target != room) {
            sendError(Protocol.ERR_NOT_IN_ROOM, "Ban phai vao phong truoc khi gui tin");
            return;
        }
        String content = field(p, 2);
        if (content.isBlank()) {
            sendError(Protocol.ERR_EMPTY_MESSAGE, "Tin nhan trong");
            return;
        }
        if (content.length() > Protocol.MAX_MESSAGE_LENGTH) {
            sendError(Protocol.ERR_MESSAGE_TOO_LONG, "Tin nhan toi da " + Protocol.MAX_MESSAGE_LENGTH + " ky tu");
            return;
        }
        // Khoa theo phong: luu + phat theo cung mot thu tu cho moi nguoi
        synchronized (room) {
            ChatMessage msg = new ChatMessage(room.getName(), username, System.currentTimeMillis(), content);
            server.safeStore(() -> server.store().saveMessage(msg));
            room.broadcast(Protocol.build(Protocol.MSG, msg.room(), msg.sender(), msg.timestamp(), msg.content()),
                    null); // gui ca nguoi gui de hien thi gio chuan cua server
        }
        server.log("[" + room.getName() + "] " + username + ": " + content);
    }

    private void handleHistory(String[] p) {
        Room room = server.rooms().get(field(p, 1).trim());
        if (room == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Khong tim thay phong '" + field(p, 1) + "'");
            return;
        }
        int limit = Protocol.DEFAULT_HISTORY_LIMIT;
        try {
            limit = Integer.parseInt(field(p, 2).trim());
        } catch (NumberFormatException ignored) {
        }
        limit = Math.max(1, Math.min(limit, Protocol.MAX_HISTORY_LIMIT));
        sendHistory(room, limit);
    }

    private void handleMembers(String[] p) {
        Room room = server.rooms().get(field(p, 1).trim());
        if (room == null) {
            sendError(Protocol.ERR_ROOM_NOT_FOUND, "Khong tim thay phong '" + field(p, 1) + "'");
            return;
        }
        sendMembers(room);
    }

    // ================== Ham phu ==================

    private void sendHistory(Room room, int limit) {
        List<ChatMessage> history = new ArrayList<>();
        try {
            history = server.store().loadHistory(room.getName(), limit);
        } catch (RuntimeException e) {
            server.log("Loi doc lich su phong '" + room.getName() + "': " + e.getMessage());
        }
        for (ChatMessage m : history) {
            send(Protocol.build(Protocol.HISTORY_ITEM, room.getName(), m.sender(), m.timestamp(), m.content()));
        }
        send(Protocol.build(Protocol.HISTORY_END, room.getName(), history.size()));
    }

    private void sendMembers(Room room) {
        List<Object> fields = new ArrayList<>();
        fields.add(room.getName());
        fields.addAll(room.memberNames());
        send(Protocol.build(Protocol.MEMBERS, fields.toArray()));
    }

    private void leaveCurrentRoom() {
        Room room = currentRoom;
        if (room == null) {
            return;
        }
        room.remove(this);
        currentRoom = null;
        room.broadcast(Protocol.build(Protocol.USER_LEFT, room.getName(), username), null);
        server.log(username + " roi phong '" + room.getName() + "'");
    }

    private static String field(String[] p, int i) {
        return i < p.length ? p[i] : "";
    }

    private String who() {
        return username != null ? username : address;
    }

    private void sendError(String code, String message) {
        send(Protocol.build(Protocol.ERROR, code, message));
    }

    /**
     * Dua 1 goi tin vao hang doi gui (khong bao gio bi chan).
     * Neu hang doi day nghia la client khong doc kip -> ngat client do de bao ve ca phong.
     */
    public void send(String line) {
        if (closed) {
            return;
        }
        if (!outbox.offer(line)) {
            server.log("Ngat " + who() + ": mang qua cham / khong phan hoi (hang doi day)");
            closeSocket();
        }
    }

    /** Gui BYE, cho gui het hang doi roi dong ket noi (dung cho LOGOUT va khi server dung). */
    void disconnect(String reason) {
        if (closed) {
            return;
        }
        send(Protocol.build(Protocol.BYE, reason));
        closed = true;              // khong nhan them goi moi
        outbox.offer(POISON);       // thread ghi gui het roi tu dong socket
        try {
            socket.shutdownInput(); // thread doc thoat vong lap
        } catch (IOException ignored) {
        }
    }

    private void closeSocket() {
        closed = true;
        outbox.clear();
        outbox.offer(POISON);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private boolean cleanedUp;

    private void cleanup() {
        synchronized (this) {
            if (cleanedUp) {
                return;
            }
            cleanedUp = true;
        }
        // Cho thread ghi gui not cac goi con lai (vd BYE), toi da 2 giay, roi dong han
        closed = true;
        if (!outbox.offer(POISON)) {
            closeSocket();
        }
        try {
            writer.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeSocket();

        boolean wasInRoom = currentRoom != null;
        if (username != null) {
            leaveCurrentRoom();
        }
        server.clientDisconnected(this);
        if (wasInRoom && server.isRunning()) {
            server.broadcastRoomList();
        }
    }
}
