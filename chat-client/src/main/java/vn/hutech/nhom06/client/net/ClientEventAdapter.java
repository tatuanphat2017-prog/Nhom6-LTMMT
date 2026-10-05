package vn.hutech.nhom06.client.net;

import java.util.List;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Cai dat rong cua {@link ClientEventListener}.
 * Dung khi chi quan tam vai su kien (vd LoginFrame chi can onLoginOk, onError, onDisconnected):
 *
 *     connection.setListener(new ClientEventAdapter() {
 *         @Override public void onLoginOk(String username) { ... }
 *     });
 *
 * @author Tran Lang
 */
public class ClientEventAdapter implements ClientEventListener {

    @Override
    public void onLoginOk(String username) {
    }

    @Override
    public void onRoomList(List<RoomInfo> rooms) {
    }

    @Override
    public void onJoined(String room) {
    }

    @Override
    public void onHistoryItem(ChatMessage message) {
    }

    @Override
    public void onHistoryEnd(String room, int count) {
    }

    @Override
    public void onMessage(ChatMessage message) {
    }

    @Override
    public void onMembers(String room, List<String> members) {
    }

    @Override
    public void onUserJoined(String room, String username) {
    }

    @Override
    public void onUserLeft(String room, String username) {
    }

    @Override
    public void onLeft(String room) {
    }

    @Override
    public void onError(String code, String message) {
    }

    @Override
    public void onDisconnected(String reason) {
    }
}
