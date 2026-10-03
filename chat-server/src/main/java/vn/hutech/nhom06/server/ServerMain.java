package vn.hutech.nhom06.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.server.store.InMemoryMessageStore;

/**
 * Chay server o che do console (chua co giao dien).
 * Tham so: [cong] (mac dinh 5000).
 * Go lenh trong cua so Output: status | stop
 *
 * Sau nay: BAO lam ServerAdminFrame, LANG doi InMemoryMessageStore -> JdbcMessageStore.
 *
 * @author Ta Tuan Phat
 */
public class ServerMain {

    public static void main(String[] args) throws IOException {
        // In tieng Viet co dau dung trong cua so Output
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        int port = Protocol.DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        ChatServer server = new ChatServer(port, new InMemoryMessageStore());
        server.addListener(new ServerListener() {
            @Override
            public void onLog(String message) {
                System.out.println(message);
            }
        });

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Khong mo duoc cong " + port + ": " + e.getMessage());
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Go 'status' de xem trang thai, 'stop' de dung server.");

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String cmd;
        while ((cmd = console.readLine()) != null) {
            switch (cmd.trim().toLowerCase()) {
                case "stop" -> {
                    server.stop();
                    return;
                }
                case "status" -> {
                    System.out.println("Ket noi: " + server.getConnectionCount()
                            + " | Online: " + server.getOnlineUsers());
                    server.getRooms().forEach(r ->
                            System.out.println("  - " + r.name() + " (" + r.memberCount() + " nguoi)"));
                }
                case "" -> {
                }
                default -> System.out.println("Lenh: status | stop");
            }
        }
        // Khong co console (chay nen): giu server chay cho toi khi bi tat
        while (server.isRunning()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
