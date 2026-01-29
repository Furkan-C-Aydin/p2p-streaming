package edu.yeditepe.cse471.p2p.net.catalog;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpControlServer {

    private final int tcpPort;
    private final LocalVideoCatalog localCatalog;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running = false;

    public TcpControlServer(int tcpPort, LocalVideoCatalog localCatalog) {
        this.tcpPort = tcpPort;
        this.localCatalog = localCatalog;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(tcpPort);
        running = true;

        acceptThread = new Thread(this::acceptLoop, "tcp-control-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = serverSocket.accept();
                Thread t = new Thread(() -> handleClient(s), "tcp-control-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                break; 
            }
        }
    }

    private void handleClient(Socket s) {
        try (Socket sock = s;
        		BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
        		OutputStream rawOut = sock.getOutputStream();
        		PrintWriter out = new PrintWriter(new OutputStreamWriter(rawOut, "UTF-8"), true);
        		java.io.DataOutputStream dout = new java.io.DataOutputStream(rawOut);) {

            String line = in.readLine();
            if (line == null) return;

            if ("CATALOG".equalsIgnoreCase(line.trim())) {
                for (VideoEntry ve : localCatalog.listVideos()) {
                	out.println("VIDEO|" + ve.name + "|" + ve.size + "|" + ve.hash);

                }
                out.println("END");
                return;
            }
            
            if (line.startsWith("META|")) {
            	String[] p = line.split("\\|", 2);
            	String id = (p.length == 2) ? p[1].trim() : "";

            	java.io.File target = resolveTarget(id);


                if (target == null || !target.exists() || !target.isFile()) {
                    out.println("ERR|NOTFOUND");
                    out.println("END");
                    return;
                }

                long size = target.length();
                int chunks = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.totalChunks(size);

                out.println("SIZE|" + size + "|CHUNKS|" + chunks);
                out.println("END");
                return;
            }
            
            
            if (line.startsWith("GETCHUNK|")) {
            	
            	String[] p = line.split("\\|");
            	if (p.length < 3) {
            	    out.println("ERR|ARGS");
            	    out.println("END");
            	    return;
            	}

            	String id = p[1].trim();
            	int idx = Integer.parseInt(p[2].trim());

            	java.io.File target = resolveTarget(id);


                if (target == null || !target.exists() || !target.isFile()) {
                    out.println("ERR|NOTFOUND");
                    out.println("END");
                    return;
                }

                long size = target.length();
                int total = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.totalChunks(size);
                if (idx < 0 || idx >= total) {
                    out.println("ERR|RANGE");
                    out.println("END");
                    return;
                }

                long offset = (long) idx * edu.yeditepe.cse471.p2p.net.transfer.ChunkProtocol.CHUNK_SIZE;
                int len = (int) Math.min(
                        edu.yeditepe.cse471.p2p.net.transfer.ChunkProtocol.CHUNK_SIZE,
                        size - offset
                );

                byte[] buf = new byte[len];
                try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(target, "r")) {
                    raf.seek(offset);
                    raf.readFully(buf);
                }

                byte[] header = ("DATA|" + idx + "|" + len + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                rawOut.write(header);
                rawOut.write(buf);
                rawOut.write("\nEND\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                rawOut.flush();
                return;
            }


            
            if (line.startsWith("HAS|")) {
                String[] p = line.split("\\|", 2);
                String id = (p.length == 2) ? p[1].trim() : "";

                boolean found;
                if (isSha256Hex(id)) {
                    found = (localCatalog.findFileByHash(id) != null);
                } else {
                    found = false;
                    for (VideoEntry ve : localCatalog.listVideos()) {
                        if (ve != null && ve.name != null && ve.name.equalsIgnoreCase(id)) {
                            found = true;
                            break;
                        }
                    }
                }

                out.println(found ? "YES" : "NO");
                out.println("END");
                return;
            }


            out.println("ERR|UnknownCommand");
            out.println("END");

        } catch (Exception ignored) {
        }
    }
    
    
    
    private java.io.File getRootFolderBestEffort() {
        return localCatalog.getRootFolder();
    }
    
    
    private static boolean isSha256Hex(String s) {
        if (s == null) return false;
        String x = s.trim();
        if (x.length() != 64) return false;
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            boolean hex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private File resolveTarget(String idOrName) {
        if (idOrName == null) return null;
        String x = idOrName.trim();
        if (x.isEmpty()) return null;

        if (isSha256Hex(x)) {
            File byHash = localCatalog.findFileByHash(x);
            if (byHash != null) return byHash;
        }

        File root = getRootFolderBestEffort();
        File byName = (root == null) ? null : new File(root, x);
        if (byName != null && byName.exists() && byName.isFile()) return byName;

        return null;
    }


}
