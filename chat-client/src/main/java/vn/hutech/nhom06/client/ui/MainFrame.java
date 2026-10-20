package vn.hutech.nhom06.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import vn.hutech.nhom06.client.net.ClientConnection;
import vn.hutech.nhom06.client.net.ClientEventListener;
import vn.hutech.nhom06.client.net.FileTransfer;
import vn.hutech.nhom06.client.net.FileTransferListener;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.FileInfo;
import vn.hutech.nhom06.common.PrivateMessage;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Man hinh chat chinh (3 cot).
 *
 *   Tren  : ten nguoi dung, dia chi server, nut Dang xuat
 *   Trai  : MOI NGUOI (moi tai khoan): online o tren (cham xanh), offline o duoi (cham xam).
 *           Bam vao 1 nguoi de nhan tin rieng - ca nguoi offline (server luu lai, ho dang nhap se thay).
 *           So do = so tin rieng chua doc, ke ca tin nhan duoc trong luc minh offline.
 *   Giua  : khung chat cua phong dang o, HOAC khung chat rieng voi 1 nguoi
 *           + o nhap (Enter = gui), nut Gui file, nut Gui, thanh tien trinh gui/tai file
 *   Phai  : 2 khung tach biet
 *           - Phong chat: danh sach phong + Tao phong / Vao phong / Roi phong
 *           - Thanh vien phong: nguoi trong phong dang o (nhay dup de nhan rieng)
 *
 * Moi cuoc tro chuyen (phong, tung nguoi) co 1 StyledDocument rieng; doi qua lai
 * chi can chatPane.setDocument(...), tin moi van duoc ghi vao dung cho du dang xem cho khac.
 *
 * Su kien tu ClientConnection den tren thread nhan tin -> moi cap nhat giao dien
 * deu boc trong SwingUtilities.invokeLater.
 *
 * @author Nhom 06
 */
public class MainFrame extends JFrame implements ClientEventListener {

    private static final long serialVersionUID = 1L;

    private final transient ClientConnection connection;
    private final String username;
    private final String host;
    private final int port;

    private String currentRoom;          // phong dang o (null = chua vao)
    private String pendingCreatedRoom;   // phong minh vua tao -> tu dong vao
    private boolean loggingOut;          // dang xuat chu dong (khong bao loi)

    // ----- Cuoc tro chuyen -----
    private transient StyledDocument roomDoc = new DefaultStyledDocument();
    private int roomUnread;
    private final transient Map<String, Conversation> privates = new HashMap<>();   // ten thuong -> chat rieng
    private String viewingUser;          // dang xem chat rieng voi nguoi nay (null = dang xem phong)
    private final Set<String> online = new HashSet<>();   // ten thuong cua nguoi dang online
    private final Map<String, String> displayNames = new HashMap<>();   // ten thuong -> ten goc (nho ca khi da offline)
    private final Set<String> accounts = new HashSet<>();   // ten thuong cua moi tai khoan da dang ky

    /** Mot cuoc tro chuyen rieng voi 1 nguoi. */
    private static final class Conversation {
        final StyledDocument doc = new DefaultStyledDocument();
        boolean requested;       // da xin lich su
        boolean loaded;          // da nhan xong lich su
        int unread;
        final List<PrivateMessage> history = new ArrayList<>();   // tin lich su vua nhan (de loc trung)
        final List<PrivateMessage> pending = new ArrayList<>();   // tin moi den trong luc dang tai lich su
    }

    // ----- Trai: nguoi online -----
    private final DefaultListModel<String> onlineModel = new DefaultListModel<>();
    private final JList<String> lstOnline = new JList<>(onlineModel);
    private final JLabel lblOnline = Theme.header("Mọi người");

    // ----- Phai tren: phong -----
    private final DefaultListModel<RoomInfo> roomModel = new DefaultListModel<>();
    private final JList<RoomInfo> lstRooms = new JList<>(roomModel);
    private final JLabel lblRooms = Theme.header("Phòng chat");
    private final JButton btnCreate = Theme.primaryButton("Tạo phòng");
    private final JButton btnJoin = Theme.primaryButton("Vào phòng");
    private final JButton btnLeave = Theme.secondaryButton("Rời phòng");

    // ----- Phai duoi: thanh vien phong -----
    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String> lstMembers = new JList<>(memberModel);
    private final JLabel lblMembers = Theme.header("Thành viên phòng (0)");

    // ----- Giua: chat -----
    private final JLabel lblRoomTitle = Theme.header("Chưa vào phòng");
    private final JLabel lblRoomHint = Theme.muted("Chọn một phòng bên phải rồi bấm \"Vào phòng\", "
            + "hoặc bấm vào một người bên trái để nhắn riêng");
    private final JTextPane chatPane = new JTextPane();
    private final JTextField txtInput = new JTextField();
    private final JLabel lblCounter = Theme.muted("0/" + Protocol.MAX_MESSAGE_LENGTH);
    private final JButton btnSend = Theme.primaryButton("Gửi");
    private final JButton btnFile = Theme.secondaryButton("Gửi file");

    // ----- Thanh tien trinh gui / tai file -----
    private final JPanel transferBar = new JPanel(new BorderLayout(10, 0));
    private final JLabel lblTransfer = new JLabel();
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JButton btnCancelTransfer = Theme.secondaryButton("Hủy");
    private transient FileTransfer activeTransfer;   // null = khong truyen file

    // ----- Tren -----
    private final JButton btnLogout = Theme.secondaryButton("Đăng xuất");

