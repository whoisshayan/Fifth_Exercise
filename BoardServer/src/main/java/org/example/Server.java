package org.example;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.lang.reflect.Type;

public class Server {
    private static final int PORT = 8080;
    private static ServerSocket serverSocket;
    private static final Gson gson = new Gson();

    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final Map<String, Board> boards = new ConcurrentHashMap<>();
    private static final Map<String, String> sessions = new ConcurrentHashMap<>();

    private static int boardIdCounter = 1;
    private static int taskIdCounter = 1;

    static {
        loadData();
    }

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Task Management Board Server started on port " + PORT);
            System.out.println("Server ready to accept connections.");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // ------------------------- ClientHandler -------------------------
    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        private String currentUser = null;
        private String currentBoardId = null;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                JsonObject welcomeResponse = new JsonObject();
                welcomeResponse.addProperty("status", "success");
                welcomeResponse.addProperty("message", "Welcome to Task Management Board Server!");
                JsonObject data = new JsonObject();
                data.addProperty("available_commands", "register, login, logout, create_board, list_boards, add_user_to_board, view_board");
                welcomeResponse.add("data", data);
                out.println(gson.toJson(welcomeResponse));
                out.flush();

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    String response = handleJsonCommand(inputLine);
                    out.println(response);
                    out.flush();
                }
            } catch (IOException e) {
                System.err.println("Client handler error: " + e.getMessage());
            } finally {
                try {
                    if (currentUser != null) {
                        sessions.remove(currentUser);
                    }
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private String handleJsonCommand(String jsonInput) {
            try {
                JsonObject request = gson.fromJson(jsonInput, JsonObject.class);
                if (request == null || !request.has("command")) {
                    return wrapError("Invalid JSON format");
                }
                String command = safeGetAsString(request, "command");
                JsonObject payload = request.has("payload") && request.get("payload").isJsonObject()
                        ? request.getAsJsonObject("payload")
                        : new JsonObject();

                String result = executeCommand(command, payload);

                JsonObject response = new JsonObject();
                response.addProperty("status", isErrorMessage(result) ? "error" : "success");
                response.addProperty("message", result);
                response.add("data", new JsonObject());
                return gson.toJson(response);
            } catch (JsonSyntaxException e) {
                return wrapError("Invalid JSON format");
            } catch (Exception e) {
                return wrapError("Invalid JSON format");
            }
        }

        private boolean isErrorMessage(String msg) {
            if (msg == null) return true;
            String m = msg.toLowerCase(Locale.ROOT);
            return m.startsWith("error:")
                    || m.contains("invalid")
                    || m.contains("not found")
                    || m.contains("already exists")
                    || m.contains("denied")
                    || m.contains("please login");
        }

        private String executeCommand(String command, JsonObject payload) {
            if (command == null) return "Error: Unknown command: null";
            switch (command) {
                case "register":
                    if (hasAll(payload, "username", "password")) {
                        return register(payload.get("username").getAsString(), payload.get("password").getAsString());
                    }
                    return "Error: Missing username or password";

                case "login":
                    if (hasAll(payload, "username", "password")) {
                        return login(payload.get("username").getAsString(), payload.get("password").getAsString());
                    }
                    return "Error: Missing username or password";

                case "logout":
                    return logout();

                case "create_board":
                    if (hasAll(payload, "boardName")) {
                        return createBoard(payload.get("boardName").getAsString());
                    }
                    return "Error: Missing board name";

                case "list_boards":
                    return listBoards();

                case "add_user_to_board":
                    if (hasAll(payload, "boardId", "username")) {
                        return addUserToBoard(payload.get("boardId").getAsString(), payload.get("username").getAsString());
                    }
                    return "Error: Missing board ID or username";

                case "view_board":
                    if (hasAll(payload, "boardId")) {
                        return viewBoard(payload.get("boardId").getAsString());
                    }
                    return "Error: Missing board ID";

                case "add_task":
                    if (hasAll(payload, "title", "description")) {
                        return addTask(payload.get("title").getAsString(), payload.get("description").getAsString());
                    }
                    return "Error: Missing title or description";

                case "list_tasks":
                    return listTasks();

                case "update_task_status":
                    if (hasAll(payload, "taskId", "status")) {
                        return updateTaskStatus(payload.get("taskId").getAsString(), payload.get("status").getAsString());
                    }
                    return "Error: Missing task ID or status";

                case "delete_task":
                    if (hasAll(payload, "taskId")) {
                        return deleteTask(payload.get("taskId").getAsString());
                    }
                    return "Error: Missing task ID";

                case "exit_board":
                    return exitBoard();

                default:
                    return "Error: Unknown command: " + command;
            }
        }


        private String register(String username, String password) {
            if (users.containsKey(username)) {
                return "Username already exists";
            }
            String hashedPassword = hashPassword(password);
            users.put(username, new User(username, hashedPassword));
            saveData();
            return "User registered successfully";
        }

        private String login(String username, String password) {
            if (isLoggedIn()) {
                return "Already logged in. Please logout first.";
            }
            User user = users.get(username);
            String hashedPassword = hashPassword(password);
            if (user == null || !hashedPassword.equals(user.getPassword())) {
                return "Invalid username or password";
            }
            currentUser = username;
            sessions.put(username, username);
            return "Login successful. Welcome " + username + "!";
        }

        private String logout() {
            if (!isLoggedIn()) {
                return "Not logged in";
            }
            String username = currentUser;
            currentUser = null;
            currentBoardId = null;
            sessions.remove(username);
            return "Logged out successfully. Goodbye " + username + "!";
        }

        private String createBoard(String boardName) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = new Board(String.valueOf(boardIdCounter++), boardName, currentUser);
            boards.put(board.getId(), board);
            saveData();
            return "Board created with ID: " + board.getId();
        }

        private String listBoards() {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            StringBuilder sb = new StringBuilder("Your boards: ");
            boolean hasBoards = false;

            for (Board b : boards.values()) {
                if (b.getOwner().equals(currentUser) || b.getMembers().contains(currentUser)) {
                    if (hasBoards) sb.append(" | ");

                    String addedBy = b.getMemberAddedBy().get(currentUser);
                    String prefix = "";
                    if (addedBy != null && !"self".equals(addedBy) && !b.getOwner().equals(currentUser)) {
                        prefix = "(" + addedBy + ") ";
                    }

                    sb.append("ID: ").append(b.getId())
                            .append(" - Name: ").append(prefix).append(b.getName())
                            .append(" - Owner: ").append(b.getOwner())
                            .append(" - Members: ").append(b.getMembers().size());
                    hasBoards = true;
                }
            }

            if (!hasBoards) {
                sb.append("No boards found");
            }
            return sb.toString();
        }

        private String addUserToBoard(String boardId, String username) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(boardId);
            if (board == null) return "Board not found";
            if (!board.getOwner().equals(currentUser)) {
                return "Only board owner can add members";
            }
            if (!users.containsKey(username)) return "User not found";

            if (board.getMembers().contains(username)) {
                board.addMember(username, currentUser); // حفظ روند
                saveData();
                return "User " + username + " added to board " + boardId;
            }

            board.addMember(username, currentUser);
            saveData();
            return "User " + username + " added to board " + boardId;
        }

        private String viewBoard(String boardId) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(boardId);
            if (board == null) return "Board not found";
            if (!board.getOwner().equals(currentUser) && !board.getMembers().contains(currentUser)) {
                return "Access denied. You are not a member of this board";
            }
            currentBoardId = boardId;
            return "Entered board view mode for: " + board.getName() +
                    "\nBoard commands: add_task, list_tasks, update_task_status, delete_task, exit_board";
        }

        private String exitBoard() {
            if (currentBoardId == null) {
                return "You are not currently viewing any board";
            }
            String boardName = boards.get(currentBoardId).getName();
            currentBoardId = null;
            return "Exited board view mode for: " + boardName;
        }

        private String addTask(String title, String description) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(currentBoardId);
            if (board == null) return "Board not found";

            Task task = new Task(String.valueOf(taskIdCounter++), title, description, currentUser);
            board.addTask(task);
            saveData();
            return "Task added with ID: " + task.getId();
        }

        private String listTasks() {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(currentBoardId);
            if (board == null) return "Board not found";
            return board.listTasks();
        }

        private String updateTaskStatus(String taskId, String newStatus) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(currentBoardId);
            if (board == null) return "Board not found";
            return board.updateTaskStatus(taskId, newStatus);
        }

        private String deleteTask(String taskId) {
            if (!isLoggedIn()) {
                return "Please login first";
            }
            Board board = boards.get(currentBoardId);
            if (board == null) return "Board not found";
            return board.deleteTask(taskId);
        }

        // ------------------------- کمکی‌ها -------------------------
        private boolean isLoggedIn() {
            return currentUser != null;
        }

        private boolean hasAll(JsonObject obj, String... keys) {
            for (String k : keys) {
                if (!obj.has(k) || obj.get(k).isJsonNull()) return false;
            }
            return true;
        }

        private String safeGetAsString(JsonObject obj, String key) {
            try {
                return obj.get(key).getAsString();
            } catch (Exception e) {
                return null;
            }
        }

        private String wrapError(String message) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "error");
            error.addProperty("message", message);
            error.add("data", new JsonObject());
            return gson.toJson(error);
        }

        private String hashPassword(String password) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(password.getBytes());
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    String h = Integer.toHexString(b & 0xff);
                    if (h.length() == 1) hex.append('0');
                    hex.append(h);
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Error hashing password: " + e.getMessage());
                return password;
            }
        }
    }

    // ------------------------- Persistence -------------------------
    private static void saveData() {
        try {
            try (FileWriter writer = new FileWriter("users.json")) {
                gson.toJson(users, writer);
            }
            try (FileWriter writer = new FileWriter("boards.json")) {
                gson.toJson(boards, writer);
            }
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
        }
    }

    private static void loadData() {
        try {
            File usersFile = new File("users.json");
            if (usersFile.exists()) {
                try (FileReader reader = new FileReader(usersFile)) {
                    Type userMapType = new TypeToken<Map<String, User>>() {}.getType();
                    Map<String, User> loadedUsers = gson.fromJson(reader, userMapType);
                    if (loadedUsers != null) users.putAll(loadedUsers);
                }
            }

            File boardsFile = new File("boards.json");
            if (boardsFile.exists()) {
                try (FileReader reader = new FileReader(boardsFile)) {
                    Type boardMapType = new TypeToken<Map<String, Board>>() {}.getType();
                    Map<String, Board> loadedBoards = gson.fromJson(reader, boardMapType);
                    if (loadedBoards != null) {
                        boards.putAll(loadedBoards);

                        int highestBoardId = 0;
                        int highestTaskId = 0;

                        for (Board board : loadedBoards.values()) {
                            try {
                                int bid = Integer.parseInt(board.getId());
                                highestBoardId = Math.max(highestBoardId, bid);
                            } catch (NumberFormatException ignored) {}

                            for (Task t : board.getTasks().values()) {
                                try {
                                    int tid = Integer.parseInt(t.getId());
                                    highestTaskId = Math.max(highestTaskId, tid);
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                        boardIdCounter = highestBoardId + 1;
                        taskIdCounter  = highestTaskId + 1;
                    }
                }
            }

            System.out.println("Data loaded. Users: " + users.size() + ", Boards: " + boards.size());
        } catch (IOException e) {
            System.err.println("Error loading data: " + e.getMessage());
        }
    }

    // ------------------------- Models -------------------------
    static class User {
        private String username;
        private String password;

        // برای Gson لازم است سازندهٔ پیش‌فرض داشته باشیم (در صورت نیاز)
        public User() {}

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
    }

    static class Board {
        private String id;
        private String name;
        private String owner;
        private Set<String> members;
        private Map<String, String> memberAddedBy;
        private Map<String, Task> tasks;

        public Board() {}

        public Board(String id, String name, String owner) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.members = new HashSet<>();
            this.memberAddedBy = new HashMap<>();
            this.members.add(owner);
            this.memberAddedBy.put(owner, "self");
            this.tasks = new HashMap<>();
        }

        public void addMember(String username, String addedBy) {
            members.add(username);
            memberAddedBy.put(username, addedBy);
        }

        public void addTask(Task task) {
            tasks.put(task.getId(), task);
        }

        public String listTasks() {
            if (tasks.isEmpty()) {
                return "No tasks in this board";
            }
            StringBuilder sb = new StringBuilder("Tasks in " + name + ": ");
            List<Task> taskList = new ArrayList<>(tasks.values());
            // همان منطق مرتب‌سازی قبلی بر اساس رشته‌ی createdAt
            taskList.sort(Comparator.comparing(Task::getCreatedAt));

            for (int i = 0; i < taskList.size(); i++) {
                if (i > 0) sb.append(" | ");
                Task t = taskList.get(i);
                sb.append("ID: ").append(t.getId())
                        .append(" - Title: ").append(t.getTitle())
                        .append(" - Status: ").append(t.getStatus())
                        .append(" - Created by: ").append(t.getCreatedBy())
                        .append(" - Created: ").append(t.getCreatedAt());
            }
            return sb.toString();
        }

        public String updateTaskStatus(String taskId, String newStatus) {
            Task t = tasks.get(taskId);
            if (t == null) return "Task not found";

            // پذیرش case-insensitive ولی خروجی مطابق همان مقادیر
            String normalized = normalizeStatus(newStatus);
            if (normalized == null) {
                return "Invalid status. Valid statuses: todo, inProgress, done";
            }
            t.setStatus(normalized);
            saveData();
            return "Task " + taskId + " status updated to " + normalized;
        }

        public String deleteTask(String taskId) {
            Task removed = tasks.remove(taskId);
            if (removed == null) return "Task not found";
            saveData();
            return "Task " + taskId + " deleted successfully";
        }

        private String normalizeStatus(String s) {
            if (s == null) return null;
            String v = s.trim();
            if (v.equals("todo") || v.equals("inProgress") || v.equals("done")) return v;
            // case-insensitive mapping بدون تغییر در منطق اصلی
            if (v.equalsIgnoreCase("todo")) return "todo";
            if (v.equalsIgnoreCase("inprogress")) return "inProgress";
            if (v.equalsIgnoreCase("done")) return "done";
            return null;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getOwner() { return owner; }
        public Set<String> getMembers() { return members; }
        public Map<String, String> getMemberAddedBy() { return memberAddedBy; }
        public Map<String, Task> getTasks() { return tasks; }
    }

    static class Task {
        private String id;
        private String title;
        private String description;
        private String status;
        private String createdBy;
        private String createdAt;

        public Task() {}

        public Task(String id, String title, String description, String createdBy) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.status = "todo";
            this.createdBy = createdBy;
            this.createdAt = new Date().toString();
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public String getCreatedBy() { return createdBy; }
        public String getCreatedAt() { return createdAt; }
        public void setStatus(String status) { this.status = status; }
    }
}
