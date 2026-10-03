package vn.hutech.nhom06.server;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Quan ly tat ca phong chat. Ten phong khong phan biet hoa/thuong
 * ("Phong A" va "phong a" la mot phong).
 *
 * @author Ta Tuan Phat
 */
public class RoomManager {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** Tao phong moi. Tra ve null neu phong da ton tai. */
    public Room create(String name) {
        Room room = new Room(name);
        return rooms.putIfAbsent(key(name), room) == null ? room : null;
    }

    public Room get(String name) {
        return name == null ? null : rooms.get(key(name));
    }

    public List<Room> all() {
        return rooms.values().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    public List<RoomInfo> infos() {
        return all().stream().map(r -> new RoomInfo(r.getName(), r.size())).toList();
    }

    public int count() {
        return rooms.size();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
