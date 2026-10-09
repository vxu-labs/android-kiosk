package il.co.kiosk;

public final class CoreTests {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        check(!PinCrypto.valid("12345"), "short PIN");
        check(!PinCrypto.valid("1234567890123"), "long PIN");
        check(!PinCrypto.valid("abcdef"), "non-numeric PIN");
        check(!PinCrypto.valid("１２３４５６"), "unicode digits");
        check(PinCrypto.valid("012345"), "leading zero");
        String hash = PinCrypto.create("012345");
        check(PinCrypto.matches("012345", hash), "correct PIN");
        check(!PinCrypto.matches("123456", hash), "wrong PIN");
        check(!hash.equals(PinCrypto.create("012345")), "random salt");
        check(!PinCrypto.matches("012345", "corrupt"), "corrupt hash");
        check(!PinCrypto.matches("012345", "120000:eA==:eA=="), "short salt");
        check(!PinCrypto.matches("012345", null), "missing hash");
        check(!PinCrypto.matches(null, hash), "missing PIN");
        check(!PinCrypto.matches("012345", hash.replace("120000:", "1:")), "iteration tampering");
        check(AttemptPolicy.delayMillis(4) == 0, "initial attempts");
        check(AttemptPolicy.delayMillis(5) == 30000, "fifth attempt locks");
        check(AttemptPolicy.delayMillis(8) == 60000, "escalating delay");
        check(AttemptPolicy.delayMillis(1000) == 900000, "delay cap");
        check(UrlPolicy.valid("https://example.com/a?q=1#b"), "HTTPS URL");
        check(!UrlPolicy.valid("http://example.com"), "cleartext rejected");
        for (String value : new String[]{"javascript:alert(1)", "intent://test", "file:///sdcard/test", "content://provider", "data:text/html,hi", "https://", "https://user:pass@example.com", "https://example.com:99999", "https://example.com/with space"}) check(!UrlPolicy.valid(value), "unsafe URL: " + value);
        check(UrlPolicy.allowed("https://example.com", "https://EXAMPLE.com/path", true), "same host");
        check(!UrlPolicy.allowed("https://example.com", "https://example.com.evil.test", true), "suffix attack");
        check(!UrlPolicy.allowed("https://example.com", "https://evil.test/?next=example.com", true), "query attack");
        check(!UrlPolicy.allowed("https://example.com", "https://login.example.com", true), "subdomain requires opt in");
        check(UrlPolicy.allowed("https://example.com", "https://login.example.com", false), "opt-in other HTTPS host");
        check(!UrlPolicy.allowed("https://example.com", "intent://example.com", false), "external app always denied");
        System.out.println("PASS: " + checks + " core security checks");
    }
}
