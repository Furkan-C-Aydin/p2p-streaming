package edu.yeditepe.cse471.p2p;

import edu.yeditepe.cse471.p2p.net.CatalogUtil;
import edu.yeditepe.cse471.p2p.net.DiscoveryService;
import edu.yeditepe.cse471.p2p.net.PeerInfo;
import edu.yeditepe.cse471.p2p.net.Protocol;
import edu.yeditepe.cse471.p2p.net.catalog.LocalVideoCatalog;
import edu.yeditepe.cse471.p2p.net.catalog.TcpControlServer;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Headless entrypoint for Docker/CLI tests (no Swing UI).
 *
 * System properties:
 *  -DdiscPort=40000
 *  -DtcpPort=50000
 *  -Dbootstrap="172.20.0.11:40000,172.21.0.11:40000"
 *  -Droot=/videos
 *  -DpeerId=peer1
 */
public class HeadlessPeer {

    public static void main(String[] args) throws Exception {
        int discPort = Integer.parseInt(System.getProperty("discPort", String.valueOf(Protocol.DISCOVERY_PORT_DEFAULT)));
        int tcpPort  = Integer.parseInt(System.getProperty("tcpPort",  String.valueOf(Protocol.TCP_PORT_DEFAULT)));
        String bootstrap = System.getProperty("bootstrap", "");
        String rootPath = System.getProperty("root", "/videos");
        String peerId = System.getProperty("peerId", "peer-" + UUID.randomUUID());

        File root = new File(rootPath);
        if (!root.isDirectory()) {
            System.err.println("[" + peerId + "] Root folder is not a directory: " + root.getAbsolutePath());
        }

        LocalVideoCatalog catalog = new LocalVideoCatalog();
        catalog.setRootFolder(root);

        TcpControlServer tcp = new TcpControlServer(tcpPort, catalog);
        tcp.start();

        DiscoveryService discovery = new DiscoveryService(
                peerId,
                discPort,
                tcpPort,
                DiscoveryService.parseBootstrap(bootstrap),
                (cnt) -> System.out.println("[" + peerId + "] peers=" + cnt)
        );

        discovery.setCatalogDigest(CatalogUtil.digestForFolder(root));
        discovery.start();

        System.out.println("[" + peerId + "] started at " + Instant.now());
        System.out.println("[" + peerId + "] discPort=" + discPort + " tcpPort=" + tcpPort + " root=" + root.getAbsolutePath());
        System.out.println("[" + peerId + "] bootstrap=" + bootstrap);

        while (true) {
            Thread.sleep(5000);
            List<PeerInfo> list = new ArrayList<>(discovery.getPeersSnapshot());
            if (!list.isEmpty()) {
                System.out.println("[" + peerId + "] peer snapshot:");
                for (PeerInfo p : list) {
                    System.out.println("  - " + p.peerId + " @ " + p.address.getHostAddress() + ":" + p.tcpPort + " (disc=" + p.discoveryPort + ")");
                }
            }
        }
    }
}
