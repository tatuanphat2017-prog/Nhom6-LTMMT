package vn.hutech.nhom06.common;

/**
 * Mot tin nhan trong phong chat.
 *
 * @param room      ten phong
 * @param sender    ten nguoi gui
 * @param timestamp thoi diem server nhan tin (milliseconds, System.currentTimeMillis())
 * @param content   noi dung
 *
 * @author Ta Tuan Phat
 */
public record ChatMessage(String room, String sender, long timestamp, String content) {
}