    // Kieu chu trong khung chat
    private final SimpleAttributeSet styTime = style(Theme.MUTED, false, false);
    private final SimpleAttributeSet styName = style(Theme.PRIMARY, true, false);
    private final SimpleAttributeSet styMyName = style(new Color(0x0B4FA8), true, false);
    private final SimpleAttributeSet styText = style(Theme.TEXT, false, false);
    private final SimpleAttributeSet styHistory = style(new Color(0x4B5563), false, false);
    private final SimpleAttributeSet stySystem = style(Theme.SYSTEM, false, true);
    private final SimpleAttributeSet styDivider = style(Theme.MUTED, false, true);
    private final SimpleAttributeSet styHint = style(Theme.MUTED, false, false);

    /** Khoa thuoc tinh danh dau doan chu la link file (gia tri = FileInfo). */
    private static final String FILE_ATTR = "nhom06.file";

    private static final Color BADGE = new Color(0xE53935);

    private static final ThreadLocal<SimpleDateFormat> TIME =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm", Locale.ROOT));

    public MainFrame(ClientConnection connection, String username, String host, int port) {
        super("Chat Nhóm 06 - " + username);
        this.connection = connection;
        this.username = username;
        this.host = host;
        this.port = port;

        setNames();
        buildUi();
        wireActions();
        chatPane.setDocument(roomDoc);
        updateState();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                loggingOut = true;
                connection.logout();
                connection.close();
                dispose();
                System.exit(0);
            }

