package bo.wii.discordcloud.server.api;

import bo.wii.discordcloud.core.services.download.DownloaderService;
import bo.wii.discordcloud.core.structure.FileStruct;

import java.io.*;

public class MultiPartInputStream extends InputStream {

    private final DownloaderService downloader;
    private final FileStruct structure;
    private final String cacheDir;
    private final boolean autoDeleteParts;

    private final int startPartIndex;
    private final int endPartIndex;
    private final long bytesToSkipInFirstPart;
    private final long contentLength;

    private int currentPartIndex;
    private InputStream currentPartStream;
    private File currentPartFile;
    private long bytesSent = 0;
    private boolean initialized = false;

    public MultiPartInputStream(DownloaderService downloader, FileStruct structure,
                                int startPartIndex, int endPartIndex,
                                long bytesToSkipInFirstPart, long contentLength,
                                String cacheDir, boolean autoDeleteParts) {
        this.downloader = downloader;
        this.structure = structure;
        this.startPartIndex = startPartIndex;
        this.endPartIndex = endPartIndex;
        this.bytesToSkipInFirstPart = bytesToSkipInFirstPart;
        this.contentLength = contentLength;
        this.cacheDir = cacheDir.endsWith("/") ? cacheDir : cacheDir + "/";
        this.autoDeleteParts = autoDeleteParts;
        this.currentPartIndex = startPartIndex;
    }

    @Override
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int n = read(buf, 0, 1);
        return n == -1 ? -1 : (buf[0] & 0xFF);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (bytesSent >= contentLength) return -1;

        if (!initialized) {
            openNextPart();
            initialized = true;
        }

        if (currentPartStream == null) {
            // No more parts available
            return -1;
        }

        long remaining = contentLength - bytesSent;
        int toRead = (int) Math.min(len, remaining);

        int bytesRead = currentPartStream.read(buf, off, toRead);

        if (bytesRead == -1) {
            // Current part exhausted, move to next
            closeCurrentPart();
            if (currentPartIndex <= endPartIndex) {
                openNextPart();
                bytesRead = currentPartStream != null
                        ? currentPartStream.read(buf, off, toRead)
                        : -1;
            }
        }

        if (bytesRead > 0) {
            bytesSent += bytesRead;
        }

        return bytesRead;
    }

    @Override
    public void close() {
        closeCurrentPart();
        downloader.close();
    }

    // Downloads next part and opens an InputStream into it, skipping bytes if needed
    private void openNextPart() throws IOException {
        if (currentPartIndex > endPartIndex
                || currentPartIndex >= structure.getParts().size()) {
            currentPartStream = null;
            return;
        }

        File partFile = downloader.downloadPart(currentPartIndex, cacheDir);
        if (partFile == null) {
            throw new IOException("Failed to download part " + currentPartIndex);
        }

        currentPartFile = partFile;
        InputStream fileStream = new FileInputStream(partFile);

        // Skip bytes at the beginning of the first part only
        if (currentPartIndex == startPartIndex && bytesToSkipInFirstPart > 0) {
            long remaining = bytesToSkipInFirstPart;
            while (remaining > 0) {
                long skipped = fileStream.skip(remaining);
                if (skipped <= 0) {
                    fileStream.close();
                    throw new IOException("Failed to skip bytes in part " + currentPartIndex);
                }
                remaining -= skipped;
            }
        }

        currentPartStream = fileStream;
        currentPartIndex++;
    }

    private void closeCurrentPart() {
        if (currentPartStream != null) {
            try {
                currentPartStream.close();
            } catch (IOException ignored) {}
            currentPartStream = null;
        }

        if (autoDeleteParts && currentPartFile != null && currentPartFile.exists()) {
            boolean deleted = currentPartFile.delete();
            if (!deleted) {
                // TODO: sprawdzić czy pliki będą się usuwać automatycznie bo jeśli nie to dysk będzie się zapychał
                currentPartFile.deleteOnExit();
            }
            currentPartFile = null;
        }
    }
}




