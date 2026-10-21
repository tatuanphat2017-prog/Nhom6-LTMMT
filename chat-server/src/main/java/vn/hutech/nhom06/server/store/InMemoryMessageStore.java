package vn.hutech.nhom06.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.PrivateMessage;

/**
 * Luu tru tam trong RAM (mat khi tat server).
 * Dung de chay thu trong luc chua co CSDL.
 *
 * @author Ta Tuan Phat
 */
public class InMemoryMessageStore implements MessageStore {

    private final Map<String, String> rooms = new ConcurrentHashMap<>();          // key thuong -> ten goc
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
    private final Map<String, List<PrivateMessage>> privates = new ConcurrentHashMap<>(); // "a|b" (a<b) -> tin
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();             // ten thuong -> tai khoan
    // nguoi nhan (thuong) -> (nguoi gui -> so tin chua doc)
    private final Map<String, Map<String, Integer>> unread = new ConcurrentHashMap<>();
    // phong (thuong) -> (nguoi (ten goc) -> so tin da doc, tuc vi tri tin cuoi da doc)
    private final Map<String, Map<String, Integer>> roomReads = new ConcurrentHashMap<>();

    @Override
    public List<String> loadRoomNames() {
        return new ArrayList<>(rooms.values());
    }

    @Override
    public void saveUser(String username) {
        // khong can luu gi
    }

    @Override
    public Account findAccount(String username) {
        return accounts.get(key(username));
    }

    @Override
    public boolean createAccount(String username, String passwordHash, String passwordSalt) {
        return accounts.putIfAbsent(key(username), new Account(username, passwordHash, passwordSalt)) == null;
    }

    @Override
    public void saveRoom(String roomName, String createdBy) {
        rooms.putIfAbsent(key(roomName), roomName);
    }

    @Override
    public void saveMessage(ChatMessage message) {
        messages.computeIfAbsent(key(message.room()), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(message);
    }

    @Override
    public List<ChatMessage> loadHistory(String roomName, int limit) {
        List<ChatMessage> list = messages.get(key(roomName));
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            int from = Math.max(0, list.size() - limit);
            return new ArrayList<>(list.subList(from, list.size()));
        }
    }

    @Override
    public void savePrivateMessage(PrivateMessage message) {
        privates.computeIfAbsent(pairKey(message.sender(), message.receiver()),
                k -> Collections.synchronizedList(new ArrayList<>())).add(message);
        unread.computeIfAbsent(key(message.receiver()), k -> new ConcurrentHashMap<>())
                .merge(message.sender(), 1, Integer::sum);
    }

    @Override
    public List<PrivateMessage> loadPrivateHistory(String userA, String userB, int limit) {
        List<PrivateMessage> list = privates.get(pairKey(userA, userB));
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            int from = Math.max(0, list.size() - limit);
            return new ArrayList<>(list.subList(from, list.size()));
        }
    }

    @Override
    public Map<String, Integer> countUnread(String receiver) {
        Map<String, Integer> m = unread.get(key(receiver));
        return m == null ? Map.of() : new java.util.TreeMap<>(m);
    }

    @Override
    public void markRead(String receiver, String sender) {
        Map<String, Integer> m = unread.get(key(receiver));
        if (m != null) {
            m.keySet().removeIf(s -> s.equalsIgnoreCase(sender));
        }
    }

    @Override
    public boolean isLastPrivateRead(String sender, String receiver) {
        List<PrivateMessage> list = privates.get(pairKey(sender, receiver));
        if (list == null) {
            return false;
        }
        boolean sent;
        synchronized (list) {
            sent = list.stream().anyMatch(m -> m.sender().equalsIgnoreCase(sender));
        }
        Map<String, Integer> m = unread.get(key(receiver));
        boolean pending = m != null && m.keySet().stream().anyMatch(s -> s.equalsIgnoreCase(sender));
        return sent && !pending;
    }

    @Override
    public void markRoomRead(String user, String room) {
        List<ChatMessage> list = messages.get(key(room));
        int n = list == null ? 0 : list.size();
        Map<String, Integer> reads = roomReads.computeIfAbsent(key(room), k -> new ConcurrentHashMap<>());
        reads.keySet().removeIf(u -> u.equalsIgnoreCase(user) && !u.equals(user));
        reads.put(user, n);
    }

    @Override
    public Map<String, Integer> countRoomUnread(String user) {
        Map<String, Integer> result = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Map<String, Integer>> e : roomReads.entrySet()) {
            Integer read = null;
            for (Map.Entry<String, Integer> r : e.getValue().entrySet()) {
                if (r.getKey().equalsIgnoreCase(user)) {
                    read = r.getValue();
                }
            }
            List<ChatMessage> list = messages.get(e.getKey());
            if (read == null || list == null) {
                continue;
            }
            int count = 0;
            synchronized (list) {
                for (int i = read; i < list.size(); i++) {
                    if (!list.get(i).sender().equalsIgnoreCase(user)) {
                        count++;
                    }
                }
            }
            if (count > 0) {
                result.put(rooms.getOrDefault(e.getKey(), e.getKey()), count);
            }
        }
        return result;
    }

    @Override
    public List<String> loadRoomSeen(String room) {
        List<ChatMessage> list = messages.get(key(room));
        Map<String, Integer> reads = roomReads.get(key(room));
        if (list == null || list.isEmpty() || reads == null) {
            return List.of();
        }
        int size;
        String lastSender;
        synchronized (list) {
            size = list.size();
            lastSender = list.get(size - 1).sender();
        }
        return reads.entrySet().stream()
                .filter(e -> e.getValue() >= size && !e.getKey().equalsIgnoreCase(lastSender))
                .map(Map.Entry::getKey).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Override
    public List<String> loadRoomMembers(String room) {
        Map<String, Integer> reads = roomReads.get(key(room));
        return reads == null ? List.of() : new ArrayList<>(reads.keySet());
    }

    @Override
    public List<String> loadAccountNames() {
        return accounts.values().stream().map(Account::username)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Khoa cho 1 cap nguoi, khong phu thuoc ai gui: (Phat, Lang) va (lang, phat) -> "lang|phat". */
    private static String pairKey(String a, String b) {
        String x = key(a);
        String y = key(b);
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    private static String key(String room) {
        return room.toLowerCase(Locale.ROOT);
    }
}
