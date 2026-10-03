package vn.hutech.nhom06.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.common.RoomInfo;
import vn.hutech.nhom06.server.store.MessageStore;

/**
 * Server chat da luong.
 *
 * @author Ta Tuan Phat
 *
 * - 1 thread "accept" lang nghe ket noi moi tren ServerSocket.
 * - Moi client ket noi duoc phuc vu boi 1 thread rieng ({@link ClientHandler}).
 * - Quan ly danh sach nguoi dang online va cac phong ({@link RoomManager}).
 *
 * Su dung:
 *     ChatServer server = new ChatServer(5000, new InMemoryMessageStore());
 *     server.addListener(...);   // giao dien quan tri
 *     server.start();
 *     ...
 *     server.stop();
 */
public class ChatServer {

    public static final String DEFAULT_ROOM = "Phong chung";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final MessageStore store;
    private final RoomManager rooms = new RoomManager();
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>(); // ten thuong -> client
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();         // ke ca chua dang nhap
    private final List<ServerListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private Thread acceptThread;

    public ChatServer(int port, MessageStore store) {
        this.port = port;
        this.store = store;
    }

    // ================== Khoi dong / dung ==================

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(port);
        loadRooms();

        AtomicInteger counter = new AtomicInteger();
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "client-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });

        running = true;
        acceptThread = new Thread(this::acceptLoop, "accept-thread");
        acceptThread.start();

        log("Server da khoi dong tai cong " + port + " (" + rooms.count() + " phong)");
        listeners.forEach(l -> l.onServerStarted(port));
        fireRoomsChanged();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close(); // lam accept() nem exception -> thoat vong lap
        } catch (IOException ignored) {
        }
        for (ClientHandler c : new ArrayList<>(clients)) {
            c.disconnect("Server dung hoat dong");
        }
        pool.shutdownNow();
        store.close();
        log("Server da dung");
        listeners.forEach(ServerListener::onServerStopped);
    }

    private void loadRooms() {
        try {
            for (String name : store.loadRoomNames()) {
                rooms.create(name);
            }
        } catch (RuntimeException e) {
            log("Loi nap danh sach phong: " + e.getMessage());
        }
        if (rooms.get(DEFAULT_ROOM) == null) {
            rooms.create(DEFAULT_ROOM);
            safeStore(() -> store.saveRoom(DEFAULT_ROOM, "server"));
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                log("Ket noi moi tu " + handler.getAddress() + " (dang co " + clients.size() + " ket noi)");
                listeners.forEach(l -> l.onClientConnected(handler.getAddress()));
                pool.execute(handler);
            } catch (SocketException e) {
                if (running) {
                    log("Loi socket: " + e.getMessage());
                }
            } catch (IOException e) {
                log("Loi chap nhan ket noi: " + e.getMessage());
            }
        }
    }

    // ================== Dung boi ClientHandler ==================

    /** Dang ky ten. Tra ve false neu ten da co nguoi dung. */
    boolean registerUser(String username, ClientHandler handler) {
        return onlineUsers.putIfAbsent(key(username), handler) == null;
    }

    void userLoggedIn(ClientHandler handler) {
        safeStore(() -> store.saveUser(handler.getUsername()));
        log(handler.getUsername() + " dang nhap tu " + handler.getAddress());
        listeners.forEach(l -> l.onUserLoggedIn(handler.getUsername(), handler.getAddress()));
    }

    /** Goi khi ket noi dong (o thread cua client do). */
    void clientDisconnected(ClientHandler handler) {
        clients.remove(handler);
        String name = handler.getUsername();
        if (name != null) {
            onlineUsers.remove(key(name), handler);
            log(name + " da thoat");
        }
        log("Dong ket noi " + handler.getAddress() + " (con " + clients.size() + " ket noi)");
        listeners.forEach(l -> l.onClientDisconnected(handler.getAddress(), name));
    }

    /** Gui ROOM_LIST moi cho tat ca nguoi da dang nhap + bao giao dien quan tri. */
    void broadcastRoomList() {
        String line = roomListLine();
        for (ClientHandler c : onlineUsers.values()) {
            c.send(line);
        }
        fireRoomsChanged();
    }

    String roomListLine() {
        List<Object> fields = new ArrayList<>();
        for (RoomInfo r : rooms.infos()) {
            fields.add(r.name() + ":" + r.memberCount());
        }
        return Protocol.build(Protocol.ROOM_LIST, fields.toArray());
    }

    RoomManager rooms() {
        return rooms;
    }

    MessageStore store() {
        return store;
    }

    /** Goi CSDL, neu loi thi chi ghi log, khong lam sap server. */
    boolean safeStore(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            log("Loi luu tru: " + e.getMessage());
            return false;
        }
    }

    void log(String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] " + message;
        listeners.forEach(l -> l.onLog(line));
    }

    private void fireRoomsChanged() {
        List<RoomInfo> infos = rooms.infos();
        listeners.forEach(l -> l.onRoomsChanged(infos));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ================== Cho giao dien quan tri ==================

    public void addListener(ServerListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServerListener listener) {
        listeners.remove(listener);
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    /** Ten nhung nguoi dang online (sap xep A-Z). */
    public List<String> getOnlineUsers() {
        return onlineUsers.values().stream()
                .map(ClientHandler::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public int getConnectionCount() {
        return clients.size();
    }

    public List<RoomInfo> getRooms() {
        return rooms.infos();
    }
}