            @Override
            public void windowOpened(WindowEvent e) {
                lstRooms.requestFocusInWindow();
            }
        });
        setSize(1180, 700);
        setMinimumSize(new Dimension(980, 580));
        setLocationRelativeTo(null);
    }

    private void setNames() {   // dat ten de de kiem thu tu dong
        lstOnline.setName("lstOnline");
        lstRooms.setName("lstRooms");
        btnCreate.setName("btnCreate");
        btnJoin.setName("btnJoin");
        btnLeave.setName("btnLeave");
        chatPane.setName("chatPane");
        txtInput.setName("txtInput");
        btnSend.setName("btnSend");
        lstMembers.setName("lstMembers");
        btnLogout.setName("btnLogout");
        lblRoomTitle.setName("lblRoomTitle");
        lblRoomHint.setName("lblRoomHint");
        btnFile.setName("btnFile");
        btnCancelTransfer.setName("btnCancelTransfer");
        progress.setName("progress");
        lblTransfer.setName("lblTransfer");
    }

    // ===================== Dung giao dien =====================

    private void buildUi() {
        getContentPane().setBackground(Theme.WINDOW);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buildTopBar(), BorderLayout.NORTH);
        getContentPane().add(buildOnlinePanel(), BorderLayout.WEST);
        getContentPane().add(buildChatPanel(), BorderLayout.CENTER);
        getContentPane().add(buildRightPanel(), BorderLayout.EAST);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.PRIMARY);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel app = new JLabel("Chat Nhóm 06");
        app.setFont(Theme.FONT_TITLE.deriveFont(18f));
        app.setForeground(Color.WHITE);
        bar.add(app, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        JLabel who = new JLabel("Xin chào, " + username + "   •   Server " + host + ":" + port);
        who.setFont(Theme.FONT);
        who.setForeground(new Color(0xE3EEFD));
        right.add(who);
        right.add(btnLogout);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Cot trai: nguoi dang online. */
    private JPanel buildOnlinePanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.PANEL);
        p.setPreferredSize(new Dimension(230, 0));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.DIVIDER),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));

        JPanel head = new JPanel(new GridLayout(2, 1, 0, 2));
        head.setOpaque(false);
        head.add(lblOnline);
        head.add(Theme.muted("Bấm vào tên để nhắn riêng"));
        p.add(head, BorderLayout.NORTH);

        lstOnline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstOnline.setCellRenderer(new UserRenderer(true));
        lstOnline.setFixedCellHeight(36);
        JScrollPane sp = scroll(lstOnline);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildChatPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.WINDOW);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));

        JPanel head = new JPanel(new GridLayout(2, 1));
        head.setOpaque(false);
        head.add(lblRoomTitle);
        head.add(lblRoomHint);
        p.add(head, BorderLayout.NORTH);

        chatPane.setEditable(false);
        chatPane.setFont(Theme.FONT);
        chatPane.setBackground(Theme.BG);
        chatPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        p.add(scroll(chatPane), BorderLayout.CENTER);

        // O nhap: toi da 1000 ky tu
        Theme.styleInput(txtInput);
        ((AbstractDocument) txtInput.getDocument()).setDocumentFilter(new LengthFilter(Protocol.MAX_MESSAGE_LENGTH));
        txtInput.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCounter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCounter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCounter();
            }
        });

        JPanel input = new JPanel(new BorderLayout(8, 0));
        input.setOpaque(false);
        input.add(txtInput, BorderLayout.CENTER);
        JPanel sendBox = new JPanel(new BorderLayout(8, 0));
        sendBox.setOpaque(false);
        sendBox.add(lblCounter, BorderLayout.WEST);
        btnFile.setPreferredSize(new Dimension(96, 38));
        btnFile.setToolTipText("Gửi file (tối đa " + FileInfo.formatSize(Protocol.MAX_FILE_SIZE) + ")");
        sendBox.add(btnFile, BorderLayout.CENTER);
        btnSend.setPreferredSize(new Dimension(84, 38));
        sendBox.add(btnSend, BorderLayout.EAST);
        input.add(sendBox, BorderLayout.EAST);

        // Thanh tien trinh (an khi khong truyen file)
        transferBar.setOpaque(true);
        transferBar.setBackground(Theme.PRIMARY_LIGHT);
        transferBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xA9C4EC), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 6)));
        lblTransfer.setFont(Theme.FONT_SMALL);
        lblTransfer.setForeground(Theme.TEXT);
        progress.setForeground(Theme.PRIMARY);
        progress.setBackground(Theme.BG);
        progress.setBorderPainted(false);
        progress.setPreferredSize(new Dimension(180, 10));
        JPanel bar = new JPanel(new GridLayout(2, 1, 0, 4));
        bar.setOpaque(false);
        bar.add(lblTransfer);
        bar.add(progress);
        transferBar.add(bar, BorderLayout.CENTER);
        btnCancelTransfer.setPreferredSize(new Dimension(64, 32));
        transferBar.add(btnCancelTransfer, BorderLayout.EAST);
        transferBar.setVisible(false);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(transferBar, BorderLayout.NORTH);
        south.add(input, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    /** Cot phai: 2 khung tach biet - Phong chat (tren) va Thanh vien phong (duoi). */
    private JPanel buildRightPanel() {
        JPanel col = new JPanel(new GridBagLayout());
        col.setBackground(Theme.PANEL);
        col.setPreferredSize(new Dimension(260, 0));
        col.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.DIVIDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        // --- Khung phong ---
        JPanel rooms = card();
        rooms.add(lblRooms, BorderLayout.NORTH);
        lstRooms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstRooms.setCellRenderer(new RoomRenderer());
        lstRooms.setFixedCellHeight(36);
        JScrollPane roomScroll = scroll(lstRooms);
        roomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rooms.add(roomScroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 6));
        buttons.setOpaque(false);
        buttons.add(btnCreate);
        buttons.add(btnJoin);
        buttons.add(btnLeave);
        rooms.add(buttons, BorderLayout.SOUTH);

        // --- Khung thanh vien phong ---
        JPanel members = card();
        JPanel mHead = new JPanel(new GridLayout(2, 1, 0, 2));
        mHead.setOpaque(false);
        mHead.add(lblMembers);
        mHead.add(Theme.muted("Nháy đúp vào tên để nhắn riêng"));
        members.add(mHead, BorderLayout.NORTH);
        lstMembers.setCellRenderer(new UserRenderer(false));
        lstMembers.setFixedCellHeight(30);
        lstMembers.setSelectionModel(new NoSelection());
        JScrollPane memberScroll = scroll(lstMembers);
        memberScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        members.add(memberScroll, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridy = 0;
        c.weighty = 0.58;
        c.insets = new Insets(0, 0, 12, 0);
        col.add(rooms, c);
        c.gridy = 1;
        c.weighty = 0.42;
        c.insets = new Insets(0, 0, 0, 0);
        col.add(members, c);
        return col;
    }

    /** Khung trang vien bo goc, de tach ro tung nhom. */
    private static JPanel card() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Theme.BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        return p;
    }

    private static JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(Theme.lineBorder());
        sp.getViewport().setBackground(Theme.BG);
        return sp;
    }

    // ===================== Hanh dong cua nguoi dung =====================

    private void wireActions() {
        btnCreate.addActionListener(e -> createRoom());
        btnJoin.addActionListener(e -> joinSelected());
        btnLeave.addActionListener(e -> connection.leave());
        btnSend.addActionListener(e -> sendMessage());
        txtInput.addActionListener(e -> sendMessage());   // Enter = Gui
        btnLogout.addActionListener(e -> logout());
        btnFile.addActionListener(e -> chooseAndSendFile());
        btnCancelTransfer.addActionListener(e -> {
            FileTransfer t = activeTransfer;
            if (t != null) {
                t.cancel();
                lblTransfer.setText("Đang hủy...");
            }
        });
        lstRooms.addListSelectionListener(e -> updateState());
        lstRooms.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RoomInfo r = lstRooms.getSelectedValue();
                if (r == null) {
                    return;
                }
                if (r.name().equalsIgnoreCase(currentRoom)) {
                    showRoom();          // bam vao phong dang o = quay lai chat phong
                } else if (e.getClickCount() == 2) {
                    joinSelected();      // nhay dup = vao phong
                }
            }
        });
        // Bam vao nguoi online = mo chat rieng
        lstOnline.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = lstOnline.locationToIndex(e.getPoint());
                if (i >= 0 && lstOnline.getCellBounds(i, i).contains(e.getPoint())) {
                    openPrivate(onlineModel.get(i));
                }
            }
        });
        // Nhay dup vao thanh vien phong = mo chat rieng
        lstMembers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = lstMembers.locationToIndex(e.getPoint());
                if (e.getClickCount() == 2 && i >= 0 && lstMembers.getCellBounds(i, i).contains(e.getPoint())) {
                    openPrivate(memberModel.get(i));
                }
            }
        });
        // Nhap vao link file trong khung chat = tai ve; re chuot len link = con tro ban tay
        chatPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                FileInfo f = fileAt(e);
                if (f != null && SwingUtilities.isLeftMouseButton(e)) {
                    downloadFile(f);
                }
            }
        });
        chatPane.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                FileInfo f = fileAt(e);
                chatPane.setCursor(f != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                chatPane.setToolTipText(f != null ? "Nhấp để tải " + f.name() : null);
            }
        });
    }

    private void createRoom() {
        String name = JOptionPane.showInputDialog(this, "Tên phòng mới (1-30 ký tự):", "Tạo phòng",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;   // bam Huy
        }
        name = name.trim();
        if (!Protocol.isValidRoomName(name)) {
            showError("Tên phòng không hợp lệ: 1-30 ký tự, chỉ gồm chữ, số, khoảng trắng, _ . -");
            return;
        }
        pendingCreatedRoom = name;
        connection.createRoom(name);
    }

    private void joinSelected() {
        RoomInfo r = lstRooms.getSelectedValue();
        if (r == null) {
            showError("Hãy chọn một phòng trong danh sách");
            return;
        }
        if (r.name().equalsIgnoreCase(currentRoom)) {
            showRoom();   // dang o phong nay roi: chi chuyen ve khung chat phong
            return;
        }
        connection.join(r.name());
    }

    private void sendMessage() {
        String text = txtInput.getText();
        if (text.isBlank() || !canSend()) {
            return;
        }
        if (viewingUser != null) {
            connection.sendPrivateMessage(viewingUser, text);
        } else {
            connection.sendMessage(text);
        }
        txtInput.setText("");
        txtInput.requestFocusInWindow();
    }

    private void logout() {
        int ok = JOptionPane.showConfirmDialog(this, "Bạn muốn đăng xuất?", "Đăng xuất",
                JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            loggingOut = true;
            connection.logout();   // -> onDisconnected -> ve man hinh dang nhap
        }
    }

    // ===================== Doi khung chat: phong <-> rieng =====================

    /** Hien khung chat cua phong dang o. */
    private void showRoom() {
        viewingUser = null;
        roomUnread = 0;
        chatPane.setDocument(roomDoc);
        scrollToEnd();
        lstOnline.clearSelection();
        if (currentRoom != null) {
            lblRoomTitle.setText("# " + currentRoom);
            lblRoomHint.setText("Bạn đang ở trong phòng này");
        } else {
            lblRoomTitle.setText("Chưa vào phòng");
            lblRoomHint.setText("Chọn một phòng bên phải rồi bấm \"Vào phòng\", "
                    + "hoặc bấm vào một người bên trái để nhắn riêng");
        }
        updateState();
        txtInput.requestFocusInWindow();
    }

    /** Mo khung chat rieng voi {@code user} (lan dau thi tai lich su tu server). */
    private void openPrivate(String user) {
        if (user == null || user.equalsIgnoreCase(username)) {
            return;   // khong nhan rieng cho chinh minh
        }
        Conversation c = conversation(user);
        viewingUser = user;
        c.unread = 0;
        connection.markPrivateRead(user);   // server danh dau da doc -> lan dang nhap sau khong hien so do nua
        if (!c.requested) {
            c.requested = true;
            append(c.doc, "Đang tải tin nhắn...\n", styDivider);
            connection.requestPrivateHistory(user, Protocol.DEFAULT_HISTORY_LIMIT);
        }
        chatPane.setDocument(c.doc);
        scrollToEnd();
        lblRoomTitle.setText("Tin nhắn riêng với " + user);
        updatePrivateHint();
        selectOnline(user);
        lstRooms.repaint();
        lstOnline.repaint();
        updateState();
        txtInput.requestFocusInWindow();
    }

    private void updatePrivateHint() {
        if (viewingUser == null) {
            return;
        }
        lblRoomHint.setText(isOnline(viewingUser)
                ? "Chỉ bạn và " + viewingUser + " thấy các tin này  •  bấm tên phòng bên phải để quay lại phòng"
                : viewingUser + " đang offline: tin bạn gửi được lưu lại, " + viewingUser
                        + " đăng nhập sẽ thấy  •  bấm tên phòng bên phải để quay lại phòng");
    }

    private Conversation conversation(String user) {
        return privates.computeIfAbsent(key(user), k -> new Conversation());
    }

    private boolean isOnline(String user) {
        return user != null && online.contains(key(user));
    }

    /** Tai khoan da dang ky (online hoac offline). */
    private boolean isKnownUser(String user) {
        return user != null && (accounts.contains(key(user)) || online.contains(key(user)));
    }

    private boolean canSend() {
        return viewingUser != null ? isKnownUser(viewingUser) : currentRoom != null;
    }

    /** Dung lai cot "Moi nguoi": minh -> nguoi online (A-Z) -> nguoi offline (A-Z). */
    private void rebuildPeople() {
        Set<String> all = new HashSet<>(accounts);
        all.addAll(online);
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (String k : all) {
            if (k.equals(key(username))) {
                continue;
            }
            (online.contains(k) ? on : off).add(displayNames.getOrDefault(k, k));
        }
        on.sort(String.CASE_INSENSITIVE_ORDER);
        off.sort(String.CASE_INSENSITIVE_ORDER);
        onlineModel.clear();
        onlineModel.addElement(username);
        on.forEach(onlineModel::addElement);
        off.forEach(onlineModel::addElement);
        lblOnline.setText("Mọi người  (" + (on.size() + 1) + "/" + (on.size() + off.size() + 1) + " online)");
        if (viewingUser != null) {
            selectOnline(viewingUser);
        }
    }

    // ===================== Gui / tai file =====================

    private void chooseAndSendFile() {
        if (!canSend()) {
            showError(viewingUser != null ? viewingUser + " đã offline, không gửi được file"
                    : "Bạn cần vào phòng trước khi gửi file");
            return;
        }
        if (activeTransfer != null) {
            showError("Đang truyền một file khác, hãy đợi xong hoặc bấm Hủy");
            return;
        }
        String to = viewingUser;   // ghi lai: nguoi dung co the doi khung chat trong luc gui
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle((to != null ? "Gửi file cho " + to : "Gửi file vào phòng " + currentRoom)
                + " (tối đa " + FileInfo.formatSize(Protocol.MAX_FILE_SIZE) + ")");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = fc.getSelectedFile().toPath();
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            showError("Không đọc được file: " + e.getMessage());
            return;
        }
        // Kiem tra ngay tai client: bao loi luon, khong ton mang
        if (size > Protocol.MAX_FILE_SIZE) {
            showError("File \"" + file.getFileName() + "\" có dung lượng " + FileInfo.formatSizeExact(size)
                    + ".\nChỉ được gửi file tối đa " + FileInfo.formatSizeExact(Protocol.MAX_FILE_SIZE) + ".");
            return;
        }
        if (size == 0) {
            showError("File rỗng (0 byte), không thể gửi");
            return;
        }
        String name = file.getFileName().toString();
        startTransferUi("Đang gửi " + name + (to != null ? " cho " + to : ""), size);
        TransferUi cb = new TransferUi("Gửi", name, null);
        FileTransfer t = to != null ? connection.sendPrivateFile(to, file, cb) : connection.sendFile(file, cb);
        if (t != null) {
            activeTransfer = t;
        }
        updateState();
    }

    private void downloadFile(FileInfo f) {
        if (activeTransfer != null) {
            showError("Đang truyền một file khác, hãy đợi xong hoặc bấm Hủy");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Lưu file " + f.name() + " (" + f.sizeText() + ")");
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.isDirectory()) {
            fc.setCurrentDirectory(downloads);
        }
        fc.setSelectedFile(new File(fc.getCurrentDirectory(), Protocol.cleanFileName(f.name())));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = fc.getSelectedFile();
        if (target.exists() && JOptionPane.showConfirmDialog(this,
                "File \"" + target.getName() + "\" đã có. Ghi đè?", "Lưu file",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        startTransferUi("Đang tải " + f.name(), f.size());
        FileTransfer t = connection.downloadFile(f, target.toPath(), new TransferUi("Tải", f.name(), target));
        if (t != null) {
            activeTransfer = t;
        }
        updateState();
    }

    private void startTransferUi(String text, long size) {
        lblTransfer.setText(text + "  (0 / " + FileInfo.formatSize(size) + ")");
        progress.setValue(0);
        btnCancelTransfer.setEnabled(true);
        transferBar.setVisible(true);
        transferBar.revalidate();
    }

    private void endTransferUi() {
        activeTransfer = null;
        transferBar.setVisible(false);
        transferBar.revalidate();
        updateState();
    }

    /** Cap nhat thanh tien trinh tu thread mang (boc invokeLater). */
    private final class TransferUi implements FileTransferListener {

        private final String verb;      // "Gui" / "Tai"
        private final String name;
        private final File savedTo;     // null khi gui

        TransferUi(String verb, String name, File savedTo) {
            this.verb = verb;
            this.name = name;
            this.savedTo = savedTo;
        }

        @Override
        public void onProgress(long done, long total) {
            SwingUtilities.invokeLater(() -> {
                if (activeTransfer == null || activeTransfer.isCancelled()) {
                    return;
                }
                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                progress.setValue(total > 0 ? (int) (done * 1000 / total) : 0);
                lblTransfer.setText("Đang " + verb.toLowerCase(Locale.ROOT) + " " + name + "  ("
                        + FileInfo.formatSize(done) + " / " + FileInfo.formatSize(total) + " - " + pct + "%)");
            });
        }

        @Override
        public void onDone() {
            SwingUtilities.invokeLater(() -> {
                endTransferUi();
                if (savedTo != null && isDisplayable()) {
                    askOpen(savedTo);
                }
            });
        }

        @Override
        public void onFailed(String reason) {
            SwingUtilities.invokeLater(() -> {
                endTransferUi();
                if (isDisplayable() && !loggingOut) {
                    showError(verb + " file \"" + name + "\" không thành công:\n" + reason);
                }
            });
        }
    }

    private void askOpen(File file) {
        Object[] options = {"Mở file", "Mở thư mục", "Đóng"};
        int choice = JOptionPane.showOptionDialog(this, "Đã tải xong:\n" + file.getAbsolutePath(), "Tải file",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice != 0 && choice != 1) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(choice == 0 ? file : file.getParentFile());
            }
        } catch (IOException | RuntimeException e) {
            showError("Không mở được: " + e.getMessage());
        }
    }

    /** FileInfo cua link file duoi con tro chuot, hoac null. */
    private FileInfo fileAt(MouseEvent e) {
        int pos = chatPane.viewToModel2D(e.getPoint());
        if (pos < 0) {
            return null;
        }
        Element el = chatPane.getStyledDocument().getCharacterElement(pos);
        Object v = el.getAttributes().getAttribute(FILE_ATTR);
        if (!(v instanceof FileInfo f)) {
            return null;
        }
        // viewToModel tra ve ky tu gan nhat: chi tinh khi chuot nam tren dong chu that
        try {
            java.awt.geom.Rectangle2D r = chatPane.modelToView2D(pos);
            if (r == null || Math.abs(e.getY() - r.getCenterY()) > r.getHeight()) {
                return null;
            }
        } catch (BadLocationException ex) {
            return null;
        }
        return f;
    }

    // ===================== Su kien tu server (thread nhan tin) =====================

    @Override
    public void onLoginOk(String name) {
        // da dang nhap o LoginFrame, khong can xu ly
    }

    @Override
    public void onRoomList(List<RoomInfo> rooms) {
        SwingUtilities.invokeLater(() -> {
            RoomInfo selected = lstRooms.getSelectedValue();
            String keep = selected != null ? selected.name() : currentRoom;
            roomModel.clear();
            int select = -1;
            for (int i = 0; i < rooms.size(); i++) {
                roomModel.addElement(rooms.get(i));
                if (rooms.get(i).name().equalsIgnoreCase(keep)) {
                    select = i;
                }
            }
            if (select >= 0) {
                lstRooms.setSelectedIndex(select);
            }
            lblRooms.setText("Phòng chat (" + rooms.size() + ")");
            updateState();
        });
    }

    @Override
    public void onRoomCreated(String room) {
        SwingUtilities.invokeLater(() -> {
            if (room.equalsIgnoreCase(pendingCreatedRoom)) {
                pendingCreatedRoom = null;
                connection.join(room);   // tao xong tu vao luon
            }
        });
    }

    @Override
    public void onJoined(String room) {
        SwingUtilities.invokeLater(() -> {
            currentRoom = room;
            roomDoc = new DefaultStyledDocument();   // khung chat moi cho phong moi
            memberModel.clear();
            selectRoom(room);
            showRoom();
            lblRoomHint.setText("Đang tải lịch sử tin nhắn...");
        });
    }

    @Override
    public void onHistoryItem(ChatMessage m) {
        SwingUtilities.invokeLater(() -> appendMessage(roomDoc, m.sender(), m.timestamp(), m.content(), m.file(), true));
    }

    @Override
    public void onHistoryEnd(String room, int count) {
        SwingUtilities.invokeLater(() -> {
            if (count == 0) {
                append(roomDoc, "Phòng chưa có tin nhắn nào. Hãy là người đầu tiên!\n", styDivider);
            } else {
                append(roomDoc, "── " + count + " tin nhắn trước đó ──\n", styDivider);
            }
            if (viewingUser == null) {
                lblRoomHint.setText("Bạn đang ở trong phòng này");
            }
        });
    }

    @Override
    public void onMessage(ChatMessage m) {
        SwingUtilities.invokeLater(() -> {
            if (m.room().equalsIgnoreCase(currentRoom)) {
                appendMessage(roomDoc, m.sender(), m.timestamp(), m.content(), m.file(), false);
                if (viewingUser != null) {
                    roomUnread++;   // dang xem chat rieng -> danh dau phong co tin moi
                    lstRooms.repaint();
                }
            }
        });
    }

    @Override
    public void onMembers(String room, List<String> members) {
        SwingUtilities.invokeLater(() -> {
            if (room.equalsIgnoreCase(currentRoom)) {
                setMembers(members);
            }
        });
    }

    @Override
    public void onUserJoined(String room, String user) {
        SwingUtilities.invokeLater(() -> {
            if (room.equalsIgnoreCase(currentRoom)) {
                List<String> list = currentMembers();
                if (!list.contains(user)) {
                    list.add(user);
                    list.sort(String.CASE_INSENSITIVE_ORDER);
                    setMembers(list);
                }
                appendSystem(roomDoc, user + " đã vào phòng");
            }
        });
    }

    @Override
    public void onUserLeft(String room, String user) {
        SwingUtilities.invokeLater(() -> {
            if (room.equalsIgnoreCase(currentRoom) && !user.equalsIgnoreCase(username)) {
                List<String> list = currentMembers();
                list.removeIf(u -> u.equalsIgnoreCase(user));
                setMembers(list);
                appendSystem(roomDoc, user + " đã rời phòng");
            }
        });
    }

    @Override
    public void onLeft(String room) {
        SwingUtilities.invokeLater(() -> {
            currentRoom = null;
            roomUnread = 0;
            memberModel.clear();
            lblMembers.setText("Thành viên phòng (0)");
            appendSystem(roomDoc, "Bạn đã rời phòng " + room);
            if (viewingUser == null) {
                showRoom();
            }
            updateState();
        });
    }

    @Override
    public void onOnlineList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            Set<String> before = new HashSet<>(online);
            online.clear();
            for (String u : users) {
                online.add(key(u));
                displayNames.put(key(u), u);
            }
            rebuildPeople();
            // Bao vao cac khung chat rieng dang mo khi nguoi kia offline / online lai
            for (Map.Entry<String, Conversation> e : privates.entrySet()) {
                boolean was = before.contains(e.getKey());
                boolean now = online.contains(e.getKey());
                if (e.getValue().loaded && was != now && !before.isEmpty()) {
                    String name = displayNames.getOrDefault(e.getKey(), e.getKey());
                    appendSystem(e.getValue().doc, name + (now ? " đã online lại" : " đã offline"));
                }
            }
            if (viewingUser != null) {
                updatePrivateHint();
            }
            updateState();
        });
    }

    @Override
    public void onUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            accounts.clear();
            for (String u : users) {
                accounts.add(key(u));
                displayNames.putIfAbsent(key(u), u);
            }
            rebuildPeople();
            updateState();
        });
    }

    @Override
    public void onUnreadCounts(Map<String, Integer> unread) {
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<String, Integer> e : unread.entrySet()) {
                displayNames.putIfAbsent(key(e.getKey()), e.getKey());
                if (!e.getKey().equalsIgnoreCase(viewingUser)) {
                    conversation(e.getKey()).unread = e.getValue();
                }
            }
            lstOnline.repaint();
        });
    }

    @Override
    public void onPrivateMessage(PrivateMessage m) {
        SwingUtilities.invokeLater(() -> {
            String other = m.other(username);
            Conversation c = conversation(other);
            if (c.loaded) {
                appendPrivate(c.doc, m, false);
            } else if (c.requested) {
                c.pending.add(m);   // dang tai lich su: doi tai xong roi them (tranh trung, sai thu tu)
            }
            // chua mo lan nao: khong can ghi, lan dau mo se tai lich su (da gom tin nay)
            if (!other.equalsIgnoreCase(viewingUser)) {
                c.unread++;
                lstOnline.repaint();
            } else if (!m.sender().equalsIgnoreCase(username)) {
                connection.markPrivateRead(other);   // dang xem dung khung chat nay -> doc roi
            }
        });
    }

    @Override
    public void onPrivateHistoryItem(PrivateMessage m) {
        SwingUtilities.invokeLater(() -> {
            Conversation c = conversation(m.other(username));
            if (c.history.isEmpty()) {
                clear(c.doc);   // xoa dong "Dang tai tin nhan..."
            }
            c.history.add(m);
            appendPrivate(c.doc, m, true);
        });
    }

    @Override
    public void onPrivateHistoryEnd(String other, int count) {
        SwingUtilities.invokeLater(() -> {
            Conversation c = conversation(other);
            if (count == 0) {
                clear(c.doc);
                append(c.doc, "Chưa có tin nhắn riêng nào với " + other + ". Hãy gửi lời chào!\n", styDivider);
            } else {
                append(c.doc, "── " + count + " tin nhắn trước đó ──\n", styDivider);
            }
            for (PrivateMessage m : c.pending) {
                if (!c.history.contains(m)) {
                    appendPrivate(c.doc, m, false);
                }
            }
            c.pending.clear();
            c.history.clear();
            c.loaded = true;
            if (chatPane.getDocument() == c.doc) {
                scrollToEnd();
            }
        });
    }

    @Override
    public void onError(String code, String message) {
        SwingUtilities.invokeLater(() -> {
            if (Protocol.ERR_ROOM_EXISTS.equals(code)) {
                pendingCreatedRoom = null;
            }
            showError(translate(code, message));
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (!loggingOut) {
                JOptionPane.showMessageDialog(this, "Mất kết nối với server:\n" + reason,
                        "Mất kết nối", JOptionPane.WARNING_MESSAGE);
            }
            dispose();
            new LoginFrame(host, port, username).setVisible(true);   // quay ve dang nhap
        });
    }

    // ===================== Ham phu (chi goi tren thread giao dien) =====================

    private void appendPrivate(StyledDocument doc, PrivateMessage m, boolean history) {
        appendMessage(doc, m.sender(), m.timestamp(), m.content(), m.file(), history);
    }

    /** "[HH:mm] Ten: noi dung" hoac "[HH:mm] Ten: da gui file <link>". */
    private void appendMessage(StyledDocument doc, String sender, long time, String content, FileInfo f,
            boolean history) {
        boolean mine = sender.equalsIgnoreCase(username);
        append(doc, "[" + TIME.get().format(new Date(time)) + "] ", styTime);
        append(doc, sender + ": ", mine ? styMyName : styName);
        if (f != null) {
            append(doc, "đã gửi file ", history ? styHistory : styText);
            SimpleAttributeSet link = new SimpleAttributeSet(styName);
            StyleConstants.setUnderline(link, true);
            link.addAttribute(FILE_ATTR, f);
            append(doc, f.name() + " (" + f.sizeText() + ")", link);
            append(doc, "  - nhấp để tải\n", styHint);
        } else {
            append(doc, content + "\n", history ? styHistory : styText);
        }
    }

    private void appendSystem(StyledDocument doc, String text) {
        append(doc, "[" + TIME.get().format(new Date()) + "] ", styTime);
        append(doc, "• " + text + "\n", stySystem);
    }

    private void append(StyledDocument doc, String text, AttributeSet style) {
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {
            // khong xay ra vi luon chen o cuoi
        }
        if (chatPane.getDocument() == doc) {
            chatPane.setCaretPosition(doc.getLength());   // tu cuon xuong tin moi nhat
        }
    }

    private static void clear(StyledDocument doc) {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void scrollToEnd() {
        chatPane.setCaretPosition(chatPane.getDocument().getLength());
    }

    private void setMembers(List<String> members) {
        memberModel.clear();
        members.forEach(memberModel::addElement);
        lblMembers.setText("Thành viên phòng (" + members.size() + ")");
    }

    private List<String> currentMembers() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < memberModel.size(); i++) {
            list.add(memberModel.get(i));
        }
        return list;
    }

    private void selectRoom(String room) {
        for (int i = 0; i < roomModel.size(); i++) {
            if (roomModel.get(i).name().equalsIgnoreCase(room)) {
                lstRooms.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectOnline(String user) {
        for (int i = 0; i < onlineModel.size(); i++) {
            if (onlineModel.get(i).equalsIgnoreCase(user)) {
                lstOnline.setSelectedIndex(i);
                return;
            }
        }
        lstOnline.clearSelection();
    }

    private void updateState() {
        RoomInfo sel = lstRooms.getSelectedValue();
        btnJoin.setEnabled(sel != null && !sel.name().equalsIgnoreCase(currentRoom));
        btnLeave.setEnabled(currentRoom != null);
        boolean send = canSend();
        txtInput.setEnabled(send);
        btnSend.setEnabled(send);
        btnFile.setEnabled(send && activeTransfer == null);
        lblCounter.setVisible(send);
        lstRooms.repaint();
        lstOnline.repaint();
    }

    private void updateCounter() {
        int n = txtInput.getText().length();
        lblCounter.setText(n + "/" + Protocol.MAX_MESSAGE_LENGTH);
        lblCounter.setForeground(n >= Protocol.MAX_MESSAGE_LENGTH ? Theme.ERROR : Theme.MUTED);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Thông báo", JOptionPane.WARNING_MESSAGE);
    }

    private static String translate(String code, String fallback) {
        return switch (code) {
            case Protocol.ERR_ROOM_EXISTS -> "Phòng này đã tồn tại, hãy chọn tên khác";
            case Protocol.ERR_INVALID_ROOM -> "Tên phòng không hợp lệ";
            case Protocol.ERR_ROOM_NOT_FOUND -> "Phòng không còn tồn tại";
            case Protocol.ERR_NOT_IN_ROOM -> "Bạn cần vào phòng trước";
            case Protocol.ERR_EMPTY_MESSAGE -> "Tin nhắn đang trống";
            case Protocol.ERR_MESSAGE_TOO_LONG -> "Tin nhắn tối đa " + Protocol.MAX_MESSAGE_LENGTH + " ký tự";
            case Protocol.ERR_FILE_TOO_LARGE -> "File vượt quá giới hạn " + FileInfo.formatSize(Protocol.MAX_FILE_SIZE);
            case Protocol.ERR_USER_NOT_FOUND -> "Không có tài khoản này";
            case Protocol.ERR_SELF_MESSAGE -> "Không thể nhắn riêng cho chính mình";
            default -> fallback;
        };
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static SimpleAttributeSet style(Color color, boolean bold, boolean italic) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        StyleConstants.setFontFamily(s, Theme.FONT_NAME);
        StyleConstants.setFontSize(s, 14);
        return s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ===================== Lop phu =====================

    /** Phong: ten ben trai, so nguoi (hoac so tin chua doc) ben phai; phong dang o co vach xanh. */
    private final class RoomRenderer implements ListCellRenderer<RoomInfo> {

        private final JPanel row = new JPanel(new BorderLayout(8, 0));
        private final JLabel name = new JLabel();
        private final JLabel count = new JLabel();
        private final Badge badge = new Badge();
        private final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        RoomRenderer() {
            right.setOpaque(false);
            right.add(badge);
            right.add(count);
            row.add(name, BorderLayout.CENTER);
            row.add(right, BorderLayout.EAST);
            count.setFont(Theme.FONT_SMALL);
            count.setForeground(Theme.MUTED);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomInfo> list, RoomInfo r, int index,
                boolean selected, boolean focus) {
            boolean here = r.name().equalsIgnoreCase(currentRoom);
            boolean viewing = here && viewingUser == null;
            name.setText(r.name());
            name.setFont(here ? Theme.FONT_BOLD : Theme.FONT);
            name.setForeground(here ? Theme.PRIMARY : Theme.TEXT);
            count.setText(r.memberCount() + " người");
            badge.setCount(here ? roomUnread : 0);
            right.setBorder(BorderFactory.createEmptyBorder(9, 0, 0, 0));
            row.setBackground(viewing || selected ? Theme.PRIMARY_LIGHT : Theme.BG);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, here ? Theme.PRIMARY : (selected ? Theme.PRIMARY_LIGHT : Theme.BG)),
                    BorderFactory.createEmptyBorder(0, 8, 0, 6)));
            return row;
        }
    }

    /** Nguoi dung: cham xanh + ten (+ "(ban)"); o cot online co them so tin rieng chua doc. */
    private final class UserRenderer implements ListCellRenderer<String> {

        private final boolean onlineList;
        private final JPanel row = new JPanel(new BorderLayout(6, 0));
        private final JLabel label = new JLabel();
        private final Badge badge = new Badge();
        private final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

        UserRenderer(boolean onlineList) {
            this.onlineList = onlineList;
            right.setOpaque(false);
            right.add(badge);
            row.add(label, BorderLayout.CENTER);
            row.add(right, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                boolean selected, boolean focus) {
            boolean me = value.equalsIgnoreCase(username);
            Conversation c = privates.get(key(value));
            int unread = onlineList && c != null ? c.unread : 0;
            boolean viewing = onlineList && value.equalsIgnoreCase(viewingUser);
            boolean on = !onlineList || me || isOnline(value);
            label.setText("<html><span style='color:" + (on ? "#0F8A5F" : "#A0AAB8") + "'>●</span>&nbsp;&nbsp;"
                    + escape(value)
                    + (me ? " <span style='color:#6B7280'>(bạn)</span>" : "")
                    + (on ? "" : " <span style='color:#8A94A6'>· offline</span>") + "</html>");
            label.setFont(unread > 0 || viewing ? Theme.FONT_BOLD : Theme.FONT);
            label.setForeground(viewing ? Theme.PRIMARY : on || unread > 0 ? Theme.TEXT : Theme.MUTED);
            badge.setCount(unread);
            int h = onlineList ? 36 : 30;
            right.setBorder(BorderFactory.createEmptyBorder((h - 18) / 2, 0, 0, 0));
            row.setBackground(viewing ? Theme.PRIMARY_LIGHT : Theme.BG);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, onlineList ? 4 : 0, 0, 0, viewing ? Theme.PRIMARY : Theme.BG),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)));
            row.setToolTipText(me ? null : onlineList ? "Nhắn riêng với " + value + (on ? "" : " (đang offline)")
                    : "Nháy đúp để nhắn riêng");
            return row;
        }
    }

    /** Vong tron do hien so tin chua doc (an khi = 0). */
    private static final class Badge extends JComponent {

        private static final long serialVersionUID = 1L;
        private int count;

        void setCount(int n) {
            count = n;
            setVisible(n > 0);
            String t = text();
            int w = Math.max(18, getFontMetrics(Theme.FONT_SMALL.deriveFont(java.awt.Font.BOLD)).stringWidth(t) + 10);
            setPreferredSize(new Dimension(w, 18));
        }

        private String text() {
            return count > 99 ? "99+" : String.valueOf(count);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (count <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(BADGE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.FONT_SMALL.deriveFont(java.awt.Font.BOLD));
            String t = text();
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Gioi han do dai o nhap (dan vao qua dai thi cat bot). */
    private static final class LengthFilter extends DocumentFilter {

        private final int max;

        LengthFilter(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null) {
                super.replace(fb, offset, length, null, attrs);
                return;
            }
            int room = max - (fb.getDocument().getLength() - length);
            if (room <= 0) {
                return;
            }
            super.replace(fb, offset, length, text.length() > room ? text.substring(0, room) : text, attrs);
        }
    }

    /** Danh sach thanh vien chi de xem, khong chon. */
    private static final class NoSelection extends javax.swing.DefaultListSelectionModel {

        private static final long serialVersionUID = 1L;

        @Override
        public void setSelectionInterval(int a, int b) {
            // bo qua
        }

        @Override
        public void addSelectionInterval(int a, int b) {
            // bo qua
        }
    }
}
