package vn.hutech.nhom06.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.border.Border;

/**
 * Mau sac, font va nut bam dung chung cho giao dien Client.
 * Phong cach: nen trang, diem nhan xanh duong, don gian.
 *
 * @author Nhom 06
 */
public final class Theme {

    private Theme() {
    }

    // ===== Bang mau =====
    public static final Color BG = Color.WHITE;                       // nen chinh
    public static final Color PANEL = new Color(0xF4F7FB);            // nen cot trai/phai
    public static final Color BORDER = new Color(0xDCE3ED);           // duong vien
    public static final Color PRIMARY = new Color(0x1E6FD9);          // xanh chu dao
    public static final Color PRIMARY_HOVER = new Color(0x185BB5);
    public static final Color PRIMARY_PRESSED = new Color(0x134A94);
    public static final Color PRIMARY_LIGHT = new Color(0xE6F0FC);    // nen xanh nhat (nut phu, dong dang chon)
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color MUTED = new Color(0x6B7280);            // chu phu, gio gui
    public static final Color SYSTEM = new Color(0x0F8A5F);           // thong bao vao/roi phong
    public static final Color ERROR = new Color(0xC62828);
    public static final Color DISABLED = new Color(0xB8C4D6);

    // ===== Font (uu tien Segoe UI tren Windows, ho tro tieng Viet) =====
    public static final String FONT_NAME = pickFont("Segoe UI", "Tahoma", "Arial", Font.SANS_SERIF);
    public static final Font FONT = new Font(FONT_NAME, Font.PLAIN, 14);
    public static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);
    public static final Font FONT_SMALL = FONT.deriveFont(12f);
    public static final Font FONT_TITLE = new Font(FONT_NAME, Font.BOLD, 20);
    public static final Font FONT_HEADER = new Font(FONT_NAME, Font.BOLD, 15);

    private static String pickFont(String... names) {
        List<String> available = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String n : names) {
            if (available.contains(n)) {
                return n;
            }
        }
        return Font.SANS_SERIF;
    }

    // ===== Thanh phan =====

    /** Nut chinh: nen xanh, chu trang. */
    public static JButton primaryButton(String text) {
        return new RoundButton(text, true);
    }

    /** Nut phu: nen xanh nhat, chu xanh. */
    public static JButton secondaryButton(String text) {
        return new RoundButton(text, false);
    }

    public static JLabel header(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEADER);
        l.setForeground(TEXT);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(MUTED);
        return l;
    }

    public static JTextField textField(String text, int columns) {
        JTextField f = new JTextField(text, columns);
        styleInput(f);
        return f;
    }

    public static void styleInput(JComponent c) {
        c.setFont(FONT);
        c.setForeground(TEXT);
        c.setBackground(BG);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    public static Border lineBorder() {
        return BorderFactory.createLineBorder(BORDER);
    }

    /** Nut bo goc tu ve, de mau xanh hien dung tren moi he dieu hanh. */
    private static final class RoundButton extends JButton {

        private static final long serialVersionUID = 1L;
        private final boolean primary;

        RoundButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(FONT_BOLD);
            setForeground(primary ? Color.WHITE : PRIMARY);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setMargin(new Insets(8, 18, 8, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets m = getMargin();
            return new Dimension(fm.stringWidth(getText()) + m.left + m.right,
                    fm.getHeight() + m.top + m.bottom);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color bg;
            if (!isEnabled()) {
                bg = primary ? DISABLED : PANEL;
            } else if (getModel().isPressed()) {
                bg = primary ? PRIMARY_PRESSED : new Color(0xCFE0F7);
            } else if (getModel().isRollover()) {
                bg = primary ? PRIMARY_HOVER : new Color(0xD9E7FA);
            } else {
                bg = primary ? PRIMARY : PRIMARY_LIGHT;
            }
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            if (!primary) {
                g2.setColor(isEnabled() ? new Color(0xB9D0F0) : BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isEnabled() ? getForeground() : (primary ? Color.WHITE : DISABLED));
            int x = (w - fm.stringWidth(getText())) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }
}
