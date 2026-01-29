package edu.yeditepe.cse471.p2p.net.transfer;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ChunkClient {
    private ChunkClient() {}

    public static Meta fetchMeta(InetAddress addr, int tcpPort, String videoId) throws IOException {
        try (Socket sock = new Socket(addr, tcpPort)) {
            sock.setSoTimeout(ChunkProtocol.SOCKET_TIMEOUT_MS);

            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            writeLine(out, "META|" + videoId);

            String line = readLine(in);
            if (line == null) throw new IOException("No response");
            if (line.startsWith("ERR|")) throw new IOException(line);

            String[] p = line.split("\\|");
            long size = Long.parseLong(p[1]);
            int chunks = Integer.parseInt(p[3]);

            while (true) {
                String x = readLine(in);
                if (x == null || "END".equals(x)) break;
            }

            return new Meta(size, chunks);
        }
    }

    public static byte[] fetchChunk(InetAddress addr, int tcpPort, String videoId, int idx) throws IOException {
        try (Socket sock = new Socket(addr, tcpPort)) {
            sock.setSoTimeout(ChunkProtocol.SOCKET_TIMEOUT_MS);

            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            writeLine(out, "GETCHUNK|" + videoId + "|" + idx);

            String header = readLine(in);
            if (header == null) throw new IOException("No response");
            if (header.startsWith("ERR|")) throw new IOException(header);

            String[] p = header.split("\\|");
            int len = Integer.parseInt(p[2]);

            byte[] data = readExactly(in, len);

            while (true) {
                String x = readLine(in);
                if (x == null) break;
                if ("END".equals(x)) break;
            }

            return data;
        }
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            baos.write(b);
        }

        if (b == -1 && baos.size() == 0) return null;

        String s = baos.toString(StandardCharsets.UTF_8);
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1); // CRLF tolerans
        return s;
    }

    private static byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new EOFException("Unexpected EOF while reading chunk, need=" + len + " got=" + off);
            off += r;
        }
        return buf;
    }

    public static final class Meta {
        public final long size;
        public final int chunks;
        public Meta(long size, int chunks) { this.size = size; this.chunks = chunks; }
    }
}
