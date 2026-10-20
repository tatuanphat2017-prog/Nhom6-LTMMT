package vn.hutech.nhom06.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import vn.hutech.nhom06.client.net.ClientConnection;
import vn.hutech.nhom06.client.net.ClientEventListener;
import vn.hutech.nhom06.common.ChatMessage;
import vn.hutech.nhom06.common.Protocol;
import vn.hutech.nhom06.common.RoomInfo;

/**
 * Man hinh chat chinh.
 *
 *   Tren  : ten nguoi dung, dia chi server, nut Dang xuat
 *   Trai  : danh sach phong (ten + so nguoi), nut Tao phong / Vao phong / Roi phong
 *   Giua  : khung chat "[HH:mm] Ten: noi dung", thong bao vao/roi phong mau khac
 *   Phai  : thanh vien trong phong
 *   Duoi  : o nhap (toi da 1000 ky tu, Enter = gui) + nut Gui
 *
 * Nhan su kien tu ClientConnection (thread nhan tin) -> moi lenh cap nhat giao dien
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

    // ----- Trai: phong -----
    private final DefaultListModel<RoomInfo> roomModel = new DefaultListModel<>();
    private final JList<RoomInfo> lstRooms = new JList<>(roomModel);
    private final JLabel lblRooms = Theme.header("Phòng chat");
    private final JButton btnCreate = Theme.primaryButton("Tạo phòng");
    private final JButton btnJoin = Theme.primaryButton("Vào phòng");
    private final JButton btnLeave = Theme.secondaryButton("Rời phòng");

    // ----- Giua: chat -----
    private final JLabel lblRoomTitle = Theme.header("Chưa vào phòng");
    private final JLabel lblRoomHint = Theme.muted("Chọn một phòng bên trái rồi bấm \"Vào phòng\"");
    private final JTextPane chatPane = new JTextPane();
    private final JTextField txtInput = new JTextField();
    private final JLabel lblCounter = Theme.muted("0/" + Protocol.MAX_MESSAGE_LENGTH);
    private final JButton btnSend = Theme.primaryButton("Gửi");

    // ----- Phai: thanh vien -----
    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String> lstMembers = new JList<>(memberModel);
    private final JLabel lblMembers = Theme.header("Thành viên (0)");

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
        updateRoomState();

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
        setSize(1060, 660);
        setMinimumSize(new Dimension(860, 520));
        setLocationRelativeTo(null);
    }

    private void setNames() {   // dat ten de de kiem thu tu dong
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
    }

    // ===================== Dung giao dien =====================

    private void buildUi() {
        getContentPane().setBackground(Theme.BG);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buildTopBar(), BorderLayout.NORTH);
        getContentPane().add(buildRoomPanel(), BorderLayout.WEST);
        getContentPane().add(buildChatPanel(), BorderLayout.CENTER);
        getContentPane().add(buildMemberPanel(), BorderLayout.EAST);
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

    private JPanel buildRoomPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.PANEL);
        p.setPreferredSize(new Dimension(250, 0));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));
        p.add(lblRooms, BorderLayout.NORTH);

        lstRooms.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstRooms.setCellRenderer(new RoomRenderer());
        lstRooms.setFixedCellHeight(38);
        JScrollPane roomScroll = scroll(lstRooms);
        roomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        p.add(roomScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 8));
        buttons.setOpaque(false);
        buttons.add(btnCreate);
        buttons.add(btnJoin);
        buttons.add(btnLeave);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildChatPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.BG);
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
        btnSend.setPreferredSize(new Dimension(84, 38));
        sendBox.add(btnSend, BorderLayout.EAST);
        input.add(sendBox, BorderLayout.EAST);
        p.add(input, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildMemberPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Theme.PANEL);
        p.setPreferredSize(new Dimension(210, 0));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));
        p.add(lblMembers, BorderLayout.NORTH);
        lstMembers.setCellRenderer(new MemberRenderer());
        lstMembers.setFixedCellHeight(30);
        lstMembers.setSelectionModel(new NoSelection());
        JScrollPane memberScroll = scroll(lstMembers);
        memberScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        p.add(memberScroll, BorderLayout.CENTER);
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
        lstRooms.addListSelectionListener(e -> updateRoomState());
        lstRooms.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {   // nhap dup = vao phong
                    joinSelected();
                }
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
            return;   // dang o phong nay roi
        }
        connection.join(r.name());
    }

    private void sendMessage() {
        String text = txtInput.getText();
        if (currentRoom == null || text.isBlank()) {
            return;
        }
        connection.sendMessage(text);
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
            updateRoomState();
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
            chatPane.setText("");
            memberModel.clear();
            lblRoomTitle.setText("# " + room);
            lblRoomHint.setText("Đang tải lịch sử tin nhắn...");
            selectRoom(room);
            updateRoomState();
            txtInput.requestFocusInWindow();
        });
    }

    @Override
    public void onHistoryItem(ChatMessage m) {
        SwingUtilities.invokeLater(() -> appendMessage(m, true));
    }

    @Override
    public void onHistoryEnd(String room, int count) {
        SwingUtilities.invokeLater(() -> {
            if (count == 0) {
                appendCentered("Phòng chưa có tin nhắn nào. Hãy là người đầu tiên!", styDivider);
            } else {
                appendCentered("── " + count + " tin nhắn trước đó ──", styDivider);
            }
            lblRoomHint.setText("Bạn đang ở trong phòng này");
        });
    }

    @Override
    public void onMessage(ChatMessage m) {
        SwingUtilities.invokeLater(() -> {
            if (m.room().equalsIgnoreCase(currentRoom)) {
                appendMessage(m, false);
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
                appendSystem(user + " đã vào phòng");
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
                appendSystem(user + " đã rời phòng");
            }
        });
    }

    @Override
    public void onLeft(String room) {
        SwingUtilities.invokeLater(() -> {
            currentRoom = null;
            memberModel.clear();
            lblMembers.setText("Thành viên (0)");
            appendSystem("Bạn đã rời phòng " + room);
            lblRoomTitle.setText("Chưa vào phòng");
            lblRoomHint.setText("Chọn một phòng bên trái rồi bấm \"Vào phòng\"");
            updateRoomState();
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

    private void appendMessage(ChatMessage m, boolean history) {
        boolean mine = m.sender().equalsIgnoreCase(username);
        append("[" + TIME.get().format(new Date(m.timestamp())) + "] ", styTime);
        append(m.sender() + ": ", mine ? styMyName : styName);
        append(m.content() + "\n", history ? styHistory : styText);
    }

    private void appendSystem(String text) {
        append("[" + TIME.get().format(new Date()) + "] ", styTime);
        append("• " + text + "\n", stySystem);
    }

    private void appendCentered(String text, SimpleAttributeSet sty) {
        append(text + "\n", sty);
    }

    private void append(String text, AttributeSet style) {
        StyledDocument doc = chatPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ignored) {
            // khong xay ra vi luon chen o cuoi
        }
        chatPane.setCaretPosition(doc.getLength());   // tu cuon xuong tin moi nhat
    }

    private void setMembers(List<String> members) {
        memberModel.clear();
        members.forEach(memberModel::addElement);
        lblMembers.setText("Thành viên (" + members.size() + ")");
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

    private void updateRoomState() {
        boolean inRoom = currentRoom != null;
        RoomInfo sel = lstRooms.getSelectedValue();
        btnJoin.setEnabled(sel != null && !sel.name().equalsIgnoreCase(currentRoom));
        btnLeave.setEnabled(inRoom);
        txtInput.setEnabled(inRoom);
        btnSend.setEnabled(inRoom);
        lblCounter.setVisible(inRoom);
        lstRooms.repaint();
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
            default -> fallback;
        };
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

    // ===================== Lop phu =====================

    /** Moi dong: ten phong ben trai, so nguoi ben phai; phong dang o co vach xanh + chu xanh. */
    private final class RoomRenderer implements javax.swing.ListCellRenderer<RoomInfo> {

        private final JPanel row = new JPanel(new BorderLayout(8, 0));
        private final JLabel name = new JLabel();
        private final JLabel count = new JLabel();

        RoomRenderer() {
            row.add(name, BorderLayout.CENTER);
            row.add(count, BorderLayout.EAST);
            count.setFont(Theme.FONT_SMALL);
            count.setForeground(Theme.MUTED);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoomInfo> list, RoomInfo r, int index,
                boolean selected, boolean focus) {
            boolean here = r.name().equalsIgnoreCase(currentRoom);
            name.setText(r.name());
            name.setFont(here ? Theme.FONT_BOLD : Theme.FONT);
            name.setForeground(here ? Theme.PRIMARY : Theme.TEXT);
            count.setText(r.memberCount() + " người");
            row.setBackground(selected || here ? Theme.PRIMARY_LIGHT : Theme.BG);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, here ? Theme.PRIMARY : (selected ? Theme.PRIMARY_LIGHT : Theme.BG)),
                    BorderFactory.createEmptyBorder(0, 8, 0, 10)));
            return row;
        }
    }

    private final class MemberRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
            String name = String.valueOf(value);
            boolean me = name.equalsIgnoreCase(username);
            l.setText("<html><span style='color:#0F8A5F'>●</span>&nbsp;&nbsp;" + escape(name)
                    + (me ? " <span style='color:#6B7280'>(bạn)</span>" : "") + "</html>");
            l.setFont(Theme.FONT);
            l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            l.setBackground(Theme.BG);
            return l;
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
