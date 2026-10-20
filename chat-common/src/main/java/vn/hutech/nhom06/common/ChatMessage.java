package vn.hutech.nhom06.common;

/**
 * Mot tin nhan trong phong chat (tin chu hoac tin gui file).
 *
 * @param room      ten phong
 * @param sender    ten nguoi gui
 * @param timestamp thoi diem server nhan tin (milliseconds, System.currentTimeMillis())
 * @param content   noi dung (voi tin file: "[File] ten file")
 * @param file      thong tin file, null neu la tin chu
 *
 * @author Ta Tuan Phat
 */
public record ChatMessage(String room, String sender, long timestamp, String content, FileInfo file) {

    /** Tin nhan chu (khong kem file). */
    public ChatMessage(String room, String sender, long timestamp, String content) {
        this(room, sender, timestamp, content, null);
    }

    /** Tao tin nhan gui file. */
    public static ChatMessage ofFile(String room, String sender, long timestamp, FileInfo file) {
        return new ChatMessage(room, sender, timestamp, "[File] " + file.name(), file);
    }

    public boolean isFile() {
        return file != null;
    }
}
