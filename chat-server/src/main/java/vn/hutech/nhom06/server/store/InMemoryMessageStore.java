package vn.hutech.nhom06.server.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.hutech.nhom06.common.ChatMessage;

/**
 * Luu tru tam trong RAM (mat khi tat server).
 * Dung de chay thu trong luc chua co CSDL.
 *
 * @author Ta Tuan Phat
 */
public class InMemoryMessageStore implements MessageStore {

    private final Map<String, String> rooms = new ConcurrentHashMap<>();          // key thuong -> ten goc
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();

    @Override
    public List<String> loadRoomNames() {
        return new ArrayList<>(rooms.values());
    }

    @Override
    public void saveUser(String username) {
        // khong can luu gi
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

    private static String key(String room) {
        return room.toLowerCase(Locale.ROOT);
    }
}
