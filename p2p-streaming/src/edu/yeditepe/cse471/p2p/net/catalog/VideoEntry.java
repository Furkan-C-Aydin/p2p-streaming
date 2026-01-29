package edu.yeditepe.cse471.p2p.net.catalog;

public class VideoEntry {
    public final String name;
    public final long size;

    public final String hash;

    public VideoEntry(String name, long size, String hash) {
        this.name = name;
        this.size = size;
        this.hash = (hash == null) ? "" : hash;
    }
}
