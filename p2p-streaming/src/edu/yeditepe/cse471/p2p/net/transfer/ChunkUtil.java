package edu.yeditepe.cse471.p2p.net.transfer;

import java.io.*;
import java.nio.file.Files;

public final class ChunkUtil {
    private ChunkUtil() {}

    public static long fileSize(File f) {
        return f.length();
    }

    public static int totalChunks(long fileSize) {
        return (int) ((fileSize + ChunkProtocol.CHUNK_SIZE - 1) / ChunkProtocol.CHUNK_SIZE);
    }

    public static File chunkFile(File bufferDir, String videoName, int chunkIndex) {
        File partDir = new File(bufferDir, videoName + ".part");
        return new File(partDir, chunkIndex + ".chk");
    }

    public static void ensureDir(File dir) throws IOException {
        if (dir == null) throw new IOException("Buffer dir null");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create dir: " + dir.getAbsolutePath());
        }
    }

    public static void writeBytes(File out, byte[] data) throws IOException {
        Files.write(out.toPath(), data);
    }
}
