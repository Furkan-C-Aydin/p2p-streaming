package edu.yeditepe.cse471.p2p.net;

import java.net.InetAddress;

public class PeerInfo {
    public final String peerId;
    public final InetAddress address;
    public final int discoveryPort;
    public final int tcpPort;
    public volatile long lastSeenMs;
    public volatile String catalogDigest;

    public PeerInfo(String peerId, InetAddress address, int discoveryPort, int tcpPort, long lastSeenMs, String catalogDigest) {
        this.peerId = peerId;
        this.address = address;
        this.discoveryPort = discoveryPort;
        this.tcpPort = tcpPort;
        this.lastSeenMs = lastSeenMs;
        this.catalogDigest = catalogDigest == null ? "" : catalogDigest;
    }

    @Override
    public String toString() {
        return address.getHostAddress() + ":" + tcpPort + " (peerId=" + peerId + ")";
    }
}
