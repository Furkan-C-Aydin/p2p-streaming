package edu.yeditepe.cse471.p2p.net;

import java.util.UUID;

public class DiscoveryMessage {
    public final MessageType type;
    public final String msgId;
    public final int ttl;
    public final String peerId;
    public final int tcpPort;
    public final int discoveryPort;
    public final String catalogDigest; 

    public DiscoveryMessage(MessageType type, String msgId, int ttl,
                            String peerId, int tcpPort, int discoveryPort,
                            String catalogDigest) {
        this.type = type;
        this.msgId = msgId;
        this.ttl = ttl;
        this.peerId = peerId;
        this.tcpPort = tcpPort;
        this.discoveryPort = discoveryPort;
        this.catalogDigest = catalogDigest == null ? "" : catalogDigest;
    }

    public static DiscoveryMessage hello(String peerId, int tcpPort, int discoveryPort, int ttl, String catalogDigest) {
        return new DiscoveryMessage(MessageType.HELLO, UUID.randomUUID().toString(), ttl, peerId, tcpPort, discoveryPort, catalogDigest);
    }

    public static DiscoveryMessage helloAck(String msgId, String peerId, int tcpPort, int discoveryPort, String catalogDigest) {
        return new DiscoveryMessage(MessageType.HELLO_ACK, msgId, 0, peerId, tcpPort, discoveryPort, catalogDigest);
    }

    public static DiscoveryMessage bye(String peerId, int tcpPort, int discoveryPort) {
        return new DiscoveryMessage(MessageType.BYE, UUID.randomUUID().toString(), 0, peerId, tcpPort, discoveryPort, "");
    }

    public String toWire() {
        return type.name() + "|" + msgId + "|" + ttl + "|" + peerId + "|" + tcpPort + "|" + discoveryPort + "|" + sanitize(catalogDigest);
    }

    public static DiscoveryMessage fromWire(String s) {
        String[] p = s.split("\\|", 7);
        if (p.length < 6) throw new IllegalArgumentException("Invalid message: " + s);

        MessageType type = MessageType.valueOf(p[0]);
        String msgId = p[1];
        int ttl = Integer.parseInt(p[2]);
        String peerId = p[3];
        int tcpPort = Integer.parseInt(p[4]);
        int discoveryPort = Integer.parseInt(p[5]);
        String digest = (p.length >= 7) ? p[6] : "";

        return new DiscoveryMessage(type, msgId, ttl, peerId, tcpPort, discoveryPort, digest);
    }

    private static String sanitize(String s) {
        return s.replace("|", "_");
    }
}
