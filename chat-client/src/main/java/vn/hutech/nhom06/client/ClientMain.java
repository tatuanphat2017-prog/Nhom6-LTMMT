package vn.hutech.nhom06.client;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import vn.hutech.nhom06.client.ui.LoginFrame;
import vn.hutech.nhom06.common.Protocol;

/**
 * Diem khoi chay Client (giao dien Swing).
 * NetBeans: chuot phai file nay -> Run File.
 * Tham so (khong bat buoc): [ip server] [cong]   mac dinh localhost 5000
 *
 * Cau truc:
 *  - client.ui  : giao dien (LoginFrame, MainFrame, Theme)
 *  - client.net : ket noi mang (ClientConnection, ClientEventListener)
 *
 * @author Nhom 06
 */
public class ClientMain {

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        try {
            // Hop thoai (JOptionPane) giong ung dung Windows
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new LoginFrame(host, port, "").setVisible(true));
    }
}
