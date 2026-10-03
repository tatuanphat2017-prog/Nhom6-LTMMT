package vn.hutech.nhom06.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Giao thuc tang ung dung cua nhom 06 (xem docs/protocol.md).
 *
 * Moi goi tin la MOT DONG van ban UTF-8, ket thuc bang '\n':
 *     LENH|truong1|truong2|...
 * Noi dung moi truong duoc ma hoa bang {@link #escape(String)} nen
 * khong bao gio chua ky tu '|' hay xuong dong.
 *
 * Client va Server deu dung chung lop nay de tao va tach goi tin,
 * nho vay hai ben luon hieu nhau.
 *
 * @author Ta Tuan Phat
 */
public final class Protocol {

    private Protocol() {
    }

    // ===== Cau hinh chung =====
    public static final int DEFAULT_PORT = 5000;
    public static final String SEPARATOR = "|";
    public static final int MAX_NAME_LENGTH = 20;
    public static final int MAX_ROOM_LENGTH = 30;
    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MAX_LINE_LENGTH = 8192;
    public static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final int MAX_HISTORY_LIMIT = 200;

    // ===== Lenh Client -> Server =====
    public static final String LOGIN = "LOGIN";             // LOGIN|ten
    public static final String LIST_ROOMS = "LIST_ROOMS";   // LIST_ROOMS
    public static final String CREATE_ROOM = "CREATE_ROOM"; // CREATE_ROOM|phong
    public static final String JOIN = "JOIN";               // JOIN|phong
    public static final String LEAVE = "LEAVE";             // LEAVE|phong
    public static final String MSG = "MSG";                 // MSG|phong|noidung
    public static final String HISTORY = "HISTORY";         // HISTORY|phong|soLuong
    public static final String MEMBERS = "MEMBERS";         // MEMBERS|phong
    public static final String LOGOUT = "LOGOUT";           // LOGOUT

    // ===== Phan hoi Server -> Client =====
    public static final String LOGIN_OK = "LOGIN_OK";         // LOGIN_OK|ten
    public static final String ROOM_LIST = "ROOM_LIST";       // ROOM_LIST|phong1:soNguoi|phong2:soNguoi...
    public static final String ROOM_CREATED = "ROOM_CREATED"; // ROOM_CREATED|phong
    public static final String JOINED = "JOINED";             // JOINED|phong
    public static final String LEFT = "LEFT";                 // LEFT|phong
    // MSG (server gui):   MSG|phong|nguoiGui|thoiGian(ms)|noidung
    public static final String HISTORY_ITEM = "HISTORY_ITEM"; // HISTORY_ITEM|phong|nguoiGui|thoiGian|noidung
    public static final String HISTORY_END = "HISTORY_END";   // HISTORY_END|phong|soLuong
    // MEMBERS (server gui): MEMBERS|phong|ten1|ten2...
    public static final String USER_JOINED = "USER_JOINED";   // USER_JOINED|phong|ten
    public static final String USER_LEFT = "USER_LEFT";       // USER_LEFT|phong|ten
    public static final String ERROR = "ERROR";               // ERROR|maLoi|moTa
    public static final String BYE = "BYE";                   // BYE|lyDo

    // ===== Ma loi =====
    public static final String ERR_BAD_COMMAND = "BAD_COMMAND";
    public static final String ERR_UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String ERR_NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String ERR_ALREADY_LOGGED_IN = "ALREADY_LOGGED_IN";
    public static final String ERR_INVALID_NAME = "INVALID_NAME";
    public static final String ERR_NAME_TAKEN = "NAME_TAKEN";
    public static final String ERR_INVALID_ROOM = "INVALID_ROOM";
    public static final String ERR_ROOM_EXISTS = "ROOM_EXISTS";
    public static final String ERR_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ERR_ALREADY_IN_ROOM = "ALREADY_IN_ROOM";
    public static final String ERR_NOT_IN_ROOM = "NOT_IN_ROOM";
    public static final String ERR_EMPTY_MESSAGE = "EMPTY_MESSAGE";
    public static final String ERR_MESSAGE_TOO_LONG = "MESSAGE_TOO_LONG";
    public static final String ERR_SERVER = "SERVER_ERROR";

    // Ten nguoi dung: chu (co dau), so, _ . - ; khong co khoang trang
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[\\p{L}\\p{M}\\p{N}_.\\-]{1," + MAX_NAME_LENGTH + "}$");
    // Ten phong: nhu tren, cho phep khoang trang o giua
    private static final Pattern ROOM_PATTERN =
            Pattern.compile("^[\\p{L}\\p{M}\\p{N}_.\\-]([\\p{L}\\p{M}\\p{N}_.\\- ]{0," + (MAX_ROOM_LENGTH - 2)
                    + "}[\\p{L}\\p{M}\\p{N}_.\\-])?$");

    /** Tao mot goi tin: build("MSG", "Phong A", "xin chao") -> "MSG|Phong A|xin chao" */
    public static String build(String command, Object... fields) {
        StringBuilder sb = new StringBuilder(command);
        for (Object f : fields) {
            sb.append(SEPARATOR).append(escape(f == null ? "" : String.valueOf(f)));
        }
        return sb.toString();
    }

    /**
     * Tach mot dong nhan duoc. Phan tu [0] la ten lenh (viet hoa),
     * cac phan tu sau la truong du lieu da giai ma.
     */
    public static String[] parse(String line) {
        if (line == null) {
            return new String[]{""};
        }
        String[] parts = line.split("\\|", -1);
        parts[0] = parts[0].trim().toUpperCase();
        for (int i = 1; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    /** Ma hoa: \ -> \\ , | -> \p , xuong dong -> \n , \r -> \r */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '|' -> sb.append("\\p");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Giai ma nguoc lai voi {@link #escape(String)}. */
    public static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case '\\' -> sb.append('\\');
                    case 'p' -> sb.append('|');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    public static boolean isValidRoomName(String room) {
        return room != null && ROOM_PATTERN.matcher(room).matches();
    }

    /** Tach truong cua ROOM_LIST ("Phong A:3") thanh RoomInfo. */
    public static List<RoomInfo> parseRoomList(String[] parts) {
        List<RoomInfo> list = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int idx = p.lastIndexOf(':');
            if (idx > 0) {
                try {
                    list.add(new RoomInfo(p.substring(0, idx), Integer.parseInt(p.substring(idx + 1))));
                } catch (NumberFormatException ignored) {
                    // bo qua truong loi
                }
            }
        }
        return list;
    }
}
