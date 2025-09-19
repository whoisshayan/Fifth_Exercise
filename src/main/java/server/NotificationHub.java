package server;


import org.example.Protocol;
import org.example.Json;


import java.io.IOException;
import java.net.*;
import java.util.*;


/** UDP fan‑out by boardId. */
class NotificationHub {
    private final DatagramSocket socket;
    // boardId -> set of (host,port)
    private final Map<Long, Set<InetSocketAddress>> subscribers = new HashMap<>();


    NotificationHub(int udpPort) throws SocketException { this.socket = new DatagramSocket(); }


    synchronized void subscribe(long boardId, InetSocketAddress addr){
        subscribers.computeIfAbsent(boardId, k-> new HashSet<>()).add(addr);
    }
    synchronized void unsubscribeAll(InetSocketAddress addr){
        for(Set<InetSocketAddress> set: subscribers.values()) set.remove(addr);
    }


    void push(long boardId, String event, Object payload){
        Protocol.Push p = new Protocol.Push(); p.event = event; p.data = payload;
        byte[] bytes = Json.gson.toJson(p).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Set<InetSocketAddress> targets;
        synchronized (this){
            targets = new HashSet<>(subscribers.getOrDefault(boardId, Set.of()));
        }
        for(InetSocketAddress to: targets){
            try {
                DatagramPacket pkt = new DatagramPacket(bytes, bytes.length, to);
                socket.send(pkt);
            } catch (IOException ignored) {}
        }
    }
}