package vn.hutech.nhom06.common;

/**
 * Mot tin nhan rieng giua 2 nguoi (tin chu hoac tin gui file).
 *
 * @param sender    nguoi gui
 * @param receiver  nguoi nhan
 * @param timestamp thoi diem server nhan tin (milliseconds)
 * @param content   noi dung (voi tin file: "[File] ten file")
 * @param file      thong tin file, null neu la tin chu
 *
 * @author Ta Tuan Phat
 */
public record PrivateMessage(String sender, String receiver, long timestamp, String content, FileInfo file) {

    public PrivateMessage(String sender, String receiver, long timestamp, String content) {
        this(sender, receiver, timestamp, content, null);
    }

    public static PrivateMessage ofFile(String sender, String receiver, long timestamp, FileInfo file) {
        return new PrivateMessage(sender, receiver, timestamp, "[File] " + file.name(), file);
    }

    public boolean isFile() {
        return file != null;
    }

    /** Nguoi con lai trong cuoc tro chuyen, nhin tu phia {@code me}. */
    public String other(String me) {
        return sender.equalsIgnoreCase(me) ? receiver : sender;
    }
}
