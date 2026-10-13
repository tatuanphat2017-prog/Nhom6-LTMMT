package vn.hutech.nhom06.server.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.common.RoomInfo;
import vn.hutech.nhom06.server.ChatServer;
import vn.hutech.nhom06.server.ServerListener;
import vn.hutech.nhom06.server.store.MessageStore;

/**
 * Giao dien quan tri Server.
 *
 * Bo cuc:
 *   Tren : o Cong, nut Start/Stop, trang thai, so ket noi, kieu luu tru
 *   Trai : khung Nhat ky
 *   Phai : danh sach nguoi online + bang phong (ten, so nguoi)
 *
 * Moi lan bam Start se tao 1 ChatServer MOI (va 1 MessageStore moi tu storeFactory),
 * vi stop() da dong store cua lan chay truoc.
 *
 * Cac ham cua ServerListener duoc goi tu thread mang, nen moi lenh cap nhat
 * giao dien deu boc trong SwingUtilities.invokeLater(...).
 * Start/Stop chay trong SwingWorker de khong lam dung giao dien
 * (ket noi CSDL va dong ket noi client co the mat vai giay).
 *
 * @author Nhom 06
 */
public class ServerAdminFrame extends JFrame {

    private static final Color GREEN = new Color(0, 140, 60);
    private static final Color RED = new Color(190, 30, 30);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final transient Supplier<MessageStore> storeFactory;
    private transient volatile ChatServer server;

    // ----- Thanh dieu khien -----
    private final JTextField txtPort = new JTextField(String.valueOf(Protocol.DEFAULT_PORT), 6);
    private final JButton btnStart = new JButton("Start");
    private final JButton btnStop = new JButton("Stop");
    private final JLabel lblStatus = new JLabel();
    private final JLabel lblConnections = new JLabel("Kết nối: 0");
    private final JLabel lblStore = new JLabel("Lưu trữ: -");

    // ----- Nhat ky -----
    private final JTextArea txtLog = new JTextArea();

    // ----- Nguoi online -----
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> lstOnline = new JList<>(onlineModel);
    private final JLabel lblOnline = new JLabel("Người online (0)");

    // ----- Bang phong -----
    private final DefaultTableModel roomModel = new DefaultTableModel(new Object[]{"Tên phòng", "Số người"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? Integer.class : String.class;
        }
    };
    private final JTable tblRooms = new JTable(roomModel);
    private final JLabel lblRooms = new JLabel("Phòng (0)");

