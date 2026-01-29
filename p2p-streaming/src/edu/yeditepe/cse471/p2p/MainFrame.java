package edu.yeditepe.cse471.p2p;

import java.util.*;


import edu.yeditepe.cse471.p2p.net.catalog.LocalVideoCatalog;
import edu.yeditepe.cse471.p2p.net.catalog.TcpCatalogClient;
import edu.yeditepe.cse471.p2p.net.catalog.TcpControlServer;
import edu.yeditepe.cse471.p2p.net.catalog.VideoEntry;




import edu.yeditepe.cse471.p2p.net.CatalogUtil;

import edu.yeditepe.cse471.p2p.net.DiscoveryService;
import edu.yeditepe.cse471.p2p.net.PeerInfo;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
	
	private final int discoveryPort;
	private final int tcpPort;
	private final String bootstrapStr;

	private final String selfPeerId = java.util.UUID.randomUUID().toString();
	private DiscoveryService discovery;


    private File rootVideoFolder;
    private File bufferFolder;
    private boolean connected = false;

    private final JTextField searchField = new JTextField();
    private final JButton searchButton = new JButton("Search");

    private final DefaultListModel<String> availableVideosModel = new DefaultListModel<>();
    private final JList<String> availableVideosList = new JList<>(availableVideosModel);

    private final DefaultListModel<String> activeStreamsModel = new DefaultListModel<>();
    private final JList<String> activeStreamsList = new JList<>(activeStreamsModel);

    private final JProgressBar globalBufferBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Status: Disconnected");

    private List<VideoEntry> lastScannedLocalVideos = new ArrayList<>();

    
    
    private final LocalVideoCatalog localCatalog = new LocalVideoCatalog();
    private TcpControlServer controlServer;

    private final Map<String, String> peerDigestCache = new HashMap<>();
    private final Map<String, List<VideoEntry>> peerCatalogCache = new HashMap<>();

    private javax.swing.Timer catalogRefreshTimer;
    
    private final Map<String, String> activeStreamByVideo = new HashMap<>();
    private final JLabel playerStatusLabel = new JLabel("No active stream", SwingConstants.CENTER);
    
    
    private static final int PLAYBACK_THRESHOLD_PCT = 10; 
    private volatile boolean playbackStarted = false;




    public MainFrame(int discoveryPort, int tcpPort, String bootstrapStr) {
        super("CSE471 P2P Streaming");

        this.discoveryPort = discoveryPort;
        this.tcpPort = tcpPort;
        this.bootstrapStr = bootstrapStr == null ? "" : bootstrapStr;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());

        globalBufferBar.setStringPainted(true);
        globalBufferBar.setValue(0);
        refreshStatus();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu streamMenu = new JMenu("Stream");
        JMenuItem connectItem = new JMenuItem("Connect");
        JMenuItem disconnectItem = new JMenuItem("Disconnect");
        JMenuItem setRootFolderItem = new JMenuItem("Set Root Video Folder");
        JMenuItem setBufferFolderItem = new JMenuItem("Set Buffer Folder");

        connectItem.addActionListener(e -> {
            try {
                if (discovery == null) {
                    discovery = new DiscoveryService(
                            selfPeerId,
                            discoveryPort,
                            tcpPort,
                            DiscoveryService.parseBootstrap(bootstrapStr),
                            (cnt) -> SwingUtilities.invokeLater(this::refreshStatus)
                    );
                }

                discovery.setCatalogDigest(CatalogUtil.digestForFolder(rootVideoFolder));

                discovery.start();
                try {
                    if (controlServer == null) {
                        controlServer = new TcpControlServer(tcpPort, localCatalog);
                        controlServer.start();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "TCP server start failed: " + ex.getMessage());
                }

                if (catalogRefreshTimer == null) {
                    catalogRefreshTimer = new javax.swing.Timer(3000, ev -> refreshNetworkCatalogAsync());
                }
                catalogRefreshTimer.start();

                connected = true;
                refreshStatus();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Connect failed: " + ex.getMessage());
            }
        });

        disconnectItem.addActionListener(e -> {
            connected = false;
            if (discovery != null) {
                discovery.stop();
            }
            
            if (catalogRefreshTimer != null) catalogRefreshTimer.stop();

            if (controlServer != null) {
                controlServer.stop();
                controlServer = null;
            }

            peerDigestCache.clear();
            peerCatalogCache.clear();

            refreshStatus();
            activeStreamsModel.clear();
            activeStreamsModel.clear();
            activeStreamByVideo.clear();

        });

        setRootFolderItem.addActionListener(e -> chooseRootVideoFolder());
        setBufferFolderItem.addActionListener(e -> chooseBufferFolder());

        streamMenu.add(connectItem);
        streamMenu.add(disconnectItem);
        streamMenu.addSeparator();
        streamMenu.add(setRootFolderItem);
        streamMenu.add(setBufferFolderItem);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e ->
                JOptionPane.showMessageDialog(
                        this,
                        "Developer: Furkan Can Aydin\nCourse: CSE471\nTerm Project: P2P Streaming",
                        "About",
                        JOptionPane.INFORMATION_MESSAGE
                )
        );
        helpMenu.add(aboutItem);

        menuBar.add(streamMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private Container buildContent() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
        searchPanel.setBorder(new TitledBorder("Search"));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        searchButton.addActionListener(e -> runSearch());

        JPanel center = new JPanel(new BorderLayout(10, 10));

        JScrollPane availableScroll = new JScrollPane(availableVideosList);
        availableScroll.setBorder(new TitledBorder("Available Videos on Network"));
        
        availableVideosList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = availableVideosList.getSelectedValue();
                    if (sel != null) {
                    	SelectedVideo sv = parseSelectedVideo(sel);
                    	if (sv != null) startStreamAsync(sv.displayName, sv.hash);
                    }
                }
            }
        });


        JScrollPane streamsScroll = new JScrollPane(activeStreamsList);
        streamsScroll.setBorder(new TitledBorder("Active Streams"));

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, availableScroll, streamsScroll);
        topSplit.setResizeWeight(0.5);

        JPanel playerPlaceholder = new JPanel(new BorderLayout());
        playerPlaceholder.setBorder(new TitledBorder("Video Player (placeholder)"));
        playerPlaceholder.add(playerStatusLabel, BorderLayout.CENTER);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, playerPlaceholder);
        centerSplit.setResizeWeight(0.6);

        center.add(centerSplit, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        JPanel bufferPanel = new JPanel(new BorderLayout(8, 8));
        bufferPanel.setBorder(new TitledBorder("Global Buffer Status"));
        bufferPanel.add(globalBufferBar, BorderLayout.CENTER);

        bottom.add(bufferPanel, BorderLayout.CENTER);
        bottom.add(statusLabel, BorderLayout.SOUTH);

        root.add(searchPanel, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    private void chooseRootVideoFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Root Video Folder (shared folder)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            rootVideoFolder = chooser.getSelectedFile();
            
            localCatalog.setRootFolder(rootVideoFolder);

            
            if (discovery != null) discovery.setCatalogDigest(CatalogUtil.digestForFolder(rootVideoFolder));

            scanLocalVideos();
            refreshAvailableVideosList(buildLocalVideoListLines(lastScannedLocalVideos));

            refreshStatus();
        }
    }

    private void chooseBufferFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Buffer Folder (download destination)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            bufferFolder = chooser.getSelectedFile();
            if (!bufferFolder.exists()) {
                bufferFolder.mkdirs();
            }
            refreshStatus();
        }
    }

    private void scanLocalVideos() {
        lastScannedLocalVideos.clear();
        if (rootVideoFolder == null || !rootVideoFolder.isDirectory()) return;

        localCatalog.setRootFolder(rootVideoFolder);
        lastScannedLocalVideos.addAll(localCatalog.listVideos());
    }

    private boolean isVideoFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov");
    }
    
    
    private static final String HASH_TAG = "[hash=";

    private List<String> buildLocalVideoListLines(List<VideoEntry> items) {
        List<String> out = new ArrayList<>();
        for (VideoEntry ve : items) {
            if (ve == null) continue;
            String h = (ve.hash == null) ? "" : ve.hash.trim();
            if (h.isEmpty()) continue;
            out.add(ve.name + "  [local] " + HASH_TAG + h + "]");
        }
        if (out.isEmpty()) out.add("(no local videos found)");
        return out;
    }

    private static final class SelectedVideo {
        final String displayName;
        final String hash;
        SelectedVideo(String displayName, String hash) {
            this.displayName = displayName;
            this.hash = hash;
        }
    }

    private SelectedVideo parseSelectedVideo(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("(") || s.startsWith("[")) return null;

        int hIdx = s.indexOf(HASH_TAG);
        if (hIdx < 0) return null;
        int start = hIdx + HASH_TAG.length();
        int end = s.indexOf("]", start);
        if (end < 0) return null;

        String hash = s.substring(start, end).trim();
        if (hash.isEmpty()) return null;

        String name = s;
        int sep = s.indexOf("  [");
        if (sep > 0) name = s.substring(0, sep).trim();

        return new SelectedVideo(name, hash);
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
    
    
    private void upsertActiveStreamLine(String streamKey, String newLine) {
        SwingUtilities.invokeLater(() -> {
            String old = activeStreamByVideo.get(streamKey);

            if (old == null) {
                activeStreamsModel.addElement(newLine);
            } else {
                int i = activeStreamsModel.indexOf(old);
                if (i >= 0) activeStreamsModel.set(i, newLine);
                else activeStreamsModel.addElement(newLine);
            }

            activeStreamByVideo.put(streamKey, newLine);
        });
    }




    private void runSearch() {
        String q = searchField.getText().trim().toLowerCase();

        if (!connected || discovery == null) {
            if (rootVideoFolder == null) {
                JOptionPane.showMessageDialog(this, "Please set Root Video Folder first.");
                return;
            }

            if (q.isEmpty()) {
                refreshAvailableVideosList(buildLocalVideoListLines(lastScannedLocalVideos));
                return;
            }

            List<VideoEntry> filtered = new ArrayList<>();
            for (VideoEntry ve : lastScannedLocalVideos) {
                if (ve.name != null && ve.name.toLowerCase().contains(q)) filtered.add(ve);
            }
            refreshAvailableVideosList(buildLocalVideoListLines(filtered));
            return;
        }

        List<String> results = buildNetworkVideoList(q);
        refreshAvailableVideosList(results);
    }

    private void refreshAvailableVideosList(List<String> items) {
        availableVideosModel.clear();

        if (!connected) {
            availableVideosModel.addElement("[Not connected] Showing local shared folder results only");
        }

        if (items.isEmpty()) {
            availableVideosModel.addElement("(no videos found)");
        } else {
            for (String s : items) availableVideosModel.addElement(s);
        }
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(connected ? "Connected" : "Disconnected");
        
        int peerCount = (discovery == null) ? 0 : discovery.getPeerCount();
        sb.append(" | Peers: ").append(peerCount);
        sb.append(" | UDP: ").append(discoveryPort).append(" | TCP: ").append(tcpPort);
        
        int netVideos = 0;
        for (List<VideoEntry> cat : peerCatalogCache.values()) netVideos += cat.size();
        sb.append(" | NetVideos: ").append(netVideos);



        sb.append(" | Root: ").append(rootVideoFolder != null ? rootVideoFolder.getAbsolutePath() : "(not set)");
        sb.append(" | Buffer: ").append(bufferFolder != null ? bufferFolder.getAbsolutePath() : "(not set)");

        statusLabel.setText(sb.toString());
    }
    
    
    private void refreshNetworkCatalogAsync() {
        new Thread(() -> {
            try {
                refreshNetworkCatalogOnce();
            } catch (Exception ignored) {}
        }, "catalog-refresh").start();
    }

    private void refreshNetworkCatalogOnce() {
        if (discovery == null) return;
        
        try {
            List<VideoEntry> selfCat = localCatalog.listVideos();
            peerCatalogCache.put(selfPeerId, selfCat);
            peerDigestCache.put(selfPeerId, CatalogUtil.digestForFolder(rootVideoFolder));
        } catch (Exception ignored) {}


        List<PeerInfo> peers = new ArrayList<>(discovery.getPeersSnapshot());

        boolean anyChange = false;

        for (PeerInfo p : peers) {
            String lastDigest = peerDigestCache.get(p.peerId);
            String newDigest = (p.catalogDigest == null) ? "" : p.catalogDigest;

            if (lastDigest != null && lastDigest.equals(newDigest) && peerCatalogCache.containsKey(p.peerId)) {
                continue;
            }

            try {
                List<VideoEntry> cat = TcpCatalogClient.fetchCatalog(p.address, p.tcpPort);
                peerCatalogCache.put(p.peerId, cat);
                peerDigestCache.put(p.peerId, newDigest);
                anyChange = true;
            } catch (Exception e) {
            }
        }

        if (anyChange) {
            SwingUtilities.invokeLater(() -> {
                String q = searchField.getText().trim().toLowerCase();
                if (q.isEmpty()) {
                    List<String> all = buildNetworkVideoList("");
                    refreshAvailableVideosList(all);
                } else {
                    List<String> filtered = buildNetworkVideoList(q);
                    refreshAvailableVideosList(filtered);
                }
                refreshStatus();
            });
        }
    }

    private List<String> buildNetworkVideoList(String queryLower) {
        class Group {
            long size = 0;
            final Set<String> names = new HashSet<>();
            int peers = 0; // unique per peer
        }

        Map<String, Group> byHash = new HashMap<>();

        for (Map.Entry<String, List<VideoEntry>> e : peerCatalogCache.entrySet()) {
            List<VideoEntry> cat = e.getValue();
            if (cat == null) continue;

            Set<String> seenInPeer = new HashSet<>();
            for (VideoEntry ve : cat) {
                if (ve == null) continue;
                if (ve.hash == null || ve.hash.isEmpty()) continue;

                String h = ve.hash;
                Group g = byHash.computeIfAbsent(h, k -> new Group());
                g.size = ve.size;
                g.names.add(ve.name);

                if (seenInPeer.add(h)) g.peers++;
            }
        }

        List<String> out = new ArrayList<>();

        for (Map.Entry<String, Group> e : byHash.entrySet()) {
            String hash = e.getKey();
            Group g = e.getValue();

            if (queryLower != null && !queryLower.isEmpty()) {
                boolean match = false;
                for (String n : g.names) {
                    if (n != null && n.toLowerCase().contains(queryLower)) { match = true; break; }
                }
                if (!match) continue;
            }

            List<String> names = new ArrayList<>(g.names);
            Collections.sort(names);
            String primary = names.isEmpty() ? "(unknown)" : names.get(0);
            int aliases = Math.max(0, names.size() - 1);

            out.add(primary
                    + (aliases > 0 ? " (+" + aliases + " aliases)" : "")
                    + "  [peers=" + g.peers + "] "
                    + HASH_TAG + hash + "]");
        }

        Collections.sort(out);
        if (out.isEmpty()) out.add("(no network videos found)");
        return out;
    }
    
    private void startStreamAsync(String videoName) {
        startStreamAsync(videoName, videoName);
    }

    private void startStreamAsync(String displayName, String videoHash) {
        new Thread(() -> {
            try {
                startStream(displayName, videoHash);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Start stream failed: " + ex.getMessage())
                );
            }
        }, "start-stream").start();
    }

    private void startStream(String videoName) throws Exception {
        startStream(videoName, videoName);
    }

    private void startStream(String displayName, String videoHash) throws Exception {

        if (bufferFolder == null) {
            throw new IllegalStateException("Please set Buffer Folder first.");
        }
        
        
        SwingUtilities.invokeLater(() -> activeStreamsModel.clear());


        playbackStarted = false;
        SwingUtilities.invokeLater(() -> playerStatusLabel.setText("BUFFERING... 0%"));

        if (!connected || discovery == null) {
            throw new IllegalStateException("Not connected.");
        }

        String key = (videoHash == null || videoHash.trim().isEmpty()) ? displayName : videoHash.trim();
        
        final String streamKey = key; 

        if (activeStreamByVideo.containsKey(streamKey)) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Stream already active for: " + displayName)
            );
            return;
        }

        List<PeerInfo> sources = new ArrayList<>();

        for (PeerInfo p : discovery.getPeersSnapshot()) {
            try {
                if (TcpCatalogClient.hasFile(p.address, p.tcpPort, key)) { // HASH ile sor
                    sources.add(p);
                }
            } catch (Exception ignored) {}
        }

        if (sources.isEmpty()) {
            boolean localHas = false;

            boolean keyIsHash = isSha256Hex(key);
            for (VideoEntry ve : localCatalog.listVideos()) {
                if (keyIsHash) {
                    if (ve.hash != null && ve.hash.equalsIgnoreCase(key)) { localHas = true; break; }
                } else {
                    if (ve.name != null && ve.name.equalsIgnoreCase(key)) { localHas = true; break; }
                }
            }

            if (localHas) {
                sources.add(new PeerInfo(
                        selfPeerId,
                        java.net.InetAddress.getLocalHost(),
                        discoveryPort,
                        tcpPort,
                        System.currentTimeMillis(),
                        ""
                ));
            } else {
                throw new IllegalStateException("No peer has this file: " + displayName);
            }
        }

        PeerInfo peer = sources.get(0);

        edu.yeditepe.cse471.p2p.net.transfer.ChunkClient.Meta meta = null;
        Exception lastMetaErr = null;
        PeerInfo metaSource = null;

        for (PeerInfo src : sources) {
            try {
                meta = edu.yeditepe.cse471.p2p.net.transfer.ChunkClient.fetchMeta(
                        src.address, src.tcpPort, key
                );
                metaSource = src;
                break;
            } catch (Exception ex) {
                lastMetaErr = ex;
            }
        }

        if (meta == null) {
            throw new IllegalStateException("META failed for all sources: " + lastMetaErr);
        }

        int total = meta.chunks;
        
        String baseName = normalizeDisplayNameForFile(displayName);

     java.io.File streamingFile = new java.io.File(
             bufferFolder,
             insertBeforeExtension(baseName, ".streaming")
     );

     preallocateStreamingFile(streamingFile, meta.size);

        
        int rr = 0; 
        java.util.Set<String> usedSources = new java.util.LinkedHashSet<>();
        upsertActiveStreamLine(streamKey, "STREAM " + displayName + " | 0% | START | sources=" + sources.size());



        edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.ensureDir(bufferFolder);

        java.io.File partDir = new java.io.File(bufferFolder, streamKey  + ".part");
        edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.ensureDir(partDir);

        java.util.List<Integer> order = new java.util.ArrayList<>();
        int PREFETCH = Math.min(120, total); 
        List<Integer> head = new ArrayList<>();
        for (int i = 0; i < PREFETCH; i++) head.add(i);

        List<Integer> tail = new ArrayList<>();
        for (int i = PREFETCH; i < total; i++) tail.add(i);

        Collections.shuffle(tail);

        order.clear();
        order.addAll(head);
        order.addAll(tail);

        java.util.Set<Integer> missing = new java.util.HashSet<>();
        int PROTECT = Math.min(120, total); 
        for (int idx : order) {
            if (idx < PROTECT) continue;     
            if (Math.random() < 0.20) missing.add(idx);
        }

        for (int idx : order) {
            if (missing.contains(idx)) continue;

            java.io.File outFile = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.chunkFile(bufferFolder, streamKey, idx);
            if (outFile.exists()) continue;

            int preferredIndex = rr % sources.size();
            rr++;

            PeerInfo srcUsed = null;
            byte[] chunk = null;
            Exception lastErr = null;

            for (int k = 0; k < sources.size(); k++) {
                PeerInfo src = sources.get((preferredIndex + k) % sources.size());
                try {
                    chunk = edu.yeditepe.cse471.p2p.net.transfer.ChunkClient.fetchChunk(
                            src.address, src.tcpPort, key, idx
                    );
                    srcUsed = src;
                    break;
                } catch (Exception ex) {
                    lastErr = ex;
                }
            }

            if (chunk == null) {
                throw new IllegalStateException("GETCHUNK failed for idx=" + idx + " err=" + lastErr);
            }
        
            edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.ensureDir(outFile.getParentFile());
            edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.writeBytes(outFile, chunk);
            
            writeChunkToStreamingFile(streamingFile, idx, chunk);

            
            usedSources.add(srcUsed.address.getHostAddress() + ":" + srcUsed.tcpPort);

            
            

            int pct = (int) ((countExistingChunks(streamKey, total) * 100.0) / total);
            final int finalPct = pct;
            
            String streamLine =
                    "STREAM " + displayName
                    + " | chunk " + idx + "/" + (total - 1)
                    + " | " + pct + "%"
                    + " | OK"
                    + " | src=" + srcUsed.address.getHostAddress() + ":" + srcUsed.tcpPort;

            appendChunkLogLine(streamLine);
            System.out.println(streamLine); 



            SwingUtilities.invokeLater(() -> {
                globalBufferBar.setValue(finalPct);

                if (!playbackStarted) {
                    if (finalPct >= PLAYBACK_THRESHOLD_PCT) {
                        playbackStarted = true;
                        playerStatusLabel.setText("PLAYBACK STARTED - opening player (" + finalPct + "%)");
                        new Thread(() -> openWithDesktopBestEffort(streamingFile), "open-player").start();

                    } else {
                        playerStatusLabel.setText("BUFFERING... " + finalPct + "%");
                    }
                }
            });
        }

        for (int round = 1; round <= 5; round++) {
            java.util.List<Integer> stillMissing = findMissingChunks(streamKey, total);
            if (stillMissing.isEmpty()) break;

            java.util.Collections.shuffle(stillMissing);

            for (int idx : stillMissing) {
                java.io.File outFile = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.chunkFile(bufferFolder, streamKey, idx);
                if (outFile.exists()) continue;

                try {
                	int preferredIndex = rr % sources.size();
                	rr++;

                	PeerInfo srcUsed = null;
                	byte[] chunk = null;
                	Exception lastErr = null;

                	for (int k = 0; k < sources.size(); k++) {
                	    PeerInfo src = sources.get((preferredIndex + k) % sources.size());
                	    try {
                	        chunk = edu.yeditepe.cse471.p2p.net.transfer.ChunkClient.fetchChunk(
                	                src.address, src.tcpPort, key, idx
                	        );
                	        srcUsed = src;
                	        break;
                	    } catch (Exception ex) {
                	        lastErr = ex;
                	    }
                	}

                	if (chunk == null) {
                	    throw new IllegalStateException("GETCHUNK failed for idx=" + idx + " err=" + lastErr);
                	}
        
                    edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.ensureDir(outFile.getParentFile());
                    edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.writeBytes(outFile, chunk);
                    
                    writeChunkToStreamingFile(streamingFile, idx, chunk);

                    
                    usedSources.add(srcUsed.address.getHostAddress() + ":" + srcUsed.tcpPort);


                    int pct = (int) ((countExistingChunks(streamKey, total) * 100.0) / total);
                    final int finalPct = pct;

                    String streamLine =
                            "STREAM " + displayName
                            + " | chunk " + idx + "/" + (total - 1)
                            + " | " + pct + "%"
                            + " | OK"
                            + " | src=" + srcUsed.address.getHostAddress() + ":" + srcUsed.tcpPort;

                    appendChunkLogLine(streamLine);
                    System.out.println(streamLine); 


                    SwingUtilities.invokeLater(() -> {
                        globalBufferBar.setValue(finalPct);

                        if (!playbackStarted) {
                            if (finalPct >= PLAYBACK_THRESHOLD_PCT) {
                                playbackStarted = true;
                                playerStatusLabel.setText("PLAYBACK STARTED: " + streamingFile.getName());

                                new Thread(() -> openWithDesktopBestEffort(streamingFile), "open-player").start();

                            } else {
                                playerStatusLabel.setText("BUFFERING... " + finalPct + "%");
                            }
                        } else {
                            playerStatusLabel.setText("PLAYING... buffer " + finalPct + "%");
                        }
                    });

                } catch (Exception ex) {
                    
                }
            }
        }

        java.util.List<Integer> remain = findMissingChunks(streamKey, total);
        if (!remain.isEmpty()) {
            throw new IllegalStateException("Download incomplete, missing chunks: " + remain);
        }

        reassembleIfComplete(streamKey, displayName, total, meta.size);
        
        appendChunkLogLine("COMPLETE " + displayName + " | 100% | sources=" + sources.size());
        System.out.println("COMPLETE " + displayName + " | 100% | sources=" + sources.size());


        SwingUtilities.invokeLater(() -> {
            playerStatusLabel.setText("DOWNLOAD COMPLETE: " + displayName + " -> " + displayName + ".complete");
        });

        upsertActiveStreamLine(
                streamKey,
                "COMPLETE " + displayName + " | 100% | sourcesUsed=" + usedSources
        );


    }

    private void stopStream(String streamKey) {
        String line = activeStreamByVideo.remove(streamKey);
        if (line == null) return;

        SwingUtilities.invokeLater(() -> {
            activeStreamsModel.removeElement(line);
            if (activeStreamByVideo.isEmpty()) {
                playerStatusLabel.setText("No active stream");
            }
        });
    }
    
    
    private void reassembleIfComplete(String videoHash, String outputBaseName, int totalChunks, long expectedSize) throws Exception {
        if (bufferFolder == null) throw new IllegalStateException("Buffer folder not set");

        java.io.File outFile = new java.io.File(bufferFolder, outputBaseName + ".complete");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
            for (int i = 0; i < totalChunks; i++) {
                java.io.File chk = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.chunkFile(bufferFolder, videoHash, i);
                if (!chk.exists()) {
                    throw new IllegalStateException("Missing chunk file: " + chk.getName());
                }
                byte[] data = java.nio.file.Files.readAllBytes(chk.toPath());
                fos.write(data);
            }
        }

        long actual = outFile.length();
        if (actual != expectedSize) {
            throw new IllegalStateException("Reassemble size mismatch. expected=" + expectedSize + " actual=" + actual);
        }
    }
    
    
    private int countExistingChunks(String videoHash, int total) {
        int cnt = 0;
        for (int i = 0; i < total; i++) {
            java.io.File f = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.chunkFile(bufferFolder, videoHash, i);
            if (f.exists()) cnt++;
        }
        return cnt;
    }

    private java.util.List<Integer> findMissingChunks(String videoHash, int total) {
        java.util.List<Integer> miss = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            java.io.File f = edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.chunkFile(bufferFolder, videoHash, i);
            if (!f.exists()) miss.add(i);
        }
        return miss;
    }
    
    private static String normalizeDisplayNameForFile(String displayName) {
        if (displayName == null) return "video";
        return displayName.replaceAll("\\s*\\(\\+\\d+\\s+aliases\\)$", "").trim();
    }

    private static String insertBeforeExtension(String name, String insert) {
        if (name == null || name.trim().isEmpty()) return "video" + insert;
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(0, dot) + insert + name.substring(dot);
        }
        return name + insert;
    }

    private void preallocateStreamingFile(java.io.File f, long size) throws java.io.IOException {
        edu.yeditepe.cse471.p2p.net.transfer.ChunkUtil.ensureDir(f.getParentFile());
        if (f.exists()) {
            f.delete();
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
            raf.setLength(size);
        }
    }

    private void writeChunkToStreamingFile(java.io.File f, int idx, byte[] data) throws java.io.IOException {
        long offset = (long) idx * edu.yeditepe.cse471.p2p.net.transfer.ChunkProtocol.CHUNK_SIZE;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw")) {
            raf.seek(offset);
            raf.write(data);
        }
    }

    private void openWithDesktopBestEffort(java.io.File f) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) return;
            java.awt.Desktop.getDesktop().open(f);
        } catch (Exception ignored) {
        }
    }
    
    private void appendChunkLogLine(String line) {
        SwingUtilities.invokeLater(() -> {
            activeStreamsModel.addElement(line);

            final int MAX = 300;
            while (activeStreamsModel.size() > MAX) {
                activeStreamsModel.remove(0);
            }

            int last = activeStreamsModel.size() - 1;
            if (last >= 0) activeStreamsList.ensureIndexIsVisible(last);
        });
    }






}
