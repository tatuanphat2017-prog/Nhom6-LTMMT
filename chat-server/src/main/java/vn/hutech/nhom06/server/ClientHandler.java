package vn.hutech.nhom06.server;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.FileInfo;
import vn.hutech.nhom06.common.PrivateMessage;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.server.store.MessageStore;

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
 * Tin nhan rieng: PM|nguoiNhan|noiDung -> server chi gui cho nguoi gui + nguoi nhan
 * (PM|nguoiGui|nguoiNhan|thoiGian|noiDung), luu vao CSDL de xem lai lich su.
 *
 * Truyen file (toi da Protocol.MAX_FILE_SIZE = 25 MB), vao phong hoac gui rieng ("@ten"):
 *  - File duoc cat thanh manh 4 KB, ma hoa Base64, moi manh la 1 dong -> van dung
 *    chung giao thuc theo dong, khong vuot MAX_LINE_LENGTH.
 *  - Server kiem tra dung luong khai bao VA so byte nhan thuc te: vuot 25 MB -> huy ngay,
 *    xoa file tam (khong the gui file 1-10GB lam day o dia server).
 *  - Manh nhan duoc ghi thang xuong dia (khong gom trong RAM).
 *  - Tai file ve chay tren thread rieng va gui cham lai khi hang doi day,
 *    nen nguoi dang tai van chat binh thuong.
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

    // Lan gui file dang nhan (chi thread doc truy cap). Moi client 1 file mot luc.
    private Upload upload;
    // Dang gui file cho client nay tai ve (moi client 1 file mot luc)
    private final AtomicBoolean downloading = new AtomicBoolean(false);
    /** So goi trong hang doi ma thread tai file se cho bot truoc khi gui tiep. */
    private static final int DOWNLOAD_QUEUE_LIMIT = 256;

    /** Trang thai mot lan nhan file tu client. */
    private static final class Upload {
        String transferId;
        String fileId;
        String fileName;
        long expected;
        long received;
        Room room;        // gui vao phong (null neu gui rieng)
        String toUser;    // gui rieng cho nguoi nay (null neu gui vao phong)
        Path temp;
        OutputStream out;
    }

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
        if (username == null && !cmd.equals(Protocol.LOGIN) && !cmd.equals(Protocol.REGISTER)
                && !cmd.equals(Protocol.LOGOUT)) {
            sendError(Protocol.ERR_NOT_LOGGED_IN, "Ban can dang nhap truoc");
            return;
        }
        switch (cmd) {
            case Protocol.LOGIN -> handleLogin(p, false);
            case Protocol.REGISTER -> handleLogin(p, true);
            case Protocol.LIST_ROOMS -> send(server.roomListLine());
            case Protocol.CREATE_ROOM -> handleCreateRoom(p);
            case Protocol.JOIN -> handleJoin(p);
            case Protocol.LEAVE -> handleLeave(p);
            case Protocol.MSG -> handleMessage(p);
            case Protocol.HISTORY -> handleHistory(p);
            case Protocol.MEMBERS -> handleMembers(p);
            case Protocol.LIST_ONLINE -> send(server.onlineListLine());
            case Protocol.PM -> handlePrivateMessage(p);
            case Protocol.PM_HISTORY -> handlePrivateHistory(p);
            case Protocol.PM_READ -> handlePrivateRead(p);
            case Protocol.LIST_USERS -> send(server.userListLine());
            case Protocol.LIST_UNREAD -> send(unreadLine());
            case Protocol.FILE_START -> handleFileStart(p);
            case Protocol.FILE_CHUNK -> handleFileChunk(p);
            case Protocol.FILE_END -> handleFileEnd(p);
            case Protocol.FILE_CANCEL -> handleFileCancel(p);
            case Protocol.FILE_GET -> handleFileGet(p);
            case Protocol.LOGOUT -> disconnect("Tam biet");
            default -> sendError(Protocol.ERR_UNKNOWN_COMMAND, "Lenh khong hop le: " + cmd);
        }
    }

    /** Bam san 1 lan: dung khi ten khong ton tai, de thoi gian tra loi giong nhu khi sai mat khau. */
    private static final PasswordHasher.Hashed DUMMY = PasswordHasher.hash("khong-dung-de-dang-nhap");
    /** Sai mat khau qua so lan nay tren 1 ket noi -> ngat (chong do mat khau). */
    static final int MAX_FAILED_LOGINS = 5;
    private int failedLogins;

    /**
     * LOGIN|ten|matKhau  hoac  REGISTER|ten|matKhau (dang ky xong dang nhap luon).
     * Sai ten hay sai mat khau deu bao chung WRONG_LOGIN de nguoi ngoai khong do duoc ten nao da co.
     */
    private void handleLogin(String[] p, boolean register) {
        if (username != null) {
            sendError(Protocol.ERR_ALREADY_LOGGED_IN, "Ban da dang nhap voi ten " + username);
            return;
        }
        String name = field(p, 1).trim();
        String password = field(p, 2);
        if (!Protocol.isValidName(name)) {
            sendError(Protocol.ERR_INVALID_NAME,
                    "Ten 1-" + Protocol.MAX_NAME_LENGTH + " ky tu, chi gom chu, so, _ . - (khong khoang trang)");
            return;
        }
        if (!Protocol.isValidPassword(password)) {
            if (register) {
                sendError(Protocol.ERR_INVALID_PASSWORD, "Mat khau " + Protocol.MIN_PASSWORD_LENGTH + "-"
                        + Protocol.MAX_PASSWORD_LENGTH + " ky tu");
            } else {
                wrongLogin(name);
            }
            return;
        }

        String accountName;   // ten dung theo CSDL (giu hoa/thuong luc dang ky)
        try {
            if (register) {
                PasswordHasher.Hashed h = PasswordHasher.hash(password);
                if (!server.store().createAccount(name, h.hash(), h.salt())) {
                    sendError(Protocol.ERR_USER_EXISTS, "Ten '" + name + "' da co nguoi dang ky");
                    return;
                }
                MessageStore.Account acc = server.store().findAccount(name);
                accountName = acc != null ? acc.username() : name;
                server.log("Tai khoan moi: " + accountName + " (" + address + ")");
            } else {
                MessageStore.Account acc = server.store().findAccount(name);
                boolean ok;
                if (acc != null) {
                    ok = PasswordHasher.verify(password, acc.passwordHash(), acc.passwordSalt());
                } else {
                    PasswordHasher.verify(password, DUMMY.hash(), DUMMY.salt());   // chi de ton thoi gian nhu nhau
                    ok = false;
                }
                if (!ok) {
                    wrongLogin(name);
                    return;
                }
                accountName = acc.username();
            }
        } catch (RuntimeException e) {
            server.log("Loi CSDL khi " + (register ? "dang ky" : "dang nhap") + ": " + e.getMessage());
            sendError(Protocol.ERR_SERVER, "Server khong truy cap duoc CSDL, thu lai sau");
            return;
        }

        if (!server.registerUser(accountName, this)) {
            sendError(Protocol.ERR_NAME_TAKEN, "Tai khoan '" + accountName + "' dang dang nhap o noi khac");
            return;
        }
        username = accountName;
        send(Protocol.build(Protocol.LOGIN_OK, accountName));
        send(server.roomListLine());
        send(server.userListLine());
        send(unreadLine());          // tin rieng nhan duoc trong luc offline -> so do
        server.userLoggedIn(this);   // -> moi nguoi (ke ca minh) nhan ONLINE_LIST moi
        if (register) {
            server.broadcastUserList();   // moi nguoi thay tai khoan moi trong cot "Moi nguoi"
        }
    }

    /** Bao sai ten/mat khau; sai qua MAX_FAILED_LOGINS lan tren 1 ket noi thi ngat. */
    private void wrongLogin(String name) {
        failedLogins++;
        server.log("Dang nhap sai: '" + name + "' tu " + address + " (lan " + failedLogins + ")");
        sendError(Protocol.ERR_WRONG_LOGIN, "Sai ten dang nhap hoac mat khau");
        if (failedLogins >= MAX_FAILED_LOGINS) {
            disconnect("Sai mat khau qua " + MAX_FAILED_LOGINS + " lan, ket noi bi dong");
        }
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

    // ================== Tin nhan rieng ==================

    /** Nguoi nhan tin rieng: ten dung (theo tai khoan) + ket noi neu dang online (null = offline). */
    private record Target(String name, ClientHandler online) {
    }

    /** PM|nguoiNhan|noiDung   (nguoi nhan offline van gui duoc: luu lai, ho dang nhap se thay) */
    private void handlePrivateMessage(String[] p) {
        Target to = privateTarget(field(p, 1));
        if (to == null) {
            return;   // da bao loi
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
        deliverPrivate(new PrivateMessage(username, to.name(), System.currentTimeMillis(), content), to.online());
        server.log(username + " gui tin rieng cho " + to.name() + (to.online() == null ? " (dang offline)" : ""));
    }

    /**
     * Luu + gui cho ca 2 ben (nguoi gui cung nhan lai de hien gio chuan cua server).
     * Nguoi nhan offline (to = null): chi luu, IsRead = 0 -> lan dang nhap sau hien so do.
     */
    private void deliverPrivate(PrivateMessage m, ClientHandler to) {
        server.safeStore(() -> server.store().savePrivateMessage(m));
        String line = m.isFile()
                ? Protocol.build(Protocol.PM_FILE, m.sender(), m.receiver(), m.timestamp(),
                        m.file().id(), m.file().name(), m.file().size())
                : Protocol.build(Protocol.PM, m.sender(), m.receiver(), m.timestamp(), m.content());
        send(line);
        ClientHandler now = to != null ? to : server.findUser(m.receiver());   // co the vua dang nhap lai
        if (now != null && now != this) {
            now.send(line);
        }
    }

    /** Tim nguoi nhan; sai thi bao loi (ERROR) va tra null. */
    private Target privateTarget(String name) {
        String err = checkTarget(name);
        if (err != null) {
            sendError(err.startsWith("SELF") ? Protocol.ERR_SELF_MESSAGE : Protocol.ERR_USER_NOT_FOUND,
                    err.substring(err.indexOf(':') + 1));
            return null;
        }
        return resolveTarget(name);
    }

    /** null = hop le; nguoc lai "MA:mo ta". */
    private String checkTarget(String name) {
        Target t = resolveTarget(name);
        if (t == null) {
            return "NOT_FOUND:Khong co tai khoan '" + name.trim() + "'";
        }
        if (t.name().equalsIgnoreCase(username)) {
            return "SELF:Khong the gui tin rieng cho chinh minh";
        }
        return null;
    }

    /** Dang online -> lay ket noi; offline -> tra cuu tai khoan trong CSDL. Khong co -> null. */
    private Target resolveTarget(String name) {
        String n = name == null ? "" : name.trim();
        ClientHandler on = server.findUser(n);
        if (on != null && on.getUsername() != null) {
            return new Target(on.getUsername(), on);
        }
        if (!Protocol.isValidName(n)) {
            return null;
        }
        try {
            MessageStore.Account acc = server.store().findAccount(n);
            return acc != null ? new Target(acc.username(), null) : null;
        } catch (RuntimeException e) {
            server.log("Loi tra cuu tai khoan: " + e.getMessage());
            return null;
        }
    }

    /** PM_READ|nguoiKia : minh da mo khung chat voi nguoiKia -> het so do. */
    private void handlePrivateRead(String[] p) {
        String other = field(p, 1).trim();
        if (Protocol.isValidName(other)) {
            server.safeStore(() -> server.store().markRead(username, other));
        }
    }

    /** UNREAD_LIST|Huy:3|Lang:1 */
    private String unreadLine() {
        List<Object> fields = new ArrayList<>();
        try {
            server.store().countUnread(username).forEach((who, n) -> fields.add(who + ":" + n));
        } catch (RuntimeException e) {
            server.log("Loi dem tin chua doc: " + e.getMessage());
        }
        return Protocol.build(Protocol.UNREAD_LIST, fields.toArray());
    }

    /** PM_HISTORY|nguoiKia|soLuong (nguoi kia co the dang offline: van xem duoc tin cu). */
    private void handlePrivateHistory(String[] p) {
        String other = field(p, 1).trim();
        if (!Protocol.isValidName(other)) {
            sendError(Protocol.ERR_INVALID_NAME, "Ten khong hop le");
            return;
        }
        int limit = Protocol.DEFAULT_HISTORY_LIMIT;
        try {
            limit = Integer.parseInt(field(p, 2).trim());
        } catch (NumberFormatException ignored) {
        }
        limit = Math.max(1, Math.min(limit, Protocol.MAX_HISTORY_LIMIT));
        List<PrivateMessage> list = new ArrayList<>();
        try {
            list = server.store().loadPrivateHistory(username, other, limit);
        } catch (RuntimeException e) {
            server.log("Loi doc tin rieng " + username + " - " + other + ": " + e.getMessage());
        }
        for (PrivateMessage m : list) {
            if (m.isFile()) {
                send(Protocol.build(Protocol.PM_HISTORY_FILE, m.sender(), m.receiver(), m.timestamp(),
                        m.file().id(), m.file().name(), m.file().size()));
            } else {
                send(Protocol.build(Protocol.PM_HISTORY_ITEM, m.sender(), m.receiver(), m.timestamp(), m.content()));
            }
        }
        send(Protocol.build(Protocol.PM_HISTORY_END, other, list.size()));
    }

    // ================== Nhan file tu client ==================

    /** FILE_START|maGui|noiNhan|tenFile|dungLuong   (noiNhan = ten phong, hoac "@ten" gui rieng) */
    private void handleFileStart(String[] p) {
        String tid = field(p, 1).trim();
        if (!Protocol.isValidTransferId(tid)) {
            sendError(Protocol.ERR_BAD_COMMAND, "Ma gui file khong hop le");
            return;
        }
        String target = field(p, 2).trim();
        Room room = null;
        String toUser = null;
        if (target.startsWith(Protocol.USER_TARGET_PREFIX)) {
            String name = target.substring(Protocol.USER_TARGET_PREFIX.length());
            String err = checkTarget(name);
            if (err != null) {
                rejectFile(tid, err.startsWith("SELF") ? Protocol.ERR_SELF_MESSAGE : Protocol.ERR_USER_NOT_FOUND,
                        err.substring(err.indexOf(':') + 1));
                return;
            }
            toUser = resolveTarget(name).name();   // nguoi nhan offline van gui duoc
        } else {
            room = currentRoom;
            if (room == null || server.rooms().get(target) != room) {
                rejectFile(tid, Protocol.ERR_NOT_IN_ROOM, "Ban phai vao phong truoc khi gui file");
                return;
            }
        }
        if (upload != null) {
            rejectFile(tid, Protocol.ERR_FILE_BUSY, "Ban dang gui mot file khac, hay doi gui xong");
            return;
        }
        String name = Protocol.cleanFileName(field(p, 3));
        long size;
        try {
            size = Long.parseLong(field(p, 4).trim());
        } catch (NumberFormatException e) {
            rejectFile(tid, Protocol.ERR_FILE_INVALID, "Dung luong file khong hop le");
            return;
        }
        if (size <= 0) {
            rejectFile(tid, Protocol.ERR_FILE_INVALID, "File rong (0 byte)");
            return;
        }
        if (size > Protocol.MAX_FILE_SIZE) {
            rejectFile(tid, Protocol.ERR_FILE_TOO_LARGE, "File vuot qua gioi han "
                    + FileInfo.formatSize(Protocol.MAX_FILE_SIZE));
            server.log(username + " gui file qua lon: " + name + " (" + FileInfo.formatSize(size) + ") -> tu choi");
            return;
        }

        Upload u = new Upload();
        u.transferId = tid;
        u.fileId = UUID.randomUUID().toString().replace("-", "");
        u.fileName = name;
        u.expected = size;
        u.room = room;
        u.toUser = toUser;
        u.temp = server.uploadDir().resolve(u.fileId + ".part");
        try {
            u.out = new BufferedOutputStream(Files.newOutputStream(u.temp), 64 * 1024);
        } catch (IOException e) {
            server.log("Khong tao duoc file tam: " + e.getMessage());
            rejectFile(tid, Protocol.ERR_SERVER, "Server khong luu duoc file");
            return;
        }
        upload = u;
        server.log(username + " bat dau gui file '" + name + "' (" + FileInfo.formatSize(size) + ") "
                + (room != null ? "vao phong '" + room.getName() + "'" : "rieng cho " + toUser));
        send(Protocol.build(Protocol.FILE_ACCEPT, tid));
    }

    /** FILE_CHUNK|maGui|base64 */
    private void handleFileChunk(String[] p) {
        Upload u = upload;
        if (u == null || !u.transferId.equals(field(p, 1))) {
            return;   // manh cua lan gui da bi huy/tu choi: bo qua im lang
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(field(p, 2));
        } catch (IllegalArgumentException e) {
            abortUpload(Protocol.ERR_FILE_INVALID, "Du lieu file bi loi");
            return;
        }
        if (u.received + data.length > u.expected || u.received + data.length > Protocol.MAX_FILE_SIZE) {
            abortUpload(Protocol.ERR_FILE_TOO_LARGE, "Du lieu gui len nhieu hon dung luong da khai bao");
            return;
        }
        try {
            u.out.write(data);
            u.received += data.length;
        } catch (IOException e) {
            server.log("Loi ghi file: " + e.getMessage());
            abortUpload(Protocol.ERR_SERVER, "Server khong luu duoc file");
        }
    }

    /** FILE_END|maGui -> kiem tra du byte, luu, bao ca phong. */
    private void handleFileEnd(String[] p) {
        Upload u = upload;
        if (u == null || !u.transferId.equals(field(p, 1))) {
            return;
        }
        if (u.received != u.expected) {
            abortUpload(Protocol.ERR_FILE_INVALID, "File nhan duoc bi thieu ("
                    + u.received + "/" + u.expected + " byte)");
            return;
        }
        try {
            u.out.close();
            Files.move(u.temp, server.uploadDir().resolve(u.fileId), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            server.log("Loi luu file: " + e.getMessage());
            abortUpload(Protocol.ERR_SERVER, "Server khong luu duoc file");
            return;
        }
        upload = null;

        FileInfo info = new FileInfo(u.fileId, u.fileName, u.expected);
        if (u.toUser != null) {   // file gui rieng
            send(Protocol.build(Protocol.FILE_SAVED, u.transferId, u.fileId));
            PrivateMessage pm = PrivateMessage.ofFile(username, u.toUser, System.currentTimeMillis(), info);
            deliverPrivate(pm, server.findUser(u.toUser));   // offline: chi luu, ho vao lai se thay
            server.log(username + " da gui file rieng '" + info.name() + "' (" + info.sizeText()
                    + ") cho " + u.toUser);
            return;
        }
        Room room = u.room;
        synchronized (room) {
            ChatMessage msg = ChatMessage.ofFile(room.getName(), username, System.currentTimeMillis(), info);
            server.safeStore(() -> server.store().saveMessage(msg));
            send(Protocol.build(Protocol.FILE_SAVED, u.transferId, u.fileId));
            room.broadcast(Protocol.build(Protocol.FILE, room.getName(), username, msg.timestamp(),
                    info.id(), info.name(), info.size()), null);
        }
        server.log("[" + room.getName() + "] " + username + " da gui file '" + info.name()
                + "' (" + info.sizeText() + ")");
    }

    /** FILE_CANCEL|maGui: nguoi gui bam Huy. */
    private void handleFileCancel(String[] p) {
        Upload u = upload;
        if (u != null && u.transferId.equals(field(p, 1))) {
            server.log(username + " huy gui file '" + u.fileName + "'");
            discardUpload();
        }
    }

    private void rejectFile(String tid, String code, String message) {
        send(Protocol.build(Protocol.FILE_REJECT, tid, code, message));
    }

    /** Huy lan gui dang do va bao cho client. */
    private void abortUpload(String code, String message) {
        Upload u = upload;
        if (u == null) {
            return;
        }
        server.log("Huy nhan file '" + u.fileName + "' tu " + who() + ": " + message);
        discardUpload();
        rejectFile(u.transferId, code, message);
    }

    /** Dong va xoa file tam (khi huy hoac client mat ket noi giua chung). */
    private void discardUpload() {
        Upload u = upload;
        upload = null;
        if (u == null) {
            return;
        }
        try {
            u.out.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(u.temp);
        } catch (IOException ignored) {
        }
    }

    // ================== Gui file cho client tai ve ==================

    /** FILE_GET|maFile */
    private void handleFileGet(String[] p) {
        String fileId = field(p, 1).trim();
        if (!Protocol.isValidFileId(fileId)) {
            send(Protocol.build(Protocol.FILE_DATA_ERROR, fileId, Protocol.ERR_FILE_NOT_FOUND, "Ma file khong hop le"));
            return;
        }
        Path path = server.uploadDir().resolve(fileId);
        if (!Files.isRegularFile(path)) {
            send(Protocol.build(Protocol.FILE_DATA_ERROR, fileId, Protocol.ERR_FILE_NOT_FOUND,
                    "File khong con tren server"));
            return;
        }
        if (!downloading.compareAndSet(false, true)) {
            send(Protocol.build(Protocol.FILE_DATA_ERROR, fileId, Protocol.ERR_FILE_BUSY,
                    "Ban dang tai mot file khac, hay doi tai xong"));
            return;
        }
        server.runBackground(() -> streamFile(fileId, path));
    }

    /** Chay tren thread rieng: doc file va gui tung manh, cho bot khi hang doi day. */
    private void streamFile(String fileId, Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            long size = Files.size(path);
            send(Protocol.build(Protocol.FILE_DATA_START, fileId, size));
            byte[] buf = new byte[Protocol.FILE_CHUNK_SIZE];
            Base64.Encoder b64 = Base64.getEncoder();
            int n;
            while (!closed && (n = in.readNBytes(buf, 0, buf.length)) > 0) {
                while (!closed && outbox.size() > DOWNLOAD_QUEUE_LIMIT) {
                    Thread.sleep(5);   // nhuong cho tin chat, khong lam day hang doi
                }
                send(Protocol.FILE_DATA + Protocol.SEPARATOR + fileId + Protocol.SEPARATOR
                        + b64.encodeToString(n == buf.length ? buf : java.util.Arrays.copyOf(buf, n)));
            }
            if (!closed) {
                send(Protocol.build(Protocol.FILE_DATA_END, fileId));
                server.log(who() + " da tai file " + fileId + " (" + FileInfo.formatSize(size) + ")");
            }
        } catch (IOException e) {
            send(Protocol.build(Protocol.FILE_DATA_ERROR, fileId, Protocol.ERR_SERVER, "Server khong doc duoc file"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            downloading.set(false);
        }
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
            if (m.isFile()) {
                FileInfo f = m.file();
                send(Protocol.build(Protocol.HISTORY_FILE, room.getName(), m.sender(), m.timestamp(),
                        f.id(), f.name(), f.size()));
            } else {
                send(Protocol.build(Protocol.HISTORY_ITEM, room.getName(), m.sender(), m.timestamp(), m.content()));
            }
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
        discardUpload();   // dang gui file do dang -> xoa file tam

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
