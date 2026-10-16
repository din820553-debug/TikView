package dev.tikview;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class UrlPolicy {
    static boolean isTikTok(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                && (host.equalsIgnoreCase("tiktok.com") || host.toLowerCase(Locale.ROOT).endsWith(".tiktok.com"))
                && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (URISyntaxException | NullPointerException e) { return false; }
    }
    static boolean validProxy(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme) || "socks".equals(scheme))
                && uri.getHost() != null && uri.getPort() > 0 && uri.getPort() <= 65535
                && uri.getRawUserInfo() == null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getQuery() == null && uri.getFragment() == null;
        } catch (URISyntaxException | NullPointerException e) { return false; }
    }
}
