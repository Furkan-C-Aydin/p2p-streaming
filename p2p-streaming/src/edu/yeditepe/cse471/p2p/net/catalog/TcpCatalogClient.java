package edu.yeditepe.cse471.p2p.net.catalog;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class TcpCatalogClient {
    private TcpCatalogClient() {}

    public static List<VideoEntry> fetchCatalog(InetAddress addr, int tcpPort) throws IOException {
        List<VideoEntry> out = new ArrayList<>();

        try (Socket sock = new Socket(addr, tcpPort)) {
            sock.setSoTimeout(2000);

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
            BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));

            pw.println("CATALOG");

            String line;
            while ((line = br.readLine()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("VIDEO|")) {
                	String[] p = line.split("\\|");
                	if (p.length >= 3) {
                	    String name = p[1];
                	    long size = 0;
                	    try { size = Long.parseLong(p[2]); } catch (Exception ignored) {}
                	    String hash = (p.length >= 4) ? p[3] : "";
                	    out.add(new VideoEntry(name, size, hash));
                	}
                }
            }
        }

        return out;
    }
    
    public static boolean hasFile(InetAddress addr, int tcpPort, String filename) throws IOException {
        try (Socket sock = new Socket(addr, tcpPort)) {
            sock.setSoTimeout(2000);

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
            BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));

            pw.println("HAS|" + filename);

            String line = br.readLine();
            while (true) {
                String x = br.readLine();
                if (x == null || "END".equals(x)) break;
            }
            return "YES".equalsIgnoreCase(line != null ? line.trim() : "");
        }
    }

}
