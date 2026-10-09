package il.co.kiosk;

import java.net.URI;
import java.util.Locale;

public final class UrlPolicy {
    public static boolean valid(String value) {
        if (value == null || value.length() > 4096) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && uri.getRawUserInfo() == null && uri.getPort() >= -1 && uri.getPort() <= 65535;
        } catch (Exception e) { return false; }
    }
    public static boolean allowed(String home, String candidate, boolean sameHost) {
        if (!valid(home) || !valid(candidate)) return false;
        return !sameHost || URI.create(home).getHost().toLowerCase(Locale.ROOT)
            .equals(URI.create(candidate).getHost().toLowerCase(Locale.ROOT));
    }
}
