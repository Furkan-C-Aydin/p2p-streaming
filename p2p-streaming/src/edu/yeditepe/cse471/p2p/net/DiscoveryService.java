package edu.yeditepe.cse471.p2p.net;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DiscoveryService {

    private final String selfPeerId;
    private final int discoveryPort;
    private final int tcpPort;

    private final Consumer<Integer> onPeerCountChanged;

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final Map<String, Long> seenMsg = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private Thread rxThread;
    private Thread helloThread;
    private Thread cleanupThread;

    private volatile boolean running = false;

    private final List<InetSocketAddress> bootstrapPeers;

    private volatile String catalogDigest = "";

    public DiscoveryService(String selfPeerId, int discoveryPort, int tcpPort,
                            List<InetSocketAddress> bootstrapPeers,
                            Consumer<Integer> onPeerCountChanged) {
        this.selfPeerId = selfPeerId;
        this.discoveryPort = discoveryPort;
        this.tcpPort = tcpPort;
        this.bootstrapPeers = bootstrapPeers == null ? List.of() : bootstrapPeers;
        this.onPeerCountChanged = onPeerCountChanged;
    }

    public void setCatalogDigest(String digest) {
        this.catalogDigest = (digest == null) ? "" : digest;
    }

    public int getPeerCount() {
        return peers.size();
    }

    public Collection<PeerInfo> getPeersSnapshot() {
        return new ArrayList<>(peers.values());
    }

    public synchronized void start() throws SocketException {
        if (running) return;

        socket = new DatagramSocket(discoveryPort);
        socket.setBroadcast(true);

        running = true;

        rxThread = new Thread(this::rxLoop, "discovery-rx");
        rxThread.setDaemon(true);
        rxThread.start();

        helloThread = new Thread(this::helloLoop, "discovery-hello");
        helloThread.setDaemon(true);
        helloThread.start();

        cleanupThread = new Thread(this::cleanupLoop, "discovery-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();

        sendHello(Protocol.TTL_DEFAULT);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        try {
            DiscoveryMessage bye = DiscoveryMessage.bye(selfPeerId, tcpPort, discoveryPort);
            for (PeerInfo p : peers.values()) {
                sendTo(p.address, p.discoveryPort, bye.toWire());
            }
        } catch (Exception ignored) {}

        if (socket != null) socket.close();
        peers.clear();
        seenMsg.clear();
        firePeerCountChanged();
    }

    private void rxLoop() {
        byte[] buf = new byte[Protocol.MAX_DATAGRAM_BYTES];

        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);

                String raw = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), StandardCharsets.UTF_8).trim();
                DiscoveryMessage m = DiscoveryMessage.fromWire(raw);

                if (selfPeerId.equals(m.peerId)) continue;

                long now = System.currentTimeMillis();
                Long prev = seenMsg.putIfAbsent(m.msgId, now);
                if (prev != null && (now - prev) < Protocol.SEEN_MSG_TTL_MS) {
                    continue;
                }

                InetAddress senderAddr = pkt.getAddress();
                handle(senderAddr, m);

            } catch (SocketException se) {
                break;
            } catch (Exception e) {
                
            }
        }
    }

    private void handle(InetAddress senderAddr, DiscoveryMessage m) {
        long now = System.currentTimeMillis();

        switch (m.type) {
            case HELLO: {
                upsertPeer(senderAddr, m, now);

                DiscoveryMessage ack = DiscoveryMessage.helloAck(
                        m.msgId, selfPeerId, tcpPort, discoveryPort, catalogDigest
                );
                sendTo(senderAddr, m.discoveryPort, ack.toWire());

                if (m.ttl > 1) {
                    DiscoveryMessage fwd = new DiscoveryMessage(
                            MessageType.HELLO, m.msgId, m.ttl - 1,
                            m.peerId, m.tcpPort, m.discoveryPort, m.catalogDigest
                    );
                    forwardToKnownPeers(senderAddr, m.discoveryPort, fwd.toWire());
                }
                break;
            }
            case HELLO_ACK: {
                upsertPeer(senderAddr, m, now);
                break;
            }
            case BYE: {
                peers.remove(m.peerId);
                firePeerCountChanged();
                break;
            }
        }
    }

    private void upsertPeer(InetAddress addr, DiscoveryMessage m, long now) {
        PeerInfo p = peers.get(m.peerId);
        if (p == null) {
            p = new PeerInfo(m.peerId, addr, m.discoveryPort, m.tcpPort, now, m.catalogDigest);
            peers.put(m.peerId, p);
            firePeerCountChanged();
        } else {
            p.lastSeenMs = now;
            p.catalogDigest = m.catalogDigest;
        }
    }

    private void forwardToKnownPeers(InetAddress originalSender, int originalSenderDiscPort, String wire) {
        for (PeerInfo p : peers.values()) {
            if (p.address.equals(originalSender) && p.discoveryPort == originalSenderDiscPort) continue;
            sendTo(p.address, p.discoveryPort, wire);
        }
    }

    private void helloLoop() {
        while (running) {
            try {
                Thread.sleep(Protocol.HELLO_INTERVAL_MS);
                sendHello(Protocol.TTL_DEFAULT);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void cleanupLoop() {
        while (running) {
            try {
                Thread.sleep(2_000);

                long now = System.currentTimeMillis();

                for (Iterator<Map.Entry<String, Long>> it = seenMsg.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Long> e = it.next();
                    if (now - e.getValue() > Protocol.SEEN_MSG_TTL_MS) it.remove();
                }

                boolean changed = false;
                for (Iterator<Map.Entry<String, PeerInfo>> it = peers.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, PeerInfo> e = it.next();
                    if (now - e.getValue().lastSeenMs > Protocol.PEER_TIMEOUT_MS) {
                        it.remove();
                        changed = true;
                    }
                }
                if (changed) firePeerCountChanged();

            } catch (InterruptedException ignored) {
            }
        }
    }

    private void sendHello(int ttl) {
        DiscoveryMessage hello = DiscoveryMessage.hello(selfPeerId, tcpPort, discoveryPort, ttl, catalogDigest);

        try {
            sendTo(InetAddress.getByName("255.255.255.255"), discoveryPort, hello.toWire());
        } catch (Exception ignored) {}

        for (InetSocketAddress ep : bootstrapPeers) {
            try {
                sendTo(ep.getAddress(), ep.getPort(), hello.toWire());
            } catch (Exception ignored) {}
        }

        for (PeerInfo p : peers.values()) {
            sendTo(p.address, p.discoveryPort, hello.toWire());
        }
    }

    private void sendTo(InetAddress addr, int port, String wire) {
        try {
            byte[] data = wire.getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(data, data.length, addr, port);
            socket.send(pkt);
        } catch (Exception ignored) {}
    }

    private void firePeerCountChanged() {
        if (onPeerCountChanged != null) onPeerCountChanged.accept(getPeerCount());
    }

    public static List<InetSocketAddress> parseBootstrap(String s) {
        if (s == null || s.trim().isEmpty()) return List.of();
        List<InetSocketAddress> out = new ArrayList<>();
        String[] parts = s.split(",");
        for (String p : parts) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            String[] hp = t.split(":");
            if (hp.length != 2) continue;
            try {
                InetAddress a = InetAddress.getByName(hp[0].trim());
                int port = Integer.parseInt(hp[1].trim());
                out.add(new InetSocketAddress(a, port));
            } catch (Exception ignored) {}
        }
        return out;
    }
}
