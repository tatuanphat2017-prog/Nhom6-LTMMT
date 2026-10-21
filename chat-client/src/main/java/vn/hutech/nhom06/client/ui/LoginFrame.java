package vn.hutech.nhom06.client.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import vn.hutech.nhom06.client.net.ClientConnection;
import vn.hutech.nhom06.client.net.ClientEventAdapter;
import vn.hutech.nhom06.common.Protocol;

/**
 * Man hinh dang nhap / dang ky.
 *
 *  - Che do DANG NHAP: Ten dang nhap, Mat khau.
 *    Dia chi server (IP + cong) an di, chi hien 1 dong "Server: ip:cong · Doi server";
 *    bam "Doi server" (hoac khi ket noi loi) moi hien 2 o IP / Cong de sua.
 *  - Che do DANG KY  : them o "Nhap lai mat khau". Dang ky thanh cong = vao chat luon.
 *  Bam dong chu "Chua co tai khoan? Dang ky" / "Da co tai khoan? Dang nhap" de doi che do.
 *
 * Luong xu ly:
 *  1. Kiem tra nhanh tai cho (cong, ten, mat khau, 2 mat khau trung nhau) de bao loi ngay.
 *  2. Ket noi TCP trong SwingWorker (khong lam dung giao dien khi sai IP).
 *  3. Gui LOGIN / REGISTER. Ket qua ve qua listener: LOGIN_OK -> mo MainFrame,
 *     ERROR (WRONG_LOGIN, USER_EXISTS, NAME_TAKEN...) -> bao loi, cho nhap lai (giu ket noi).
 *
 * Ghi nho tren may (java.util.prefs): IP, cong va ten dang nhap lan truoc. KHONG ghi nho mat khau.
 *
 * @author Nhom 06
 */
