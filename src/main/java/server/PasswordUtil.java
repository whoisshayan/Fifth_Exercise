package server;


import java.security.MessageDigest;
import java.security.SecureRandom;


final class PasswordUtil {
    private static final SecureRandom RNG = new SecureRandom();


    static byte[] randomSalt(){
        byte[] b = new byte[16];
        RNG.nextBytes(b); return b;
    }
    static String toHex(byte[] b){
        StringBuilder sb = new StringBuilder();
        for(byte x: b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    static String sha256Hex(byte[] salt, String password){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}