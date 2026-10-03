package vn.hutech.nhom06.server.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import vn.hutech.nhom06.common.Protocol;

/**
 * Client console de TEST server khi chua co giao dien Swing.
 * Chay: TestClient [host] [cong]   (mac dinh localhost 5000)
 *
 * Lenh tat:
 *   /login ten      /rooms        /create tenPhong   /join tenPhong
 *   /leave          /history [n]  /members           /raw LENH|...   /quit
 *   (go chu binh thuong = gui tin vao phong dang o)
 *
 * @author Ta Tuan Phat
 */
public class TestClient {

    private static volatile String currentRoom;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            System.out.println("Da ket noi " + host + ":" + port + ". Go /login <ten> de bat dau.");

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        print(Protocol.parse(line));
                    }
                } catch (IOException ignored) {
                }
                System.out.println("*** Mat ket noi voi server");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String input;
            while ((input = console.readLine()) != null) {
                String line = toProtocol(input.trim());
                if (line == null) {
                    continue;
                }
                out.write(line);
                out.write('\n');
                out.flush();
                if (line.equals(Protocol.LOGOUT)) {
                    break;
                }
            }
        }
    }

    private static String toProtocol(String s) {
        if (s.isEmpty()) {
            return null;
        }
        String[] a = s.split(" ", 2);
        String arg = a.length > 1 ? a[1].trim() : "";
        return switch (a[0].toLowerCase()) {
            case "/login" -> Protocol.build(Protocol.LOGIN, arg);
            case "/rooms" -> Protocol.build(Protocol.LIST_ROOMS);
            case "/create" -> Protocol.build(Protocol.CREATE_ROOM, arg);
            case "/join" -> Protocol.build(Protocol.JOIN, arg);
            case "/leave" -> Protocol.build(Protocol.LEAVE, currentRoom == null ? "" : currentRoom);
            case "/history" -> Protocol.build(Protocol.HISTORY, currentRoom == null ? "" : currentRoom,
                    arg.isEmpty() ? Protocol.DEFAULT_HISTORY_LIMIT : arg);
            case "/members" -> Protocol.build(Protocol.MEMBERS, currentRoom == null ? "" : currentRoom);
            case "/raw" -> arg;
            case "/quit" -> Protocol.LOGOUT;
            default -> {
                if (currentRoom == null) {
                    System.out.println("(Chua vao phong. Dung /join <tenPhong>)");
                    yield null;
                }
                yield Protocol.build(Protocol.MSG, currentRoom, s);
            }
        };
    }

    private static void print(String[] p) {
        String f1 = p.length > 1 ? p[1] : "";
        switch (p[0]) {
            case Protocol.MSG, Protocol.HISTORY_ITEM -> {
                String tag = p[0].equals(Protocol.HISTORY_ITEM) ? "(cu) " : "";
                String time = TIME.format(new Date(Long.parseLong(p[3])));
                System.out.println(tag + "[" + time + "] " + p[2] + ": " + p[4]);
            }
            case Protocol.JOINED -> {
                currentRoom = f1;
                System.out.println("=== Da vao phong: " + f1);
            }
            case Protocol.LEFT -> {
                currentRoom = null;
                System.out.println("=== Da roi phong: " + f1);
            }
            case Protocol.USER_JOINED -> System.out.println("--> " + p[2] + " vao phong");
            case Protocol.USER_LEFT -> System.out.println("<-- " + p[2] + " roi phong");
            case Protocol.ERROR -> System.out.println("!! Loi " + f1 + ": " + (p.length > 2 ? p[2] : ""));
            default -> System.out.println("<< " + String.join(" | ", p));
        }
    }
}
