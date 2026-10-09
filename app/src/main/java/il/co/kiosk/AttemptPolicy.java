package il.co.kiosk;

public final class AttemptPolicy {
    public static long delayMillis(int failures) {
        if (failures < 5) return 0;
        return Math.min(900000L, 30000L * (1L << Math.min(5, (failures - 5) / 3)));
    }
}
