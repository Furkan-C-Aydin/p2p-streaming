package edu.yeditepe.cse471.p2p.net.transfer;

public final class ChunkProtocol {
    private ChunkProtocol() {}

    public static final int CHUNK_SIZE = 256 * 1024; 
    public static final int SOCKET_TIMEOUT_MS = 4000;
}
