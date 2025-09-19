package org.example;


import java.time.Instant;
import java.util.*;


public class Models {
    public enum Priority { LOW, MEDIUM, HIGH }
    public enum Status { TODO, IN_PROGRESS, DONE }


    public static class User {
        public long id;
        public String username;
        public String saltHex; // per‑user salt (hex)
        public String passHashHex; // SHA‑256(salt + password) as hex
        public Instant createdAt;
        public Set<Long> boardIds = new HashSet<>();
    }


    public static class Board {
        public long id;
        public String name;
        public long ownerId;
        public Set<Long> memberIds = new HashSet<>();
        public Instant createdAt;
    }


    public static class Task {
        public long id;
        public long boardId;
        public String title;
        public String description;
        public Priority priority;
        public Status status;
        public Instant createdAt;
        public Instant dueDate; // nullable
    }
}