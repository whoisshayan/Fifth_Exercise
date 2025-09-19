package client;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;


public class UdpListener implements Runnable {
    private final DatagramSocket sock;


    public UdpListener(DatagramSocket sock) { this.sock = sock; }


    @Override public void run() {
        byte[] buf = new byte[8192];
        System.out.println("[push] UDP listener started on "+sock.getLocalPort());
        while (!sock.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                sock.receive(p);
                String msg = new String(p.getData(), p.getOffset(), p.getLength(), java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("\n[push] "+msg);
                System.out.print("> ");
            } catch (IOException e) {
                break;
            }
        }
    }
}