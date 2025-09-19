package client;


import com.google.gson.reflect.TypeToken;
import org.example.Json;
import org.example.Protocol;


import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;


public class ClientApp {
    public static void main(String[] args) throws Exception {
        String host = "localhost"; int port = 8080;
        try (Socket s = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
             Scanner sc = new Scanner(System.in)) {


// Read welcome line
            System.out.println("Server: "+in.readLine());


            String token = null;


// UDP listener for push
            DatagramSocket udp = new DatagramSocket();
            Thread pushThread = new Thread(new UdpListener(udp), "udp-listener");
            pushThread.setDaemon(true); pushThread.start();


            System.out.println(help());
            System.out.print("> ");
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if(line.isEmpty()) { System.out.print("> "); continue; }
                if(line.equals("exit")) break;


                Protocol.Request req = parseCommand(line, token, udp);
                if(req == null){ System.out.print("> "); continue; }


                out.write(Json.gson.toJson(req)); out.write("\n"); out.flush();
                String respLine = in.readLine();
                Protocol.Response resp = Json.gson.fromJson(respLine, Protocol.Response.class);
                if(!resp.ok){ System.out.println("Error: "+resp.error); }
                else { System.out.println(Json.gson.toJson(resp.data));
// capture token upon login/register
                    if(req.cmd.equals("login") || req.cmd.equals("register")){
                        Map<?,?> m = (Map<?,?>) resp.data;
                        token = (String) m.get("token");
                        System.out.println("[info] token set");
                    }
                }
                System.out.print("> ");
            }
            udp.close();
        }
    }


    private static String help(){
        return "Commands:\n" +
                " register <username> <password>\n" +
                " login <username> <password>\n" +
                " create_board <name>\n" +
                " list_boards\n" +
                " add_user_to_board <boardId> <userId>\n" +
                " view_board <boardId>   (subscribes to push)\n" +
                " add_task <boardId> <title> |desc=... |prio=LOW|MEDIUM|HIGH |due=2025-12-31T12:00:00Z\n" +
                " list_tasks <boardId>   |sort=createdAt|priority|dueDate |order=asc|desc |status=TODO|IN_PROGRESS|DONE |prio=LOW|MEDIUM|HIGH\n" +
                " update_task_status <taskId> <TODO|IN_PROGRESS|DONE>\n" +
                " delete_task <taskId>\n" +
                " exit\n";
    }

    private static Protocol.Request parseCommand(String line, String token, java.net.DatagramSocket udp)
            throws java.net.UnknownHostException {
        String[] parts = line.split(" ");
        String cmd = parts[0];
        java.util.Map<String,Object> args = new java.util.LinkedHashMap<>();

        switch (cmd) {
            case "register", "login" -> {
                if(parts.length<3){ System.out.println("usage: "+cmd+" <u> <p>"); return null; }
                args.put("username", parts[1]); args.put("password", parts[2]);
                return req(cmd, args, null);
            }
            case "create_board" -> {
                if(parts.length<2){ System.out.println("name?"); return null; }
                args.put("boardName", line.substring(cmd.length()).trim());
            }
            case "list_boards" -> { }
            case "add_user_to_board" -> {
                if(parts.length<3){ System.out.println("usage: add_user_to_board <boardId> <userId>"); return null; }
                args.put("boardId", Long.parseLong(parts[1]));
                args.put("userId", Long.parseLong(parts[2]));
            }
            case "view_board" -> {
                if(parts.length<2){ System.out.println("usage: view_board <boardId>"); return null; }
                long bid = Long.parseLong(parts[1]);
                args.put("boardId", bid);
                args.put("udpHost", java.net.InetAddress.getLocalHost().getHostAddress());
                args.put("udpPort", udp.getLocalPort());
                return req("view_board_subscribe", args, token);
            }
            case "add_task" -> {
                if(parts.length<3){ System.out.println("usage: add_task <boardId> <title> [|desc=.. |prio=.. |due=..]"); return null; }
                args.put("boardId", Long.parseLong(parts[1]));
                String after = line.substring(line.indexOf(parts[2]));
                String title = parts[2];
                String desc = ""; String prio = "LOW"; String due = null;
                for(String t: after.split(" \\|")){
                    if(t.startsWith("desc=")) desc = t.substring(5);
                    else if(t.startsWith("prio=")) prio = t.substring(5);
                    else if(t.startsWith("due="))  due  = t.substring(4);
                }
                args.put("title", title); args.put("description", desc); args.put("priority", prio);
                if(due!=null) args.put("dueDate", due);
            }
            case "list_tasks" -> {
                if(parts.length<2){ System.out.println("usage: list_tasks <boardId> [|sort=.. |order=.. |status=.. |prio=..]"); return null; }
                args.put("boardId", Long.parseLong(parts[1]));
                String after = line.substring(line.indexOf(parts[1])+parts[1].length()).trim();
                for(String t: after.split(" \\|")){
                    if(t.contains("=")){
                        String[] kv = t.split("=",2);
                        args.put(kv[0], kv[1]);
                    }
                }
            }
            case "update_task_status" -> {
                if(parts.length<3){ System.out.println("usage: update_task_status <taskId> <newStatus>"); return null; }
                args.put("taskId", Long.parseLong(parts[1])); args.put("newStatus", parts[2]);
            }
            case "delete_task" -> {
                if(parts.length<2){ System.out.println("usage: delete_task <taskId>"); return null; }
                args.put("taskId", Long.parseLong(parts[1]));
            }
            default -> {
                if(!java.util.Set.of("register","login","create_board","list_boards","add_user_to_board",
                        "view_board","add_task","list_tasks","update_task_status","delete_task").contains(cmd)){
                    System.out.println("unknown command; help:");
                    System.out.println(help());
                    return null;
                }
            }
        }
        return req(cmd, args, token);
    }

    private static Protocol.Request req(String cmd, java.util.Map<String,Object> args, String token){
        Protocol.Request r = new Protocol.Request();
        r.cmd = cmd; r.args = args; r.token = token; return r;
    }
}