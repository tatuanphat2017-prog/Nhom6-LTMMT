package vn.hutech.nhom06.client.net;

/**
 * Mot lan gui / tai file dang chay. Goi {@link #cancel()} de huy.
 *
 * @author Ta Tuan Phat
 */
public final class FileTransfer {

    private final String fileName;
    private final long size;
    private final boolean upload;
    private volatile boolean cancelled;

    FileTransfer(String fileName, long size, boolean upload) {
        this.fileName = fileName;
        this.size = size;
        this.upload = upload;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    /** true = dang gui len, false = dang tai ve. */
    public boolean isUpload() {
        return upload;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
