package edu.yeditepe.cse471.p2p.net.catalog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalVideoCatalog {
    private volatile File rootFolder;

    private final Map<String, CachedHash> hashCache = new ConcurrentHashMap<>();

    private static final class CachedHash {
        final long size;
        final long lastModified;
        final String hash;
        CachedHash(long size, long lastModified, String hash) {
            this.size = size;
            this.lastModified = lastModified;
            this.hash = hash;
        }
    }

    public void setRootFolder(File rootFolder) {
        this.rootFolder = rootFolder;
    }

    public File getRootFolder() {
        return rootFolder;
    }

    public List<VideoEntry> listVideos() {
        File root = this.rootFolder;
        if (root == null || !root.isDirectory()) return Collections.emptyList();

        List<VideoEntry> out = new ArrayList<>();
        File[] files = root.listFiles();
        if (files == null) return out;

        for (File f : files) {
            if (f.isFile() && isVideoFile(f.getName())) {
                out.add(new VideoEntry(f.getName(), f.length(), sha256Cached(f)));
            }
        }
        return out;
    }

    public File findFileByHash(String sha256Hex) {
        File root = this.rootFolder;
        if (root == null || !root.isDirectory()) return null;
        if (sha256Hex == null) return null;

        String needle = sha256Hex.trim().toLowerCase();
        if (needle.isEmpty()) return null;

        File[] files = root.listFiles();
        if (files == null) return null;

        for (File f : files) {
            if (f.isFile() && isVideoFile(f.getName())) {
                String h = sha256Cached(f);
                if (needle.equalsIgnoreCase(h)) return f;
            }
        }
        return null;
    }

    private boolean isVideoFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov");
    }

    private String sha256Cached(File f) {
        try {
            String key = f.getAbsolutePath();
            long size = f.length();
            long lm = f.lastModified();

            CachedHash cached = hashCache.get(key);
            if (cached != null && cached.size == size && cached.lastModified == lm) {
                return cached.hash;
            }

            String h = sha256FileHex(f);
            hashCache.put(key, new CachedHash(size, lm, h));
            return h;
        } catch (Exception e) {
            return "";
        }
    }

    private static String sha256FileHex(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[1024 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                md.update(buf, 0, r);
            }
        }
        return hex(md.digest());
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
