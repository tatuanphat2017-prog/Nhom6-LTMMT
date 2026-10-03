package vn.hutech.nhom06.server;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mot phong chat va cac thanh vien dang o trong phong.
 *
 * @author Ta Tuan Phat
 */
public class Room {

    private final String name;
    private final Set<ClientHandler> members = ConcurrentHashMap.newKeySet();

    public Room(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    void add(ClientHandler client) {
        members.add(client);
    }

    void remove(ClientHandler client) {
        members.remove(client);
    }

    public int size() {
        return members.size();
    }

    public List<String> memberNames() {
        return members.stream()
                .map(ClientHandler::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Gui mot goi tin toi tat ca thanh vien, tru {@code exclude} (co the null). */
    void broadcast(String line, ClientHandler exclude) {
        for (ClientHandler c : members) {
            if (c != exclude) {
                c.send(line);
            }
        }
    }
}
