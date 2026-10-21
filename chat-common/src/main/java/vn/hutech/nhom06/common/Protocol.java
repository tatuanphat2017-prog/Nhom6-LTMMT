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
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_ROOM_LENGTH = 30;
    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MAX_LINE_LENGTH = 8192;
    public static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final int MAX_HISTORY_LIMIT = 200;

    // ===== Truyen file =====
    /** Dung luong toi da moi file: 25 MB. Client va Server deu kiem tra. */
    public static final long MAX_FILE_SIZE = 25L * 1024 * 1024;
    /** Moi manh 4 KB -> Base64 ~5.5 nghin ky tu, nam gon trong MAX_LINE_LENGTH. */
    public static final int FILE_CHUNK_SIZE = 4096;
    public static final int MAX_FILE_NAME_LENGTH = 255;

    // ===== Lenh Client -> Server =====
    public static final String LOGIN = "LOGIN";             // LOGIN|ten|matKhau
    public static final String REGISTER = "REGISTER";       // REGISTER|ten|matKhau  (thanh cong = dang nhap luon)
    public static final String LIST_ROOMS = "LIST_ROOMS";   // LIST_ROOMS
    public static final String CREATE_ROOM = "CREATE_ROOM"; // CREATE_ROOM|phong
    public static final String JOIN = "JOIN";               // JOIN|phong
    public static final String LEAVE = "LEAVE";             // LEAVE|phong
    public static final String MSG = "MSG";                 // MSG|phong|noidung
    public static final String HISTORY = "HISTORY";         // HISTORY|phong|soLuong
    public static final String MEMBERS = "MEMBERS";         // MEMBERS|phong
    public static final String LOGOUT = "LOGOUT";           // LOGOUT
    public static final String LIST_ONLINE = "LIST_ONLINE"; // LIST_ONLINE  (xin lai danh sach nguoi online)
    // Tin nhan rieng (chi nguoi gui + nguoi nhan thay)
    public static final String PM = "PM";                   // PM|nguoiNhan|noiDung
    public static final String PM_HISTORY = "PM_HISTORY";   // PM_HISTORY|nguoiKia|soLuong
    public static final String PM_READ = "PM_READ";         // PM_READ|nguoiKia  (da doc het tin nguoiKia gui minh)
    public static final String LIST_USERS = "LIST_USERS";   // LIST_USERS   (xin danh sach moi tai khoan)
    public static final String LIST_UNREAD = "LIST_UNREAD"; // LIST_UNREAD  (xin so tin rieng chua doc)
    public static final String ROOM_READ = "ROOM_READ";     // ROOM_READ|phong  (dang xem phong, da doc het)
    public static final String LIST_ROOM_UNREAD = "LIST_ROOM_UNREAD"; // LIST_ROOM_UNREAD (xin so tin chua doc cua cac phong)
    // Gui file:  FILE_START -> (server FILE_ACCEPT) -> FILE_CHUNK x n -> FILE_END -> (server FILE_SAVED)
    // noiNhan = ten phong, hoac "@ten" de gui rieng cho 1 nguoi (ten phong khong the bat dau bang @)
    public static final String FILE_START = "FILE_START";   // FILE_START|maGui|noiNhan|tenFile|dungLuong
    public static final String FILE_CHUNK = "FILE_CHUNK";   // FILE_CHUNK|maGui|base64
    public static final String FILE_END = "FILE_END";       // FILE_END|maGui
    public static final String FILE_CANCEL = "FILE_CANCEL"; // FILE_CANCEL|maGui
    // Tai file:  FILE_GET -> (server FILE_DATA_START, FILE_DATA x n, FILE_DATA_END)
    public static final String FILE_GET = "FILE_GET";       // FILE_GET|maFile

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
    public static final String ONLINE_LIST = "ONLINE_LIST";   // ONLINE_LIST|ten1|ten2...  (gui moi khi co nguoi vao/thoat)
    public static final String USER_LIST = "USER_LIST";       // USER_LIST|ten1|ten2...    (moi tai khoan da dang ky, ca offline)
    public static final String UNREAD_LIST = "UNREAD_LIST";   // UNREAD_LIST|nguoiGui:soTin|...  (tin rieng chua doc)
    public static final String ROOM_UNREAD_LIST = "ROOM_UNREAD_LIST"; // ROOM_UNREAD_LIST|phong:soTin|... (phong tung vao)
    public static final String ROOM_SEEN = "ROOM_SEEN";       // ROOM_SEEN|phong|ten1|ten2...  (ai da xem tin moi nhat)
    public static final String PM_SEEN = "PM_SEEN";           // PM_SEEN|nguoiKia|1/0  (nguoiKia da xem tin cuoi minh gui chua)
    // PM (server gui cho CA nguoi gui va nguoi nhan): PM|nguoiGui|nguoiNhan|thoiGian|noiDung
    public static final String PM_FILE = "PM_FILE";                 // PM_FILE|nguoiGui|nguoiNhan|thoiGian|maFile|tenFile|dungLuong
    public static final String PM_HISTORY_ITEM = "PM_HISTORY_ITEM"; // PM_HISTORY_ITEM|nguoiGui|nguoiNhan|thoiGian|noiDung
    public static final String PM_HISTORY_FILE = "PM_HISTORY_FILE"; // PM_HISTORY_FILE|nguoiGui|nguoiNhan|thoiGian|maFile|tenFile|dungLuong
    public static final String PM_HISTORY_END = "PM_HISTORY_END";   // PM_HISTORY_END|nguoiKia|soLuong
    public static final String FILE_ACCEPT = "FILE_ACCEPT";   // FILE_ACCEPT|maGui   (bat dau gui manh)
    public static final String FILE_SAVED = "FILE_SAVED";     // FILE_SAVED|maGui|maFile
    public static final String FILE_REJECT = "FILE_REJECT";   // FILE_REJECT|maGui|maLoi|moTa
    // Tin file trong phong (phat cho ca phong) va trong lich su:
    public static final String FILE = "FILE";                 // FILE|phong|nguoiGui|thoiGian|maFile|tenFile|dungLuong
    public static final String HISTORY_FILE = "HISTORY_FILE"; // HISTORY_FILE|phong|nguoiGui|thoiGian|maFile|tenFile|dungLuong
    public static final String FILE_DATA_START = "FILE_DATA_START"; // FILE_DATA_START|maFile|dungLuong
    public static final String FILE_DATA = "FILE_DATA";             // FILE_DATA|maFile|base64
    public static final String FILE_DATA_END = "FILE_DATA_END";     // FILE_DATA_END|maFile
    public static final String FILE_DATA_ERROR = "FILE_DATA_ERROR"; // FILE_DATA_ERROR|maFile|maLoi|moTa

    // ===== Ma loi =====
    public static final String ERR_BAD_COMMAND = "BAD_COMMAND";
    public static final String ERR_UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    public static final String ERR_NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String ERR_ALREADY_LOGGED_IN = "ALREADY_LOGGED_IN";
    public static final String ERR_INVALID_NAME = "INVALID_NAME";
    public static final String ERR_NAME_TAKEN = "NAME_TAKEN";           // tai khoan dang dang nhap o noi khac
    public static final String ERR_WRONG_LOGIN = "WRONG_LOGIN";         // sai ten hoac mat khau (khong noi ro cai nao)
    public static final String ERR_USER_EXISTS = "USER_EXISTS";         // dang ky ten da co nguoi dung
    public static final String ERR_INVALID_PASSWORD = "INVALID_PASSWORD"; // mat khau qua ngan / qua dai
    public static final String ERR_INVALID_ROOM = "INVALID_ROOM";
    public static final String ERR_ROOM_EXISTS = "ROOM_EXISTS";
    public static final String ERR_ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ERR_ALREADY_IN_ROOM = "ALREADY_IN_ROOM";
    public static final String ERR_NOT_IN_ROOM = "NOT_IN_ROOM";
    public static final String ERR_EMPTY_MESSAGE = "EMPTY_MESSAGE";
    public static final String ERR_MESSAGE_TOO_LONG = "MESSAGE_TOO_LONG";
    public static final String ERR_SERVER = "SERVER_ERROR";
    public static final String ERR_FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String ERR_FILE_INVALID = "FILE_INVALID";
    public static final String ERR_FILE_BUSY = "FILE_BUSY";
    public static final String ERR_FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String ERR_USER_NOT_FOUND = "USER_NOT_FOUND";   // nguoi nhan khong online
    public static final String ERR_SELF_MESSAGE = "SELF_MESSAGE";       // gui tin rieng cho chinh minh

    /** Tien to cua noi nhan trong FILE_START khi gui file rieng: "@Lang". */
    public static final String USER_TARGET_PREFIX = "@";

    // Ma lan gui do client tu dat; ma file do server cap (UUID bo dau '-')
    private static final Pattern TRANSFER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,32}$");
    private static final Pattern FILE_ID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");

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

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH
                && password.length() <= MAX_PASSWORD_LENGTH;
    }

    public static boolean isValidRoomName(String room) {
        return room != null && ROOM_PATTERN.matcher(room).matches();
    }

    public static boolean isValidTransferId(String id) {
        return id != null && TRANSFER_ID_PATTERN.matcher(id).matches();
    }

    /** Ma file chi gom 32 ky tu hex -> khong the chen "../" de doc file ngoai thu muc uploads. */
    public static boolean isValidFileId(String id) {
        return id != null && FILE_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Lam sach ten file: bo duong dan ("C:\a\b.txt" -> "b.txt"), bo ky tu dieu khien
     * va ky tu Windows khong cho phep, cat con toi da MAX_FILE_NAME_LENGTH ky tu.
     */
    public static String cleanFileName(String name) {
        if (name == null) {
            return "file";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String n = name.substring(slash + 1)
                .replaceAll("[\\p{Cntrl}<>:\"|?*]", "_")
                .trim();
        while (n.endsWith(".")) {
            n = n.substring(0, n.length() - 1).trim();
        }
        if (n.isEmpty()) {
            return "file";
        }
        if (n.length() > MAX_FILE_NAME_LENGTH) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 16 ? n.substring(dot) : "";
            n = n.substring(0, MAX_FILE_NAME_LENGTH - ext.length()) + ext;
        }
        return n;
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
