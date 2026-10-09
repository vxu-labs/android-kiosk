package il.co.kiosk;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Independent of Android so credential behavior can be checked on the JVM. */
public final class PinCrypto {
    private static final int ITERATIONS = 120000;
    public static boolean valid(String pin) { return pin != null && pin.matches("[0-9]{6,12}"); }
    public static String create(String pin) {
        if (!valid(pin)) throw new IllegalArgumentException("PIN must contain 6–12 digits");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + derive(pin, salt, ITERATIONS);
    }
    public static boolean matches(String pin, String saved) {
        if (!valid(pin) || saved == null) return false;
        try {
            String[] parts = saved.split(":");
            if (parts.length != 3 || Integer.parseInt(parts[0]) != ITERATIONS) return false;
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            if (salt.length != 16) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]),
                Base64.getDecoder().decode(derive(pin, salt, ITERATIONS)));
        } catch (IllegalArgumentException e) { return false; }
    }
    private static String derive(String pin, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, 256);
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }
}
