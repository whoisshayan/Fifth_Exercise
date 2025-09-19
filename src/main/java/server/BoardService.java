package server;

import org.example.Models;

class BoardService {

    private final DataStore ds;

    BoardService(DataStore ds) {
        this.ds = ds;
    }

    boolean canView(long userId, long boardId) {
        Models.Board b = ds.boards.get(boardId);
        return b != null && (b.ownerId == userId || b.memberIds.contains(userId));
    }

    /** کاربر مالک برد است؟ */
    boolean isOwner(long userId, long boardId) {
        Models.Board b = ds.boards.get(boardId);
        return b != null && b.ownerId == userId;
    }
}