package vn.hutech.nhom06.client.net;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.FileInfo;
import vn.hutech.nhom06.common.PrivateMessage;
import vn.hutech.nhom06.common.Protocol;

/**
 * Ket noi TCP tu Client den Server chat.
 *
 * Cach dung (phia giao dien):
 *     ClientConnection conn = new ClientConnection();
 *     conn.setListener(this);              // this implements ClientEventListener
 *     conn.connect("localhost", 5000);     // nem IOException neu khong ket noi duoc
 *     conn.login("Lang", "matkhau");       // ket qua ve qua onLoginOk / onError
 *     conn.register("Lang", "matkhau");    // dang ky, thanh cong thi dang nhap luon (onLoginOk)
 *     conn.join("Phong chung");            // -> onJoined, onHistoryItem..., onHistoryEnd, onMembers
 *     conn.sendMessage("xin chao");        // -> onMessage (minh cung nhan lai)
 *     conn.sendFile(path, cb);             // gui file (toi da 25 MB) -> ca phong nhan onMessage (tin file)
 *     conn.sendPrivateMessage("Huy", "hi"); // tin rieng -> ca 2 ben nhan onPrivateMessage
 *     conn.sendPrivateFile("Huy", path, cb); // file rieng -> ca 2 ben nhan onPrivateMessage (tin file)
 *     conn.requestPrivateHistory("Huy", 50); // -> onPrivateHistoryItem..., onPrivateHistoryEnd
 *     conn.downloadFile(info, path, cb);   // tai file trong tin nhan ve may
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

    /** Thoi gian cho server tra loi khi gui file (ms). */
    private static final long FILE_REPLY_TIMEOUT_MS = 30_000;

    // Lan gui file dang cho server tra loi: maGui -> trang thai
    private final Map<String, Upload> uploads = new ConcurrentHashMap<>();
    // File dang tai ve: maFile -> trang thai (chi thread nhan tin ghi du lieu)
    private final Map<String, Download> downloads = new ConcurrentHashMap<>();

    private static final class Upload {
        final CompletableFuture<Void> accepted = new CompletableFuture<>();
        final CompletableFuture<Void> saved = new CompletableFuture<>();
        volatile String rejected;   // != null: server tu choi / huy giua chung
    }

    private static final class Download {
        final FileTransfer transfer;
        final FileTransferListener callback;
        final Path target;
        final Path temp;
        long expected = -1;
        long received;
        OutputStream out;

        Download(FileTransfer transfer, FileTransferListener callback, Path target) {
            this.transfer = transfer;
            this.callback = callback;
            this.target = target;
            this.temp = target.resolveSibling(target.getFileName() + ".part");
        }
    }

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

    /** Dang nhap. Ket qua: onLoginOk, hoac onError (WRONG_LOGIN, NAME_TAKEN...). */
    public void login(String name, String password) {
        send(Protocol.build(Protocol.LOGIN, name == null ? "" : name.trim(), password == null ? "" : password));
    }

    /** Dang ky tai khoan moi; thanh cong thi server dang nhap luon -> onLoginOk. Loi: USER_EXISTS, INVALID_PASSWORD... */
    public void register(String name, String password) {
        send(Protocol.build(Protocol.REGISTER, name == null ? "" : name.trim(), password == null ? "" : password));
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

    /** Xin lai danh sach nguoi online. Ket qua: onOnlineList. */
    public void listOnline() {
        send(Protocol.build(Protocol.LIST_ONLINE));
    }

    /** Xin danh sach moi tai khoan. Ket qua: onUserList. */
    public void listUsers() {
        send(Protocol.build(Protocol.LIST_USERS));
    }

    /** Xin so tin rieng chua doc. Ket qua: onUnreadCounts. */
    public void listUnread() {
        send(Protocol.build(Protocol.LIST_UNREAD));
    }

    /** Bao server: da doc het tin rieng {@code other} gui minh (khi mo khung chat voi nguoi do). */
    public void markPrivateRead(String other) {
        if (other != null && !other.isBlank()) {
            send(Protocol.build(Protocol.PM_READ, other.trim()));
        }
    }

    /**
     * Gui tin rieng cho 1 nguoi (online hoac offline - offline thi server luu lai, ho dang nhap se thay).
     * Ket qua: onPrivateMessage (ca 2 ben) hoac onError.
     */
    public void sendPrivateMessage(String toUser, String content) {
        send(Protocol.build(Protocol.PM, toUser == null ? "" : toUser.trim(), content == null ? "" : content));
    }

    /** Xin lich su tin rieng voi {@code other} (1..200 tin). Ket qua: onPrivateHistoryItem..., onPrivateHistoryEnd. */
    public void requestPrivateHistory(String other, int limit) {
        send(Protocol.build(Protocol.PM_HISTORY, other == null ? "" : other.trim(), limit));
    }

    /** Dang xuat: server tra BYE roi dong ket noi -> onDisconnected. */
    public void logout() {
        if (!isConnected()) {
            return;
        }
        closeReason = "Da dang xuat";
        send(Protocol.LOGOUT);
    }

    // ===================== Gui / tai file =====================

    /**
     * Gui 1 file vao phong dang o (chay tren thread rieng, khong lam dung giao dien).
     * Kiem tra dung luong toi da {@link Protocol#MAX_FILE_SIZE} ngay tai client.
     * Gui xong ca phong (ke ca minh) nhan tin file qua onMessage.
     *
     * @return doi tuong de huy, hoac null neu bi tu choi ngay (da goi callback.onFailed)
     */
    public FileTransfer sendFile(Path file, FileTransferListener callback) {
        String room = currentRoom;
        if (room == null) {
            callback.onFailed("Bạn cần vào phòng trước khi gửi file");
            return null;
        }
        return startUpload(room, file, callback);
    }

    /**
     * Gui 1 file rieng cho 1 nguoi dang online (toi da 25 MB).
     * Gui xong ca 2 ben nhan onPrivateMessage voi tin file.
     */
    public FileTransfer sendPrivateFile(String toUser, Path file, FileTransferListener callback) {
        if (toUser == null || toUser.isBlank()) {
            callback.onFailed("Chưa chọn người nhận");
            return null;
        }
        return startUpload(Protocol.USER_TARGET_PREFIX + toUser.trim(), file, callback);
    }

    /** target = ten phong, hoac "@ten" khi gui rieng. */
    private FileTransfer startUpload(String target, Path file, FileTransferListener callback) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            callback.onFailed("Không đọc được file: " + e.getMessage());
            return null;
        }
        String error = !isConnected() ? "Chưa kết nối đến server"
                : size <= 0 ? "File rỗng (0 byte), không thể gửi"
                : size > Protocol.MAX_FILE_SIZE ? "File quá lớn (" + FileInfo.formatSizeExact(size)
                        + "). Tối đa " + FileInfo.formatSizeExact(Protocol.MAX_FILE_SIZE)
                : null;
        if (error != null) {
            callback.onFailed(error);
            return null;
        }
        String name = Protocol.cleanFileName(file.getFileName().toString());
        FileTransfer transfer = new FileTransfer(name, size, true);
        Thread t = new Thread(() -> uploadLoop(file, target, name, size, transfer, callback), "client-upload");
        t.setDaemon(true);
        t.start();
        return transfer;
    }

    private void uploadLoop(Path file, String target, String name, long size,
            FileTransfer transfer, FileTransferListener callback) {
        String tid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Upload up = new Upload();
        uploads.put(tid, up);
        try {
            send(Protocol.build(Protocol.FILE_START, tid, target, name, size));
            if (!await(up.accepted)) {
                callback.onFailed(up.rejected != null ? up.rejected : "Server không phản hồi");
                return;
            }
            byte[] buf = new byte[Protocol.FILE_CHUNK_SIZE];
            Base64.Encoder b64 = Base64.getEncoder();
            long sent = 0;
            long lastReport = 0;
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.readNBytes(buf, 0, buf.length)) > 0) {
                    if (transfer.isCancelled() || up.rejected != null || !isConnected()) {
                        break;
                    }
                    if (sent + n > size) {   // file bi sua lon hon trong luc gui
                        transfer.cancel();
                        up.rejected = "File đã bị thay đổi trong lúc gửi";
                        break;
                    }
                    sendRaw(Protocol.FILE_CHUNK + Protocol.SEPARATOR + tid + Protocol.SEPARATOR
                            + b64.encodeToString(n == buf.length ? buf : Arrays.copyOf(buf, n)));
                    sent += n;
                    if (sent - lastReport >= 64 * 1024 || sent == size) {
                        lastReport = sent;
                        callback.onProgress(sent, size);
                    }
                }
            }
            if (!isConnected()) {
                callback.onFailed("Mất kết nối với server");
            } else if (up.rejected != null) {
                send(Protocol.build(Protocol.FILE_CANCEL, tid));
                callback.onFailed(up.rejected);
            } else if (transfer.isCancelled()) {
                send(Protocol.build(Protocol.FILE_CANCEL, tid));
                callback.onFailed("Đã hủy gửi file");
            } else if (sent != size) {
                send(Protocol.build(Protocol.FILE_CANCEL, tid));
                callback.onFailed("File đã bị thay đổi trong lúc gửi");
            } else {
                send(Protocol.build(Protocol.FILE_END, tid));
                if (await(up.saved)) {
                    callback.onDone();
                } else {
                    callback.onFailed(up.rejected != null ? up.rejected : "Server không xác nhận đã lưu file");
                }
            }
        } catch (IOException e) {
            send(Protocol.build(Protocol.FILE_CANCEL, tid));
            callback.onFailed("Lỗi đọc file: " + e.getMessage());
        } finally {
            uploads.remove(tid);
        }
    }

    /** Cho server tra loi; false neu bi tu choi, het gio hoac mat ket noi. */
    private static boolean await(CompletableFuture<Void> f) {
        try {
            f.get(FILE_REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Tai file trong tin nhan ve {@code target}. Du lieu ghi vao "ten.part" truoc,
     * du byte moi doi thanh ten that (khong de lai file hong neu dut mang).
     *
     * @return doi tuong de huy, hoac null neu bi tu choi ngay (da goi callback.onFailed)
     */
    public FileTransfer downloadFile(FileInfo file, Path target, FileTransferListener callback) {
        if (!isConnected()) {
            callback.onFailed("Chưa kết nối đến server");
            return null;
        }
        FileTransfer transfer = new FileTransfer(file.name(), file.size(), false);
        if (downloads.putIfAbsent(file.id(), new Download(transfer, callback, target)) != null) {
            callback.onFailed("File này đang được tải");
            return null;
        }
        send(Protocol.build(Protocol.FILE_GET, file.id()));
        return transfer;
    }

    private void onFileDataStart(String fileId, long size) {
        Download d = downloads.get(fileId);
        if (d == null) {
            return;
        }
        try {
            d.expected = size;
            d.out = new BufferedOutputStream(Files.newOutputStream(d.temp), 64 * 1024);
            d.callback.onProgress(0, size);
        } catch (IOException e) {
            failDownload(fileId, "Không ghi được file: " + e.getMessage());
        }
    }

    private void onFileData(String fileId, String base64) {
        Download d = downloads.get(fileId);
        if (d == null || d.out == null) {
            return;
        }
        if (d.transfer.isCancelled()) {
            failDownload(fileId, "Đã hủy tải file");   // phan con lai server gui se bi bo qua
            return;
        }
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            if (d.received + data.length > d.expected) {
                failDownload(fileId, "Dữ liệu nhận được lớn hơn dung lượng file");
                return;
            }
            long before = d.received;
            d.out.write(data);
            d.received += data.length;
            if (d.received / (64 * 1024) != before / (64 * 1024) || d.received == d.expected) {
                d.callback.onProgress(d.received, d.expected);
            }
        } catch (IllegalArgumentException | IOException e) {
            failDownload(fileId, "Lỗi ghi file: " + e.getMessage());
        }
    }

    private void onFileDataEnd(String fileId) {
        Download d = downloads.remove(fileId);
        if (d == null || d.out == null) {
            return;
        }
        try {
            d.out.close();
            if (d.received != d.expected) {
                Files.deleteIfExists(d.temp);
                d.callback.onFailed("File tải về bị thiếu dữ liệu");
                return;
            }
            Files.move(d.temp, d.target, StandardCopyOption.REPLACE_EXISTING);
            d.callback.onDone();
        } catch (IOException e) {
            d.callback.onFailed("Không lưu được file: " + e.getMessage());
        }
    }

    private void failDownload(String fileId, String reason) {
        Download d = downloads.remove(fileId);
        if (d == null) {
            return;
        }
        if (d.out != null) {
            try {
                d.out.close();
            } catch (IOException ignored) {
            }
        }
        try {
            Files.deleteIfExists(d.temp);
        } catch (IOException ignored) {
        }
        d.callback.onFailed(reason);
    }

    /** Mat ket noi: bao that bai cho moi lan gui/tai dang do. */
    private void failAllTransfers(String reason) {
        for (Upload u : uploads.values()) {
            u.rejected = reason;
            u.accepted.completeExceptionally(new IOException(reason));
            u.saved.completeExceptionally(new IOException(reason));
        }
        for (String id : new ArrayList<>(downloads.keySet())) {
            failDownload(id, reason);
        }
    }

    /** Gui 1 dong len server. Nhieu thread goi cung luc van an toan. */
    private void send(String line) {
        if (out == null || !isConnected()) {
            listener.onError("NOT_CONNECTED", "Chua ket noi den server");
            return;
        }
        sendRaw(line);
    }

    /** Nhu send() nhung khong bao onError (dung cho manh file, da tu kiem tra ket noi). */
    private void sendRaw(String line) {
        BufferedWriter w = out;
        if (w == null) {
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
            case Protocol.FILE -> l.onMessage(toFileMessage(p));
            case Protocol.HISTORY_FILE -> l.onHistoryItem(toFileMessage(p));
            case Protocol.ONLINE_LIST -> {
                List<String> users = new ArrayList<>();
                for (int i = 1; i < p.length; i++) {
                    if (!p[i].isEmpty()) {
                        users.add(p[i]);
                    }
                }
                l.onOnlineList(users);
            }
            case Protocol.USER_LIST -> {
                List<String> users = new ArrayList<>();
                for (int i = 1; i < p.length; i++) {
                    if (!p[i].isEmpty()) {
                        users.add(p[i]);
                    }
                }
                l.onUserList(users);
            }
            case Protocol.UNREAD_LIST -> {
                Map<String, Integer> unread = new LinkedHashMap<>();
                for (int i = 1; i < p.length; i++) {
                    int idx = p[i].lastIndexOf(':');
                    if (idx > 0) {
                        unread.put(p[i].substring(0, idx), toInt(p[i].substring(idx + 1)));
                    }
                }
                l.onUnreadCounts(unread);
            }
            case Protocol.PM -> l.onPrivateMessage(toPrivate(p));
            case Protocol.PM_FILE -> l.onPrivateMessage(toPrivateFile(p));
            case Protocol.PM_HISTORY_ITEM -> l.onPrivateHistoryItem(toPrivate(p));
            case Protocol.PM_HISTORY_FILE -> l.onPrivateHistoryItem(toPrivateFile(p));
            case Protocol.PM_HISTORY_END -> l.onPrivateHistoryEnd(field(p, 1), toInt(field(p, 2)));
            case Protocol.FILE_ACCEPT -> {
                Upload u = uploads.get(field(p, 1));
                if (u != null) {
                    u.accepted.complete(null);
                }
            }
            case Protocol.FILE_SAVED -> {
                Upload u = uploads.get(field(p, 1));
                if (u != null) {
                    u.saved.complete(null);
                }
            }
            case Protocol.FILE_REJECT -> {
                Upload u = uploads.get(field(p, 1));
                if (u != null) {
                    u.rejected = translateFileError(field(p, 2), field(p, 3));
                    u.accepted.completeExceptionally(new IOException(u.rejected));
                    u.saved.completeExceptionally(new IOException(u.rejected));
                }
            }
            case Protocol.FILE_DATA_START -> onFileDataStart(field(p, 1), toLong(field(p, 2)));
            case Protocol.FILE_DATA -> onFileData(field(p, 1), field(p, 2));
            case Protocol.FILE_DATA_END -> onFileDataEnd(field(p, 1));
            case Protocol.FILE_DATA_ERROR -> failDownload(field(p, 1), translateFileError(field(p, 2), field(p, 3)));
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

    /** FILE / HISTORY_FILE: LENH|phong|nguoiGui|thoiGian|maFile|tenFile|dungLuong */
    private static ChatMessage toFileMessage(String[] p) {
        if (p.length < 7) {
            throw new IllegalArgumentException("Goi tin thieu truong");
        }
        FileInfo f = new FileInfo(p[4], p[5], Long.parseLong(p[6]));
        return ChatMessage.ofFile(p[1], p[2], Long.parseLong(p[3]), f);
    }

    /** PM / PM_HISTORY_ITEM: LENH|nguoiGui|nguoiNhan|thoiGian|noiDung */
    private static PrivateMessage toPrivate(String[] p) {
        if (p.length < 5) {
            throw new IllegalArgumentException("Goi tin thieu truong");
        }
        return new PrivateMessage(p[1], p[2], Long.parseLong(p[3]), p[4]);
    }

    /** PM_FILE / PM_HISTORY_FILE: LENH|nguoiGui|nguoiNhan|thoiGian|maFile|tenFile|dungLuong */
    private static PrivateMessage toPrivateFile(String[] p) {
        if (p.length < 7) {
            throw new IllegalArgumentException("Goi tin thieu truong");
        }
        return PrivateMessage.ofFile(p[1], p[2], Long.parseLong(p[3]), new FileInfo(p[4], p[5], Long.parseLong(p[6])));
    }

    private static String translateFileError(String code, String fallback) {
        return switch (code) {
            case Protocol.ERR_FILE_TOO_LARGE -> "File vượt quá giới hạn "
                    + FileInfo.formatSize(Protocol.MAX_FILE_SIZE);
            case Protocol.ERR_FILE_BUSY -> "Đang có một file khác được truyền, hãy đợi xong";
            case Protocol.ERR_FILE_NOT_FOUND -> "File không còn trên server";
            case Protocol.ERR_NOT_IN_ROOM -> "Bạn cần vào phòng trước khi gửi file";
            case Protocol.ERR_USER_NOT_FOUND -> "Không có tài khoản người nhận";
            case Protocol.ERR_SELF_MESSAGE -> "Không thể gửi file cho chính mình";
            case Protocol.ERR_FILE_INVALID -> "File không hợp lệ: " + fallback;
            default -> fallback;
        };
    }

    private static long toLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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
            failAllTransfers("Mất kết nối với server");
            try {
                listener.onDisconnected(reason);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