    public ServerAdminFrame(Supplier<MessageStore> storeFactory) {
        super("Quản trị Server Chat - Nhóm 06");
        this.storeFactory = storeFactory;
        buildUi();
        setRunningState(false);

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        txtPort.addActionListener(e -> startServer());   // Enter trong o Cong = Start

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClosing();
            }
        });
        setSize(980, 600);
        setMinimumSize(new Dimension(760, 420));
        setLocationRelativeTo(null);
    }

    // ===================== Dung giao dien =====================

    private void buildUi() {
        // Tren: dieu khien
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.add(new JLabel("Cổng:"));
        top.add(txtPort);
        top.add(btnStart);
        top.add(btnStop);
        top.add(new JLabel("  "));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.BOLD));
        top.add(lblStatus);
        top.add(new JLabel("  |  "));
        top.add(lblConnections);
        top.add(new JLabel("  |  "));
        top.add(lblStore);

        // Trai: nhat ky
        txtLog.setEditable(false);
        txtLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        txtLog.setLineWrap(true);
        ((DefaultCaret) txtLog.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // tu cuon xuong cuoi
        JPanel logPanel = new JPanel(new BorderLayout(0, 4));
        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.add(new JLabel("Nhật ký"), BorderLayout.WEST);
        JButton btnClear = new JButton("Xóa nhật ký");
        btnClear.addActionListener(e -> txtLog.setText(""));
        logHeader.add(btnClear, BorderLayout.EAST);
        logPanel.add(logHeader, BorderLayout.NORTH);
        logPanel.add(new JScrollPane(txtLog), BorderLayout.CENTER);

        // Phai tren: nguoi online
        JPanel onlinePanel = new JPanel(new BorderLayout(0, 4));
        onlinePanel.add(lblOnline, BorderLayout.NORTH);
        onlinePanel.add(new JScrollPane(lstOnline), BorderLayout.CENTER);

        // Phai duoi: bang phong
        tblRooms.setAutoCreateRowSorter(true);
        tblRooms.setFillsViewportHeight(true);
        tblRooms.getColumnModel().getColumn(1).setMaxWidth(90);
        JPanel roomPanel = new JPanel(new BorderLayout(0, 4));
        roomPanel.add(lblRooms, BorderLayout.NORTH);
        roomPanel.add(new JScrollPane(tblRooms), BorderLayout.CENTER);

        JPanel right = new JPanel(new GridLayout(2, 1, 0, 10));
        right.add(onlinePanel);
        right.add(roomPanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logPanel, right);
        split.setResizeWeight(0.65);
        split.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
    }

    // ===================== Start / Stop =====================

    private void startServer() {
        if (server != null) {
            return;
        }
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Cổng phải là số từ 1 đến 65535", "Sai cổng",
                    JOptionPane.WARNING_MESSAGE);
            txtPort.requestFocus();
            return;
        }

        btnStart.setEnabled(false);
        txtPort.setEnabled(false);
        lblStatus.setText("Đang khởi động...");
        lblStatus.setForeground(Color.DARK_GRAY);

        final int p = port;
        new SwingWorker<ChatServer, Void>() {
            @Override
            protected ChatServer doInBackground() throws Exception {
                MessageStore store = storeFactory.get();   // co the mat vai giay neu ket noi CSDL
                ChatServer s = new ChatServer(p, store);
                s.addListener(new AdminListener(s));
                try {
                    s.start();
                } catch (Exception e) {
                    store.close();
                    throw e;
                }
                String storeName = store.getClass().getSimpleName().startsWith("Jdbc") ? "SQL Server" : "RAM (tạm)";
                SwingUtilities.invokeLater(() -> lblStore.setText("Lưu trữ: " + storeName));
                return s;
            }

            @Override
            protected void done() {
                try {
                    server = get();
                    setRunningState(true);
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    setRunningState(false);
                    lblStore.setText("Lưu trữ: -");
                    appendLog("[" + LocalTime.now().format(TIME) + "] Không khởi động được cổng " + p
                            + ": " + cause.getMessage());
                    JOptionPane.showMessageDialog(ServerAdminFrame.this,
                            "Không mở được cổng " + p + ":\n" + cause.getMessage()
                            + "\n\n(Có thể cổng đang bị chương trình khác dùng)",
                            "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void stopServer() {
        stopServer(null);
    }

    /** Dung server trong nen; afterStop (neu co) chay tren thread giao dien sau khi dung xong. */
    private void stopServer(Runnable afterStop) {
        ChatServer s = server;
        if (s == null) {
            if (afterStop != null) {
                afterStop.run();
            }
            return;
        }
        btnStop.setEnabled(false);
        lblStatus.setText("Đang dừng...");
        lblStatus.setForeground(Color.DARK_GRAY);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                s.stop();   // gui BYE cho moi client roi dong ket noi
                return null;
            }

            @Override
            protected void done() {
                server = null;
                setRunningState(false);
                if (afterStop != null) {
                    afterStop.run();
                }
            }
        }.execute();
    }

    private void onClosing() {
        if (server == null) {
            dispose();
            System.exit(0);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Server đang chạy. Dừng server và thoát?", "Thoát", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            stopServer(() -> {
                dispose();
                System.exit(0);
            });
        }
    }

    // ===================== Cap nhat giao dien (chi goi tren EDT) =====================

    private void setRunningState(boolean running) {
        btnStart.setEnabled(!running);
        btnStop.setEnabled(running);
        txtPort.setEnabled(!running);
        if (running) {
            lblStatus.setText("● Đang chạy (cổng " + server.getPort() + ")");
            lblStatus.setForeground(GREEN);
        } else {
            lblStatus.setText("● Đã dừng");
            lblStatus.setForeground(RED);
            onlineModel.clear();
            lblOnline.setText("Người online (0)");
            roomModel.setRowCount(0);
            lblRooms.setText("Phòng (0)");
            lblConnections.setText("Kết nối: 0");
        }
    }

    private void appendLog(String line) {
        txtLog.append(line + "\n");
    }

    private void refreshOnline(ChatServer s) {
        List<String> users = s.getOnlineUsers();
        onlineModel.clear();
        users.forEach(onlineModel::addElement);
        lblOnline.setText("Người online (" + users.size() + ")");
        lblConnections.setText("Kết nối: " + s.getConnectionCount());
    }

    private void refreshRooms(List<RoomInfo> rooms) {
        roomModel.setRowCount(0);
        for (RoomInfo r : rooms) {
            roomModel.addRow(new Object[]{r.name(), r.memberCount()});
        }
        lblRooms.setText("Phòng (" + rooms.size() + ")");
    }

    // ===================== Nhan su kien tu server =====================

    /** Moi ham deu bi goi tu thread mang -> chuyen sang thread giao dien. */
    private final class AdminListener implements ServerListener {

        private final ChatServer s;

        AdminListener(ChatServer s) {
            this.s = s;
        }

        /** Bo qua su kien cua server cu (vd con sot lai sau khi Stop). */
        private boolean isCurrent() {
            return server == s || server == null;
        }

        @Override
        public void onLog(String message) {
            SwingUtilities.invokeLater(() -> appendLog(message));
        }

        @Override
        public void onClientConnected(String address) {
            SwingUtilities.invokeLater(() -> {
                if (isCurrent()) {
                    lblConnections.setText("Kết nối: " + s.getConnectionCount());
                }
            });
        }

        @Override
        public void onUserLoggedIn(String username, String address) {
            SwingUtilities.invokeLater(() -> {
                if (isCurrent()) {
                    refreshOnline(s);
                }
            });
        }

        @Override
        public void onClientDisconnected(String address, String username) {
            SwingUtilities.invokeLater(() -> {
                if (isCurrent() && s.isRunning()) {
                    refreshOnline(s);
                }
            });
        }

        @Override
        public void onRoomsChanged(List<RoomInfo> rooms) {
            SwingUtilities.invokeLater(() -> {
                if (isCurrent() && s.isRunning()) {
                    refreshRooms(rooms);
                }
            });
        }
    }
}
