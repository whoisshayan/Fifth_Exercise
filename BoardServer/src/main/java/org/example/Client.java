package org.example;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.google.gson.*;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    private static final int MAX_DRAIN_LINES = 5;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner;
    private boolean connected = false;
    private final Gson gson = new Gson();

    public void start() {
        scanner = new Scanner(System.in);

        try {
            System.out.println("Connecting to server...");
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            socket.setSoTimeout(10_000);

            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;

            String welcomeJson = in.readLine();
            if (welcomeJson != null) {
                JsonObject welcomeResponse = gson.fromJson(welcomeJson, JsonObject.class);
                JsonElement msg = welcomeResponse.get("message");
                if (msg != null && !msg.isJsonNull()) {
                    System.out.println(msg.getAsString());
                }
                JsonObject data = welcomeResponse.getAsJsonObject("data");
                if (data != null && data.has("available_commands")) {
                    System.out.println("Available commands: " + data.get("available_commands").getAsString());
                }
            }

            runCommandLoop();

        } catch (SocketTimeoutException e) {
            System.err.println("Timed out waiting for server. " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void runCommandLoop() {
        System.out.println("\nTask Management Board Client");
        System.out.println("Type 'quit' to exit");

        while (connected) {
            System.out.print("\n> ");
            String rawInput = scanner.nextLine().trim();
            if (rawInput.isEmpty()) continue;

            if (equalsAnyIgnoreCase(rawInput, "quit", "exit")) {
                break;
            }

            drainInputLimited();

            String jsonRequest = createJsonRequest(rawInput);
            if (jsonRequest == null) {
                continue;
            }

            JsonObject response = sendAndReceive(jsonRequest);
            if (response == null) continue;

            String message = safeGetAsString(response, "message");
            if (message != null) {
                System.out.println(message);
                if (containsIgnoreCase(message, "Entered board view mode")) {
                    runBoardViewMode();
                }
            }
        }
    }

    private void runBoardViewMode() {
        System.out.println("\nBoard View Mode");
        System.out.println("Board commands: add_task, list_tasks, update_task_status, delete_task, exit_board");

        while (connected) {
            System.out.print("\n[Board] > ");
            String rawInput = scanner.nextLine().trim();
            if (rawInput.isEmpty()) continue;

            drainInputLimited();

            String jsonRequest = createJsonRequest(rawInput);
            if (jsonRequest == null) {
                continue;
            }

            JsonObject response = sendAndReceive(jsonRequest);
            if (response == null) continue;

            String message = safeGetAsString(response, "message");
            if (message != null) {
                System.out.println(message);
                if (containsIgnoreCase(message, "Exited board view mode")) {
                    break;
                }
            }
        }
    }


    private String createJsonRequest(String input) {
        List<String> parts = tokenize(input);
        if (parts.isEmpty()) return null;

        String command = parts.get(0).toLowerCase();
        JsonObject request = new JsonObject();
        request.addProperty("command", command);

        JsonObject payload = new JsonObject();

        switch (command) {
            case "register":
            case "login": {
                if (parts.size() < 3) {
                    System.out.println("Usage: " + command + " <username> <password>");
                    return null;
                }
                payload.addProperty("username", parts.get(1));
                payload.addProperty("password", parts.get(2));
                break;
            }
            case "create_board": {
                if (parts.size() < 2) {
                    System.out.println("Usage: create_board <boardName>");
                    return null;
                }
                payload.addProperty("boardName", parts.get(1));
                break;
            }
            case "add_user_to_board": {
                if (parts.size() < 3) {
                    System.out.println("Usage: add_user_to_board <boardId> <username>");
                    return null;
                }
                payload.addProperty("boardId", parts.get(1));
                payload.addProperty("username", parts.get(2));
                break;
            }
            case "view_board": {
                if (parts.size() < 2) {
                    System.out.println("Usage: view_board <boardId>");
                    return null;
                }
                payload.addProperty("boardId", parts.get(1));
                break;
            }
            case "add_task": {
                if (parts.size() < 3) {
                    System.out.println("Usage: add_task <title> <description>");
                    return null;
                }
                payload.addProperty("title", parts.get(1));
                payload.addProperty("description", parts.get(2));
                break;
            }
            case "update_task_status": {
                if (parts.size() < 3) {
                    System.out.println("Usage: update_task_status <taskId> <status>");
                    return null;
                }
                payload.addProperty("taskId", parts.get(1));
                payload.addProperty("status", parts.get(2));
                break;
            }
            case "delete_task": {
                if (parts.size() < 2) {
                    System.out.println("Usage: delete_task <taskId>");
                    return null;
                }
                payload.addProperty("taskId", parts.get(1));
                break;
            }
            // دستورات بدون payload یا سایر موارد
            default:
                // برای سازگاری با سرور، حتی اگر payload خالی باشد، اضافه می‌شود
                break;
        }

        request.add("payload", payload);
        return gson.toJson(request);
    }


    private JsonObject sendAndReceive(String jsonRequest) {
        try {
            out.println(jsonRequest);
            out.flush();

            try {
                // تاخیر کوتاه مشابه نسخه اصلی برای هم‌زمانی ساده‌ی I/O
                Thread.sleep(30);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            String responseJson = in.readLine();
            if (responseJson == null) {
                System.err.println("Server closed the connection.");
                connected = false;
                return null;
            }
            return gson.fromJson(responseJson, JsonObject.class);
        } catch (SocketTimeoutException e) {
            System.err.println("Timed out waiting for server response.");
        } catch (IOException e) {
            System.err.println("Error communicating with server: " + e.getMessage());
            connected = false;
        } catch (JsonSyntaxException je) {
            System.err.println("Invalid JSON from server.");
        }
        return null;
    }


    private void drainInputLimited() {
        try {
            int drained = 0;
            while (in.ready() && drained < MAX_DRAIN_LINES) {
                in.readLine();
                drained++;
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * توکنایزر با پشتیبانی از کوتیشن‌های دوتایی.
     * مثال:
     * add_task "Fix login" "handle empty token"
     * → ["add_task","Fix login","handle empty token"]
     */
    private List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    private String safeGetAsString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (ClassCastException | IllegalStateException e) {
            return null;
        }
    }

    private boolean equalsAnyIgnoreCase(String s, String... options) {
        for (String opt : options) {
            if (s.equalsIgnoreCase(opt)) return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private void closeConnection() {
        connected = false;
        try {
            if (in != null) in.close();
        } catch (IOException e) {
            System.err.println("Error closing input stream: " + e.getMessage());
        }
        if (out != null) out.close();
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
        if (scanner != null) scanner.close();
        System.out.println("Disconnected from server. Goodbye!");
    }

    public static void main(String[] args) {
        new Client().start();
    }
}
