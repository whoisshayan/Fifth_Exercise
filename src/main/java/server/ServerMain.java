package server;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class ServerMain {
    public static void main(String[] args) throws Exception {
        int tcpPort = 8080;
        DataStore ds = new DataStore(); ds.load();
        NotificationHub hub = new NotificationHub(0);


        try (ServerSocket ss = new ServerSocket(tcpPort)) {
            System.out.println("Server listening on TCP "+tcpPort);
            while (true) {
                Socket s = ss.accept();
                System.out.println("Client "+s.getRemoteSocketAddress()+" connected");
                new Thread(new ClientHandler(s, ds, hub), "client-"+s.getPort()).start();
            }
        }
    }
}