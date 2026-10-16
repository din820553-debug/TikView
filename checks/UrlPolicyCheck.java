package dev.tikview;

public final class UrlPolicyCheck {
    public static void main(String[] args) {
        String[] allowed = {"https://www.tiktok.com/", "https://vm.tiktok.com/abc/", "https://tiktok.com:443/@x/video/123", "https://WWW.TIKTOK.COM/"};
        String[] denied = {"http://tiktok.com/", "https://tiktok.com.evil.test/", "https://eviltiktok.com/", "javascript:alert(1)", "file:///etc/passwd", "https://tiktok.com@evil.test", "https://evil.test@tiktok.com", "https://tiktok.com:8443/", "https://www.tiktok.com\\@evil.test/", "https://%74iktok.com/"};
        for (String value : allowed) require(UrlPolicy.isTikTok(value), value);
        for (String value : denied) require(!UrlPolicy.isTikTok(value), value);
        for (String value : new String[]{"http://proxy.example:8080", "https://proxy.example:443", "socks://127.0.0.1:1080", "http://[::1]:8080"}) require(UrlPolicy.validProxy(value), value);
        for (String value : new String[]{"http://host", "http://host:0", "http://host:65536", "http://user:secret@host:80", "http://host:80/path", "http://host:80?x", "http://host:80#x", "ftp://host:21", "host:8080"}) require(!UrlPolicy.validProxy(value), value);
        System.out.println("URL and proxy validation: 27 cases passed");
    }
    private static void require(boolean result, String value) {
        if (!result) throw new AssertionError(value);
    }
}
