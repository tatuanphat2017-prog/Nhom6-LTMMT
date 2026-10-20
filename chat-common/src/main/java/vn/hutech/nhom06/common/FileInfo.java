package vn.hutech.nhom06.common;

import java.util.Locale;

/**
 * Thong tin mot file da gui len phong chat.
 *
 * @param id   ma file do server cap (32 ky tu hex), dung de tai ve
 * @param name ten file goc (da loai bo duong dan)
 * @param size dung luong (byte)
 *
 * @author Ta Tuan Phat
 */
public record FileInfo(String id, String name, long size) {

    /** 1536 -> "1.5 KB", 3_400_000 -> "3.2 MB" */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    /** Nhu formatSize nhung ghi them so byte chinh xac (dung khi bao vuot gioi han). */
    public static String formatSizeExact(long bytes) {
        return bytes < 1024 ? bytes + " B"
                : formatSize(bytes) + String.format(Locale.ROOT, " = %,d byte", bytes);
    }

    public String sizeText() {
        return formatSize(size);
    }
}
