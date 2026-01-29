package edu.yeditepe.cse471.p2p.net;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class CatalogUtil {
    private CatalogUtil() {}

    public static String digestForFolder(File root) {
        try {
            if (root == null || !root.isDirectory()) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            File[] files = root.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String line = f.getName() + ":" + f.length() + "\n";
                        md.update(line.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return hex(md.digest());
        } catch (Exception e) {
            return "";
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