public class LoginFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Preferences PREFS = Preferences.userRoot().node("vn/hutech/nhom06/chatclient");

    private final JTextField txtHost;
    private final JTextField txtPort;
    private final JTextField txtName;
    private final JPasswordField txtPassword = new JPasswordField(18);
    private final JPasswordField txtConfirm = new JPasswordField(18);
    private final JCheckBox chkShow = new JCheckBox("Hiện mật khẩu");
    private final JButton btnSubmit = Theme.primaryButton("Đăng nhập");
    private final JLabel lblTitle = new JLabel("Đăng nhập");
    private final JLabel lblSubtitle = Theme.muted("Nhập tài khoản để bắt đầu trò chuyện");
    private final JLabel lblSwitch = new JLabel();
    private final JLabel lblError = new JLabel(" ");
    private final JLabel lblConfirm = new JLabel("Nhập lại mật khẩu");
    private final JLabel lblNameHint = Theme.muted(" ");
    private final JLabel lblServer = new JLabel();                 // "Server: ip:cong · Doi server"
    private final JPanel serverPanel = new JPanel(new GridBagLayout());   // 2 o IP + Cong (an mac dinh)

    private boolean registerMode;
    private final char echoChar;

    private transient volatile ClientConnection connection;
    private String connectedHost;
    private int connectedPort;

    public LoginFrame() {
        this(null, Protocol.DEFAULT_PORT, null);
    }

    /**
     * @param host IP server; null = lay IP da nho lan truoc (mac dinh localhost)
     * @param name ten dang nhap; null hoac rong = lay ten da nho lan truoc
     */
    public LoginFrame(String host, int port, String name) {
        super("Chat Nhóm 06 - Đăng nhập");
        txtHost = Theme.textField(host != null ? host : PREFS.get("host", "localhost"), 18);
        txtPort = Theme.textField(String.valueOf(host != null ? port : PREFS.getInt("port", port)), 18);
        txtName = Theme.textField(name != null && !name.isEmpty() ? name : PREFS.get("name", ""), 18);
        Theme.styleInput(txtPassword);
        Theme.styleInput(txtConfirm);
        echoChar = txtPassword.getEchoChar();
        txtHost.setName("txtHost");
        txtPort.setName("txtPort");
        txtName.setName("txtName");
        txtPassword.setName("txtPassword");
        txtConfirm.setName("txtConfirm");
        btnSubmit.setName("btnConnect");
        lblError.setName("lblError");
        lblSwitch.setName("lblSwitch");
        lblServer.setName("lblServer");

        buildUi();
        btnSubmit.addActionListener(e -> onSubmit());
        getRootPane().setDefaultButton(btnSubmit);   // Enter o bat ky o nao = Dang nhap / Dang ky
        chkShow.addActionListener(e -> {
            char c = chkShow.isSelected() ? (char) 0 : echoChar;
            txtPassword.setEchoChar(c);
            txtConfirm.setEchoChar(c);
        });
        lblSwitch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (btnSubmit.isEnabled()) {
                    setRegisterMode(!registerMode);
                }
            }
        });
        lblServer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (btnSubmit.isEnabled()) {
                    showServerFields(!serverPanel.isVisible());
                }
            }
        });
        DocumentListener refresh = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateServerLabel();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateServerLabel();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateServerLabel();
            }
        };
        txtHost.getDocument().addDocumentListener(refresh);
        txtPort.getDocument().addDocumentListener(refresh);
        setRegisterMode(false);
        // Chua tung ket noi thanh cong (va khong truyen IP tu ChayClient.bat): hien san o IP de nhap
        showServerFields(host == null && PREFS.get("host", null) == null);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        pack();
        setLocationRelativeTo(null);
        SwingUtilities.invokeLater(() -> (txtName.getText().isEmpty() ? txtName : txtPassword).requestFocusInWindow());
    }

    private void buildUi() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        JLabel app = new JLabel("Chat Nhóm 06");
        app.setFont(Theme.FONT_BOLD);
        app.setForeground(Theme.PRIMARY);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 2, 0);
        card.add(app, c);

        lblTitle.setFont(Theme.FONT_TITLE);
        lblTitle.setForeground(Theme.TEXT);
        c.gridy = 1;
        card.add(lblTitle, c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 16, 0);
        card.add(lblSubtitle, c);

        // IP + cong tren cung 1 hang (an mac dinh, bam "Doi server" de hien)
        JPanel server = serverPanel;
        server.setOpaque(false);
        GridBagConstraints s = new GridBagConstraints();
        s.fill = GridBagConstraints.HORIZONTAL;
        s.gridy = 0;
        s.gridx = 0;
        s.weightx = 1;
        s.insets = new Insets(0, 0, 4, 8);
        server.add(label("Địa chỉ IP server"), s);
        s.gridx = 1;
        s.weightx = 0;
        s.insets = new Insets(0, 0, 4, 0);
        server.add(label("Cổng"), s);
        s.gridy = 1;
        s.gridx = 0;
        s.weightx = 1;
        s.insets = new Insets(0, 0, 0, 8);
        server.add(txtHost, s);
        s.gridx = 1;
        s.weightx = 0;
        s.insets = new Insets(0, 0, 0, 0);
        txtPort.setColumns(5);
        server.add(txtPort, s);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(server, c);

        int row = addField(card, c, 4, label("Tên đăng nhập"), txtName);
        c.gridy = row++;
        c.insets = new Insets(-8, 0, 10, 0);
        card.add(lblNameHint, c);
        row = addField(card, c, row, label("Mật khẩu"), txtPassword);
        lblConfirm.setFont(Theme.FONT_SMALL);
        lblConfirm.setForeground(Theme.TEXT);
        row = addField(card, c, row, lblConfirm, txtConfirm);

        chkShow.setOpaque(false);
        chkShow.setFont(Theme.FONT_SMALL);
        chkShow.setForeground(Theme.MUTED);
        chkShow.setFocusPainted(false);
        c.gridy = row++;
        c.insets = new Insets(-6, 0, 8, 0);
        card.add(chkShow, c);

        lblError.setFont(Theme.FONT_SMALL);
        lblError.setForeground(Theme.ERROR);
        c.gridy = row++;
        c.insets = new Insets(0, 0, 10, 0);
        card.add(lblError, c);

        btnSubmit.setPreferredSize(new Dimension(300, 40));
        c.gridy = row++;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(btnSubmit, c);

        lblSwitch.setFont(Theme.FONT_SMALL);
        lblSwitch.setForeground(Theme.TEXT);
        lblSwitch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JPanel sw = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        sw.setOpaque(false);
        sw.add(lblSwitch);
        c.gridy = row++;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(sw, c);

        // Dong "Server: ip:cong · Doi server" o cuoi the
        lblServer.setFont(Theme.FONT_SMALL);
        lblServer.setForeground(Theme.MUTED);
        lblServer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lblServer.setToolTipText("Đổi địa chỉ IP / cổng của server");
        JPanel sv = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        sv.setOpaque(false);
        sv.add(lblServer);
        c.gridy = row;
        c.insets = new Insets(14, 0, 0, 0);
        card.add(sv, c);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(Theme.WINDOW);
        outer.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        outer.add(card, BorderLayout.CENTER);
        getContentPane().setBackground(Theme.WINDOW);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(outer, BorderLayout.CENTER);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL);
        l.setForeground(Theme.TEXT);
        return l;
    }

    /** Them nhan + o nhap, tra ve hang tiep theo. */
    private static int addField(JPanel card, GridBagConstraints c, int row, JLabel label, JComponent field) {
        c.gridy = row;
        c.insets = new Insets(0, 0, 4, 0);
        card.add(label, c);
        c.gridy = row + 1;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(field, c);
        return row + 2;
    }

    /** Hien / an 2 o IP + Cong. */
    private void showServerFields(boolean show) {
        serverPanel.setVisible(show);
        updateServerLabel();
        pack();
        if (show) {
            txtHost.requestFocusInWindow();
        }
    }

    private void updateServerLabel() {
        String addr = escapeHtml(txtHost.getText().trim()) + ":" + escapeHtml(txtPort.getText().trim());
        lblServer.setText("<html>Server: " + addr + " &nbsp;·&nbsp; <font color='#1E6FD9'>"
                + (serverPanel.isVisible() ? "Ẩn" : "Đổi server") + "</font></html>");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Doi giua Dang nhap / Dang ky. */
    private void setRegisterMode(boolean register) {
        registerMode = register;
        lblTitle.setText(register ? "Tạo tài khoản" : "Đăng nhập");
        lblSubtitle.setText(register ? "Đăng ký xong sẽ vào phòng chat ngay"
                : "Nhập tài khoản để bắt đầu trò chuyện");
        btnSubmit.setText(register ? "Đăng ký" : "Đăng nhập");
        lblConfirm.setVisible(register);
        txtConfirm.setVisible(register);
        txtConfirm.setText("");
        lblNameHint.setText(register ? "1-20 ký tự: chữ, số, _ . - (không khoảng trắng). Mật khẩu từ "
                + Protocol.MIN_PASSWORD_LENGTH + " ký tự" : " ");
        lblNameHint.setVisible(register);
        lblSwitch.setText(register
                ? "<html>Đã có tài khoản? <b><font color='#1E6FD9'>Đăng nhập</font></b></html>"
                : "<html>Chưa có tài khoản? <b><font color='#1E6FD9'>Đăng ký</font></b></html>");
        setTitle("Chat Nhóm 06 - " + (register ? "Đăng ký" : "Đăng nhập"));
        lblError.setText(" ");
        pack();
    }

    // ===================== Ket noi + dang nhap / dang ky =====================

    private void onSubmit() {
        String host = txtHost.getText().trim();
        String name = txtName.getText().trim();
        char[] pw = txtPassword.getPassword();
        String password = new String(pw);
        Arrays.fill(pw, '\0');
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            showServerFields(true);
            showError("Cổng phải là số từ 1 đến 65535");
            txtPort.requestFocus();
            return;
        }
        if (host.isEmpty()) {
            showServerFields(true);
            showError("Vui lòng nhập địa chỉ IP server");
            txtHost.requestFocus();
            return;
        }
        if (!Protocol.isValidName(name)) {
            showError(name.isEmpty() ? "Vui lòng nhập tên đăng nhập"
                    : "Tên không hợp lệ: 1-20 ký tự, không khoảng trắng");
            txtName.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showError("Vui lòng nhập mật khẩu");
            txtPassword.requestFocus();
            return;
        }
        if (registerMode) {
            if (!Protocol.isValidPassword(password)) {
                showError("Mật khẩu phải từ " + Protocol.MIN_PASSWORD_LENGTH + " đến "
                        + Protocol.MAX_PASSWORD_LENGTH + " ký tự");
                txtPassword.requestFocus();
                return;
            }
            if (!password.equals(new String(txtConfirm.getPassword()))) {
                showError("Hai mật khẩu không giống nhau");
                txtConfirm.requestFocus();
                txtConfirm.selectAll();
                return;
            }
        }

        final boolean register = registerMode;
        setBusy(true, "Đang kết nối...");
        final int p = port;
        new SwingWorker<Void, Void>() {
            private IOException failure;

            @Override
            protected Void doInBackground() {
                try {
                    // Doi IP/cong so voi lan truoc (hoac server da ngat) thi mo ket noi moi
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
                    if (register) {
                        connection.register(name, password);   // ket qua ve qua LoginListener
                    } else {
                        connection.login(name, password);
                    }
                } catch (IOException e) {
                    failure = e;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    setBusy(false, null);
                    showServerFields(true);   // ket noi loi: mo san o IP / cong de sua
                    showError(describe(failure, host, p));
                } else if (!btnSubmit.isEnabled()) {
                    // Chi hien dong nay neu server chua tra loi (tren LAN loi co the ve truoc ca dong nay)
                    lblError.setText(register ? "Đang tạo tài khoản..." : "Đang đăng nhập...");
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

    /** Dang nhap thanh cong: nho IP/cong/ten, chuyen sang MainFrame, giu nguyen ket noi. */
    private void openMain(String username) {
        PREFS.put("host", connectedHost);
        PREFS.putInt("port", connectedPort);
        PREFS.put("name", username);
        txtPassword.setText("");
        txtConfirm.setText("");
        MainFrame main = new MainFrame(connection, username, connectedHost, connectedPort);
        connection.setListener(main);
        main.setVisible(true);
        connection.listRooms();   // lay lai danh sach phong cho MainFrame
        connection.listOnline();  // va danh sach nguoi online (goi luc dang nhap co the da den LoginFrame)
        connection.listUsers();   // moi tai khoan (ca offline)
        connection.listUnread();  // tin rieng nhan duoc luc offline -> so do
        connection.listRoomUnread(); // tin moi trong cac phong tung vao -> so do
        connection = null;         // MainFrame chiu trach nhiem ket noi tu day
        dispose();
    }

    private void setBusy(boolean busy, String message) {
        btnSubmit.setEnabled(!busy);
        txtHost.setEnabled(!busy);
        txtPort.setEnabled(!busy);
        txtName.setEnabled(!busy);
        txtPassword.setEnabled(!busy);
        txtConfirm.setEnabled(!busy);
        if (message != null) {
            lblError.setForeground(Theme.MUTED);
            lblError.setText(message);
        }
    }

    private void showError(String message) {
        lblError.setForeground(Theme.ERROR);
        // Boc trong HTML de tu xuong dong khi thong bao dai
        lblError.setText("<html><div style='width:270px'>" + message.replace("&", "&amp;")
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
                String name = txtName.getText().trim();
                JComponent focus = txtName;
                switch (code) {
                    case Protocol.ERR_WRONG_LOGIN -> {
                        showError("Sai tên đăng nhập hoặc mật khẩu");
                        focus = txtPassword;
                    }
                    case Protocol.ERR_USER_EXISTS ->
                        showError("Tên \"" + name + "\" đã có người đăng ký, hãy chọn tên khác");
                    case Protocol.ERR_NAME_TAKEN ->
                        showError("Tài khoản \"" + name + "\" đang đăng nhập ở máy khác");
                    case Protocol.ERR_INVALID_NAME ->
                        showError("Tên không hợp lệ: 1-20 ký tự, không khoảng trắng");
                    case Protocol.ERR_INVALID_PASSWORD -> {
                        showError("Mật khẩu phải từ " + Protocol.MIN_PASSWORD_LENGTH + " đến "
                                + Protocol.MAX_PASSWORD_LENGTH + " ký tự");
                        focus = txtPassword;
                    }
                    default -> showError(message);
                }
                focus.requestFocus();
                if (focus instanceof JTextField t) {
                    t.selectAll();
                }
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
