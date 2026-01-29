package edu.yeditepe.cse471.p2p.net;

public final class Protocol {
    private Protocol() {}

    public static final int DISCOVERY_PORT_DEFAULT = 40000;
    public static final int TCP_PORT_DEFAULT = 50000;

    public static final int TTL_DEFAULT = 2;                 
    public static final int MAX_DATAGRAM_BYTES = 1400;       

    public static final long SEEN_MSG_TTL_MS = 60_000;       
    public static final long PEER_TIMEOUT_MS = 15_000;       
    public static final long HELLO_INTERVAL_MS = 5_000;      
}
