package vn.hutech.nhom06.server.ui;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import vn.hutech.nhom06.server.store.InMemoryMessageStore;
import vn.hutech.nhom06.server.store.JdbcMessageStore;
import vn.hutech.nhom06.server.store.MessageStore;

/**
 * Chay Server voi giao dien quan tri.
 * NetBeans: chuot phai file nay -> Run File.
 *
 * Luu tru: uu tien SQL Server (can file db.properties, xem db.properties.example).
 * Neu chua cau hinh CSDL thi tam luu trong RAM (mat lich su khi tat server).
 *
 * @author Nhom 06
 */
public class ServerAdminMain {

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try {
            // Giao dien giong ung dung cua he dieu hanh (Windows)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new ServerAdminFrame(ServerAdminMain::createStore).setVisible(true));
    }

    /** Duoc goi moi lan bam Start (tren thread nen). */
    private static MessageStore createStore() {
        try {
            return new JdbcMessageStore();
        } catch (RuntimeException e) {
            System.err.println("CANH BAO: khong dung duoc SQL Server - " + e.getMessage());
            System.err.println("=> Tam luu trong RAM, LICH SU SE MAT khi tat server.");
            return new InMemoryMessageStore();
        }
    }
}
