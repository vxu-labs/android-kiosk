package il.co.kiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

final class Config {
    final SharedPreferences prefs;
    Config(Context c) { prefs = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE); }
    boolean hasPin() { return prefs.contains("pin"); }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    String text(String key, String fallback) { return prefs.getString(key, fallback); }
    boolean flag(String key, boolean fallback) { return prefs.getBoolean(key, fallback); }
    void savePin(String pin) {
        if (!prefs.edit().putString("pin", PinCrypto.create(pin)).putInt("failures", 0)
            .putLong("lock_wall", 0).putLong("lock_elapsed", 0).commit()) throw new IllegalStateException("Storage failure");
    }
    long waitMillis() {
        long wall = prefs.getLong("lock_wall", 0) - System.currentTimeMillis();
        long elapsed = prefs.getLong("lock_elapsed", 0) - SystemClock.elapsedRealtime();
        // Wall clock survives process death/reboot; monotonic clock resists a changed clock within this boot.
        long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean sameBoot = Math.abs(boot - prefs.getLong("boot_epoch", 0)) < 10000;
        return Math.max(0, Math.min(900000, sameBoot ? Math.max(wall, elapsed) : wall));
    }
    boolean authenticate(String pin) {
        if (waitMillis() > 0) return false;
        if (PinCrypto.matches(pin, text("pin", null))) {
            prefs.edit().putInt("failures", 0).putLong("lock_wall", 0).putLong("lock_elapsed", 0).commit();
            return true;
        }
        int failures = Math.min(1000, prefs.getInt("failures", 0) + 1);
        long delay = AttemptPolicy.delayMillis(failures);
        prefs.edit().putInt("failures", failures).putLong("lock_wall", System.currentTimeMillis() + delay)
            .putLong("lock_elapsed", SystemClock.elapsedRealtime() + delay)
            .putLong("boot_epoch", System.currentTimeMillis() - SystemClock.elapsedRealtime()).commit();
        return false;
    }
}
