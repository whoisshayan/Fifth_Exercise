package server;


import java.time.Instant;
import java.util.*;


/** Simple HMAC‑less demo token store (server‑side). For production use JWT/HMAC. */
final class TokenService {
    private static final Map<String, Entry> TOKENS = new HashMap<>();
    private static final long TTL_SECONDS = 3600; // 1h
    private static final java.security.SecureRandom RNG = new java.security.SecureRandom();


    static class Entry { long userId; Instant exp; }


    static String issue(long userId){
        byte[] b = new byte[24]; RNG.nextBytes(b);
        String tok = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Entry e = new Entry(); e.userId = userId; e.exp = Instant.now().plusSeconds(TTL_SECONDS);
        TOKENS.put(tok, e); return tok;
    }
    static Long validate(String token){
        Entry e = TOKENS.get(token);
        if(e == null) return null;
        if(Instant.now().isAfter(e.exp)){ TOKENS.remove(token); return null; }
        return e.userId;
    }
}