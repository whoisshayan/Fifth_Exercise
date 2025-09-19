package server;

import com.google.gson.reflect.TypeToken;
import org.example.Json;
import org.example.Models;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

class DataStore {

    private final Path dir        = Paths.get("data");
    private final Path usersFile  = dir.resolve("users.json");
    private final Path boardsFile = dir.resolve("boards.json");
    private final Path tasksFile  = dir.resolve("tasks.json");

    // یکدست با Models.X
    final Map<Long, Models.User>  users  = new HashMap<>();
    final Map<Long, Models.Board> boards = new HashMap<>();
    final Map<Long, Models.Task>  tasks  = new HashMap<>();

    void load() throws IOException {
        if (!Files.exists(dir)) Files.createDirectories(dir);
        readUsers();
        readBoards();
        readTasks();
    }

    private void readUsers() throws IOException {
        if (!Files.exists(usersFile)) return;
        Type t = new TypeToken<List<Models.User>>(){}.getType();
        List<Models.User> list = Json.gson.fromJson(Files.readString(usersFile), t);
        if (list != null) for (Models.User u : list) users.put(u.id, u);
    }

    private void readBoards() throws IOException {
        if (!Files.exists(boardsFile)) return;
        Type t = new TypeToken<List<Models.Board>>(){}.getType();
        List<Models.Board> list = Json.gson.fromJson(Files.readString(boardsFile), t);
        if (list != null) for (Models.Board b : list) boards.put(b.id, b);
    }

    private void readTasks() throws IOException {
        if (!Files.exists(tasksFile)) return;
        Type t = new TypeToken<List<Models.Task>>(){}.getType();
        List<Models.Task> list = Json.gson.fromJson(Files.readString(tasksFile), t);
        if (list != null) for (Models.Task x : list) tasks.put(x.id, x);
    }

    synchronized void saveAll() throws IOException {
        Files.writeString(usersFile,
                Json.gson.toJson(new ArrayList<>(users.values())),
                StandardCharsets.UTF_8);
        Files.writeString(boardsFile,
                Json.gson.toJson(new ArrayList<>(boards.values())),
                StandardCharsets.UTF_8);
        Files.writeString(tasksFile,
                Json.gson.toJson(new ArrayList<>(tasks.values())),
                StandardCharsets.UTF_8);
    }

    // --- سازنده‌های یکنواخت موجودیت‌ها ---

    Models.User newUser(String username, String saltHex, String passHashHex){
        Models.User u = new Models.User();
        u.id = org.example.Ids.nextUserId();
        u.username   = username;
        u.saltHex    = saltHex;
        u.passHashHex= passHashHex;
        u.createdAt  = Instant.now();
        return u;
    }

    Models.Board newBoard(long ownerId, String name){
        Models.Board b = new Models.Board();
        b.id        = org.example.Ids.nextBoardId();
        b.ownerId   = ownerId;
        b.name      = name;
        b.createdAt = Instant.now();
        b.memberIds.add(ownerId);
        return b;
    }

    Models.Task newTask(long boardId, String title, String desc, Models.Priority p, Instant due){
        Models.Task t = new Models.Task();
        t.id         = org.example.Ids.nextTaskId();
        t.boardId    = boardId;
        t.title      = title;
        t.description= desc;
        t.priority   = p;
        t.status     = Models.Status.TODO;
        t.createdAt  = Instant.now();
        t.dueDate    = due;  // می‌تونه null باشه
        return t;            // ← برگشتی که جا افتاده بود
    }
}
