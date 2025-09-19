package org.example;


import java.util.concurrent.atomic.AtomicLong;


public final class Ids {
    private static final AtomicLong USER = new AtomicLong(1000);
    private static final AtomicLong BOARD = new AtomicLong(2000);
    private static final AtomicLong TASK = new AtomicLong(3000);


    public static long nextUserId() { return USER.getAndIncrement(); }
    public static long nextBoardId() { return BOARD.getAndIncrement(); }
    public static long nextTaskId() { return TASK.getAndIncrement(); }
}