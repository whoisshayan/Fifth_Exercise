package server;

import org.example.Json;
import org.example.Models;
import org.example.Protocol;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;

/** Handles a single TCP client */
class ClientHandler implements Runnable {

    private final Socket socket;
    private final DataStore ds;
    private final NotificationHub hub;
    private final BoardService boardSvc;
    private final UserService userSvc;

    private Long authedUserId = null;

    ClientHandler(Socket socket, DataStore ds, NotificationHub hub) {
        this.socket = socket;
        this.ds = ds;
        this.hub = hub;
        this.boardSvc = new BoardService(ds);
        this.userSvc = new UserService(ds);
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            // welcome
            Map<String, Object> hello = Map.of("welcome", "TodoBoards Server", "version", "1.0");
            out.write(Json.gson.toJson(hello)); out.write("\n"); out.flush();

            String line;
            while ((line = in.readLine()) != null) {
                Protocol.Request req = Json.gson.fromJson(line, Protocol.Request.class);
                Protocol.Response resp = handle(req);
                out.write(Json.gson.toJson(resp)); out.write("\n"); out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private Protocol.Response handle(Protocol.Request req) {
        try {
            String c = req.cmd;
            Map<String, Object> a = (req.args == null) ? new HashMap<>() : req.args;

            // register / login بدون نیاز به توکن
            if (Set.of("register", "login").contains(c)) return auth(c, a);

            // token check
            Long uid = TokenService.validate(req.token);
            if (uid == null) return Protocol.Response.err("invalid_or_expired_token");
            this.authedUserId = uid;

            // routing
            switch (c) {
                case "logout" -> { return Protocol.Response.ok("bye"); }
                case "create_board" -> { return createBoard(uid, (String) a.get("boardName")); }
                case "list_boards" -> { return listBoards(uid); }
                case "add_user_to_board" -> {
                    long boardId = ((Number) a.get("boardId")).longValue();
                    long userId  = ((Number) a.get("userId")).longValue();
                    return addUser(uid, boardId, userId);
                }
                case "view_board_subscribe" -> {
                    long boardId = ((Number) a.get("boardId")).longValue();
                    String host  = (String) a.get("udpHost");
                    int port     = ((Number) a.get("udpPort")).intValue();
                    return subscribe(uid, boardId, host, port);
                }
                case "add_task" -> { return addTask(uid, a); }
                case "list_tasks" -> { return listTasks(uid, a); }
                case "update_task_status" -> { return updateTask(uid, a); }
                case "delete_task" -> {
                    long taskId = ((Number) a.get("taskId")).longValue();
                    return deleteTask(uid, taskId);
                }
                default -> { return Protocol.Response.err("unknown_command"); }
            }
        } catch (Exception ex) {
            return Protocol.Response.err("server_error: " + ex.getMessage());
        }
    }

    // =================== AUTH ===================

    private Protocol.Response auth(String cmd, Map<String, Object> a) throws IOException {
        String username = (String) a.get("username");
        String password = (String) a.get("password");
        if (username == null || password == null) return Protocol.Response.err("missing_credentials");

        if ("register".equals(cmd)) {
            if (userSvc.findByUsername(username) != null) return Protocol.Response.err("username_taken");
            byte[] salt = PasswordUtil.randomSalt();
            String saltHex = PasswordUtil.toHex(salt);
            String hash = PasswordUtil.sha256Hex(salt, password);

            Models.User u = ds.newUser(username, saltHex, hash);
            ds.users.put(u.id, u); ds.saveAll();
            String token = TokenService.issue(u.id);
            return Protocol.Response.ok(Map.of("userId", u.id, "token", token));
        } else { // login
            Models.User u = userSvc.findByUsername(username);
            if (u == null) return Protocol.Response.err("user_not_found");
            String calc = PasswordUtil.sha256Hex(hexToBytes(u.saltHex), password);
            if (!calc.equals(u.passHashHex)) return Protocol.Response.err("wrong_password");
            String token = TokenService.issue(u.id);
            return Protocol.Response.ok(Map.of("userId", u.id, "token", token));
        }
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length(); byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        return out;
    }

    // =================== BOARDS ===================

    private Protocol.Response createBoard(long uid, String name) throws IOException {
        if (name == null || name.isBlank()) return Protocol.Response.err("empty_board_name");
        Models.Board b = ds.newBoard(uid, name);
        ds.boards.put(b.id, b);
        ds.users.get(uid).boardIds.add(b.id);
        ds.saveAll();
        return Protocol.Response.ok(b);
    }

    private Protocol.Response listBoards(long uid) {
        List<Models.Board> list = new ArrayList<>();
        for (long bid : ds.users.get(uid).boardIds) {
            Models.Board b = ds.boards.get(bid);
            if (b != null) list.add(b);
        }
        return Protocol.Response.ok(list);
    }

    private Protocol.Response addUser(long uid, long boardId, long userId) throws IOException {
        if (!boardSvc.isOwner(uid, boardId)) return Protocol.Response.err("forbidden_not_owner");
        Models.Board b = ds.boards.get(boardId);
        if (b == null) return Protocol.Response.err("board_not_found");
        if (!ds.users.containsKey(userId)) return Protocol.Response.err("user_not_found");

        b.memberIds.add(userId);
        ds.users.get(userId).boardIds.add(boardId);
        ds.saveAll();
        return Protocol.Response.ok("added");
    }

    private Protocol.Response subscribe(long uid, long boardId, String host, int port) {
        if (!boardSvc.canView(uid, boardId)) return Protocol.Response.err("forbidden");
        hub.subscribe(boardId, new InetSocketAddress(host, port));
        return Protocol.Response.ok("subscribed");
    }

    // =================== TASKS ===================

    private Protocol.Response addTask(long uid, Map<String, Object> a) throws IOException {
        long boardId = ((Number) a.get("boardId")).longValue();
        if (!boardSvc.canView(uid, boardId)) return Protocol.Response.err("forbidden");

        String title = (String) a.get("title");
        String desc  = (String) a.getOrDefault("description", "");
        String p     = (String) a.getOrDefault("priority", "LOW");
        String due   = (String) a.getOrDefault("dueDate", null);

        Models.Task t = ds.newTask(
                boardId,
                title,
                desc,
                Models.Priority.valueOf(p),
                (due == null || due.isBlank()) ? null : Instant.parse(due)
        );
        ds.tasks.put(t.id, t); ds.saveAll();
        hub.push(boardId, "task_added", t);
        return Protocol.Response.ok(t);
    }

    private Protocol.Response listTasks(long uid, Map<String, Object> a) {
        long boardId = ((Number) a.get("boardId")).longValue();
        if (!boardSvc.canView(uid, boardId)) return Protocol.Response.err("forbidden");

        String sortBy = (String) a.getOrDefault("sortBy", "createdAt");
        String order  = (String) a.getOrDefault("order", "asc");
        String status = (String) a.getOrDefault("status", null);
        String prio   = (String) a.getOrDefault("priority", null);

        List<Models.Task> list = new ArrayList<>();
        for (Models.Task t : ds.tasks.values()) if (t.boardId == boardId) list.add(t);

        if (status != null) list.removeIf(t -> !t.status.name().equals(status));
        if (prio   != null) list.removeIf(t -> !t.priority.name().equals(prio));

        Comparator<Models.Task> cmp = switch (sortBy) {
            case "priority" -> Comparator.comparing(t -> t.priority);
            case "dueDate"  -> Comparator.comparing(t -> t.dueDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default         -> Comparator.comparing(t -> t.createdAt);
        };
        if ("desc".equalsIgnoreCase(order)) cmp = cmp.reversed();
        list.sort(cmp);

        return Protocol.Response.ok(list);
    }

    private Protocol.Response updateTask(long uid, Map<String, Object> a) throws IOException {
        long taskId = ((Number) a.get("taskId")).longValue();
        String newStatus = (String) a.get("newStatus");

        Models.Task t = ds.tasks.get(taskId);
        if (t == null) return Protocol.Response.err("task_not_found");
        if (!boardSvc.canView(uid, t.boardId)) return Protocol.Response.err("forbidden");

        t.status = Models.Status.valueOf(newStatus);
        ds.saveAll();
        hub.push(t.boardId, "task_updated", t);
        return Protocol.Response.ok(t);
    }

    private Protocol.Response deleteTask(long uid, long taskId) throws IOException {
        Models.Task t = ds.tasks.get(taskId);
        if (t == null) return Protocol.Response.err("task_not_found");
        if (!boardSvc.canView(uid, t.boardId)) return Protocol.Response.err("forbidden");

        ds.tasks.remove(taskId); ds.saveAll();
        hub.push(t.boardId, "task_deleted", Map.of("taskId", taskId));
        return Protocol.Response.ok("deleted");
    }
}