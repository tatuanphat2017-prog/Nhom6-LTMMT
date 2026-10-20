package vn.hutech.nhom06.client.net;

/**
 * Theo doi tien trinh mot lan gui / tai file.
 *
 * !!! Cac ham duoc goi tu thread mang (khong phai thread giao dien):
 * cap nhat Swing phai boc trong SwingUtilities.invokeLater(...).
 *
 * @author Ta Tuan Phat
 */
public interface FileTransferListener {

    /** Da gui / nhan duoc {@code done} tren tong {@code total} byte. */
    default void onProgress(long done, long total) {
    }

    /** Hoan tat (gui len server xong, hoac da luu file tai ve). */
    void onDone();

    /** That bai hoac bi huy; reason de hien cho nguoi dung. */
    void onFailed(String reason);
}
