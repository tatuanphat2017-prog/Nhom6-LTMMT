package vn.hutech.nhom06.client.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Chuong trinh console de THU ClientConnection khi chua co giao dien cua Huy,
 * dong thoi la vi du mau cach dung ClientConnection + ClientEventListener.
 * Chay: ConsoleDemo [host] [cong]   (mac dinh localhost 5000)
 *
 * Lenh: /login ten | /rooms | /create phong | /join phong | /leave
 *       /history n | /members | /quit | (go chu thuong = gui tin)
 *
 * @author Tran Lang
 */
public class ConsoleDemo {

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        SimpleDateFormat time = new SimpleDateFormat("HH:mm");

        ClientConnection conn = new ClientConnection();
        conn.setListener(new ClientEventListener() {
            @Override
            public void onLoginOk(String username) {
                System.out.println("=== Dang nhap thanh cong: " + username);
            }

            @Override
            public void onRoomList(List<RoomInfo> rooms) {
                System.out.println("=== Phong: " + rooms);
            }

            @Override
            public void onJoined(String room) {
                System.out.println("=== Da vao phong: " + room);
            }

            @Override
            public void onHistoryItem(ChatMessage m) {
                System.out.println("(cu) " + format(m));
            }

            @Override
            public void onHistoryEnd(String room, int count) {
                System.out.println("=== Het lich su (" + count + " tin)");
            }

            @Override
            public void onMessage(ChatMessage m) {
                System.out.println(format(m));
            }

            @Override
            public void onMembers(String room, List<String> members) {
                System.out.println("=== Thanh vien " + room + ": " + members);
            }

            @Override
            public void onUserJoined(String room, String username) {
                System.out.println("--> " + username + " vao phong");
            }

            @Override
            public void onUserLeft(String room, String username) {
                System.out.println("<-- " + username + " roi phong");
            }

            @Override
            public void onLeft(String room) {
                System.out.println("=== Da roi phong: " + room);
            }

            @Override
            public void onError(String code, String message) {
                System.out.println("!! Loi " + code + ": " + message);
            }

            @Override
            public void onDisconnected(String reason) {
                System.out.println("*** Ngat ket noi: " + reason);
                System.exit(0);
            }

            private String format(ChatMessage m) {
                return "[" + time.format(new Date(m.timestamp())) + "] " + m.sender() + ": " + m.content();
            }
        });

        try {
            conn.connect(host, port);
        } catch (IOException e) {
            System.out.println("Khong ket noi duoc " + host + ":" + port + " - " + e.getMessage());
            return;
        }
        System.out.println("Da ket noi. Go /login <ten> de bat dau.");

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String input;
        while ((input = console.readLine()) != null) {
            String s = input.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] a = s.split(" ", 2);
            String arg = a.length > 1 ? a[1].trim() : "";
            switch (a[0].toLowerCase()) {
                case "/login" -> conn.login(arg);
                case "/rooms" -> conn.listRooms();
                case "/create" -> conn.createRoom(arg);
                case "/join" -> conn.join(arg);
                case "/leave" -> conn.leave();
                case "/history" -> conn.requestHistory(arg.isEmpty() ? Protocol.DEFAULT_HISTORY_LIMIT : Integer.parseInt(arg));
                case "/members" -> conn.requestMembers();
                case "/quit" -> conn.logout();
                default -> conn.sendMessage(s);
            }
        }
    }
}
