package com.zerium.gecko;

/**
 * URL classification shared by the omnibox, history recording and
 * suggestions. Pure JVM (no android.* imports) so it is unit-testable in
 * the local ecj/JUnit chain.
 *
 * <p>Zerium distinguishes <em>internal pages</em> (generated start page,
 * {@code about:blank}, {@code data:} error/reader pages) from real
 * navigated URLs. Internal pages must never reach the address bar as if
 * they were websites, and must never enter browsing history.</p>
 */
public final class Urls {

    private Urls() {}

    /**
     * True for URLs that identify a generated/internal page rather than a
     * navigated website: empty, {@code about:*} (home, blank), {@code data:}
     * and {@code javascript:}. The start page is {@code about:home}, so one
     * {@code about:} check covers it and {@code about:blank}.
     */
    public static boolean isInternalPage(String url) {
        if (url == null) return true;
        String u = url.trim();
        if (u.isEmpty()) return true;
        String lower = u.toLowerCase();
        return lower.startsWith("about:") || lower.startsWith("data:")
                || lower.startsWith("javascript:");
    }

    /**
     * True for real navigated HTTP/HTTPS pages: the only kind of URL that
     * belongs in browsing history and in remote suggestions. The host must
     * be present, so degenerate values never get recorded.
     */
    public static boolean isWebUrl(String url) {
        if (isInternalPage(url)) return false;
        String u = url.trim();
        int colon = u.indexOf(':');
        if (colon <= 0) return false;
        String scheme = u.substring(0, colon).toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        String rest = u.substring(colon + 1);
        if (rest.startsWith("//")) rest = rest.substring(2);
        // Cut the authority at the first path/query/fragment separator
        // (colons inside IPv6 literals are handled below).
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char ch = rest.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') { end = i; break; }
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            return close > 1;
        }
        int portColon = authority.indexOf(':');
        if (portColon >= 0) authority = authority.substring(0, portColon);
        return !authority.isEmpty();
    }

    /**
     * Scheme stripped for display (the security icon carries TLS state);
     * internal pages display as empty string so {@code about:blank} can
     * never pose as a website URL.
     */
    public static String displayUrl(String url) {
        if (url == null) return "";
        if (isInternalPage(url)) return "";
        return url.replaceFirst("^https?://", "");
    }
}
