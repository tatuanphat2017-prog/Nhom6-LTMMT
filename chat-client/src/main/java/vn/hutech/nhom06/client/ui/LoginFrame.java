package vn.hutech.nhom06.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import vn.hutech.nhom06.client.net.ClientConnection;
import vn.hutech.nhom06.client.net.ClientEventAdapter;
import vn.hutech.nhom06.common.Protocol;

/**
 * Man hinh dang nhap: nhap IP server, cong, ten hien thi roi bam "Ket noi".
 *
 * Luong xu ly:
 *  1. Kiem tra nhanh tai cho (cong la so, ten dung dinh dang) de bao loi ngay.
 *  2. Ket noi TCP trong SwingWorker (khong lam dung giao dien khi sai IP).
 *  3. Gui LOGIN. Ket qua ve qua listener: LOGIN_OK -> mo MainFrame,
 *     ERROR (vd NAME_TAKEN) -> bao loi va cho nhap lai (giu nguyen ket noi).
 *
 * @author Nhom 06
 */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField txtHost;
    private final JTextField txtPort;
    private final JTextField txtName;
    private final JButton btnConnect = Theme.primaryButton("Kết nối");
    private final JLabel lblError = new JLabel(" ");

    private transient volatile ClientConnection connection;
    private String connectedHost;
    private int connectedPort;

    public LoginFrame() {
        this("localhost", Protocol.DEFAULT_PORT, "");
    }

    public LoginFrame(String host, int port, String name) {
        super("Chat Nhóm 06 - Đăng nhập");
        txtHost = Theme.textField(host, 18);
        txtPort = Theme.textField(String.valueOf(port), 18);
        txtName = Theme.textField(name, 18);
        txtHost.setName("txtHost");
        txtPort.setName("txtPort");
        txtName.setName("txtName");
        btnConnect.setName("btnConnect");
        lblError.setName("lblError");

        buildUi();
        btnConnect.addActionListener(e -> onConnect());
        getRootPane().setDefaultButton(btnConnect);   // Enter o bat ky o nao = Ket noi

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.BG);
        card.setBorder(BorderFactory.createEmptyBorder(28, 36, 28, 36));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JLabel title = new JLabel("Chat Nhóm 06");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.PRIMARY);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 2, 0);
        card.add(title, c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 18, 0);
        card.add(Theme.muted("Đăng nhập để bắt đầu trò chuyện"), c);

        addField(card, c, 2, "Địa chỉ IP server", txtHost);
        addField(card, c, 4, "Cổng", txtPort);
        addField(card, c, 6, "Tên hiển thị", txtName);

        c.gridy = 8;
        c.insets = new Insets(2, 0, 10, 0);
        card.add(Theme.muted("Tên 1-20 ký tự: chữ, số, _ . - (không khoảng trắng)"), c);

        lblError.setFont(Theme.FONT_SMALL);
        lblError.setForeground(Theme.ERROR);
        c.gridy = 9;
        c.insets = new Insets(0, 0, 10, 0);
        card.add(lblError, c);

        btnConnect.setPreferredSize(new Dimension(280, 40));
        c.gridy = 10;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(btnConnect, c);

        getContentPane().setBackground(Theme.BG);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(card, BorderLayout.CENTER);
    }

    private static void addField(JPanel card, GridBagConstraints c, int row, String label, JTextField field) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.FONT_SMALL);
        l.setForeground(Theme.TEXT);
        c.gridy = row;
        c.insets = new Insets(0, 0, 4, 0);
        card.add(l, c);
        c.gridy = row + 1;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(field, c);
    }

    // ===================== Ket noi + dang nhap =====================

    private void onConnect() {
        String host = txtHost.getText().trim();
        String name = txtName.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            showError("Cổng phải là số từ 1 đến 65535");
            txtPort.requestFocus();
            return;
        }
        if (host.isEmpty()) {
            showError("Vui lòng nhập địa chỉ IP server");
            txtHost.requestFocus();
            return;
        }
        if (!Protocol.isValidName(name)) {
            showError(name.isEmpty() ? "Vui lòng nhập tên hiển thị"
                    : "Tên không hợp lệ: 1-20 ký tự, không khoảng trắng");
            txtName.requestFocus();
            return;
        }

        setBusy(true, "Đang kết nối...");
        final int p = port;
        new SwingWorker<Void, Void>() {
            private IOException failure;

            @Override
            protected Void doInBackground() {
                try {
                    // Doi IP/cong so voi lan truoc thi dong ket noi cu, mo ket noi moi
                    if (connection != null && (!connection.isConnected()
                            || !host.equals(connectedHost) || p != connectedPort)) {
                        connection.close();
                        connection = null;
                    }
                    if (connection == null) {
                        ClientConnection conn = new ClientConnection();
                        conn.setListener(new LoginListener());
                        conn.connect(host, p);
                        connection = conn;
                        connectedHost = host;
                        connectedPort = p;
                    }
                    connection.login(name);   // ket qua ve qua LoginListener
                } catch (IOException e) {
                    failure = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    setBusy(false, null);
                    showError(describe(failure, host, p));
                } else if (!btnConnect.isEnabled()) {
                    // Chi hien "Dang dang nhap" neu server chua tra loi
                    // (tren mang LAN, loi NAME_TAKEN co the ve truoc ca dong nay)
                    lblError.setText("Đang đăng nhập...");
                    lblError.setForeground(Theme.MUTED);
                }
            }
        }.execute();
    }

    private static String describe(IOException e, String host, int port) {
        if (e instanceof UnknownHostException) {
            return "Không tìm thấy máy chủ \"" + host + "\"";
        }
        if (e instanceof SocketTimeoutException) {
            return "Hết thời gian chờ kết nối tới " + host + ":" + port;
        }
        if (e instanceof ConnectException) {
            return "Không kết nối được " + host + ":" + port + " (server chưa bật?)";
        }
        return "Lỗi kết nối: " + e.getMessage();
    }

    /** Dang nhap thanh cong: chuyen sang MainFrame, giu nguyen ket noi. */
    private void openMain(String username) {
        MainFrame main = new MainFrame(connection, username, connectedHost, connectedPort);
        connection.setListener(main);
        main.setVisible(true);
        connection.listRooms();   // lay lai danh sach phong cho MainFrame
        connection = null;         // MainFrame chiu trach nhiem ket noi tu day
        dispose();
    }

    private void setBusy(boolean busy, String message) {
        btnConnect.setEnabled(!busy);
        txtHost.setEnabled(!busy);
        txtPort.setEnabled(!busy);
        txtName.setEnabled(!busy);
        if (message != null) {
            lblError.setForeground(Theme.MUTED);
            lblError.setText(message);
        }
    }

    private void showError(String message) {
        lblError.setForeground(Theme.ERROR);
        // Bo trong HTML de tu xuong dong khi thong bao dai
        lblError.setText("<html><div style='width:250px'>" + message.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
        pack();
    }

    /** Su kien tu server trong luc o man hinh dang nhap (goi tu thread nhan tin). */
    private final class LoginListener extends ClientEventAdapter {

        @Override
        public void onLoginOk(String username) {
            SwingUtilities.invokeLater(() -> openMain(username));
        }

        @Override
        public void onError(String code, String message) {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, null);
                switch (code) {
                    case Protocol.ERR_NAME_TAKEN ->
                        showError("Tên \"" + txtName.getText().trim() + "\" đang có người dùng, hãy chọn tên khác");
                    case Protocol.ERR_INVALID_NAME ->
                        showError("Tên không hợp lệ: 1-20 ký tự, không khoảng trắng");
                    default -> showError(message);
                }
                txtName.requestFocus();
                txtName.selectAll();
            });
        }

        @Override
        public void onDisconnected(String reason) {
            SwingUtilities.invokeLater(() -> {
                connection = null;
                setBusy(false, null);
                showError("Mất kết nối với server: " + reason);
            });
        }
    }
}
