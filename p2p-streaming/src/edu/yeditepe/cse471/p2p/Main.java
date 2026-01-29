package edu.yeditepe.cse471.p2p;

import javax.swing.*;
import edu.yeditepe.cse471.p2p.net.Protocol;

public class Main {
    public static void main(String[] args) {
        int discPort = Integer.parseInt(System.getProperty("discPort", String.valueOf(Protocol.DISCOVERY_PORT_DEFAULT)));
        int tcpPort  = Integer.parseInt(System.getProperty("tcpPort",  String.valueOf(Protocol.TCP_PORT_DEFAULT)));
        String bootstrap = System.getProperty("bootstrap", ""); // örn: "127.0.0.1:40001"

        SwingUtilities.invokeLater(() -> {
            MainFrame f = new MainFrame(discPort, tcpPort, bootstrap);
            f.setVisible(true);
        });
    }
}
