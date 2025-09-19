package server;

import org.example.Models;

class UserService {
    private final DataStore ds;
    UserService(DataStore ds){ this.ds = ds; }

    // خروجی حتماً از جنس Models.User باشد
    synchronized Models.User findByUsername(String username){
        for (Models.User u : ds.users.values()) {
            if (u.username.equals(username)) return u;
        }
        return null;
    }
}
