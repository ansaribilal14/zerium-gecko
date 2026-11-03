package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Internal-page vs real-URL classification (address bar + history rules). */
public class UrlsTest {

    @Test
    public void internalPagesAreRecognised() {
        assertTrue(Urls.isInternalPage(null));
        assertTrue(Urls.isInternalPage(""));
        assertTrue(Urls.isInternalPage("   "));
        assertTrue(Urls.isInternalPage("about:blank"));
        assertTrue(Urls.isInternalPage("about:home"));
        assertTrue(Urls.isInternalPage("ABOUT:BLANK"));
        assertTrue(Urls.isInternalPage("data:text/html;base64,PGI+"));
        assertTrue(Urls.isInternalPage("javascript:void(0)"));
    }

    @Test
    public void realUrlsAreWebUrls() {
        assertTrue(Urls.isWebUrl("https://example.com"));
        assertTrue(Urls.isWebUrl("http://example.com/path?x=1#f"));
        assertTrue(Urls.isWebUrl("https://sub.domain.example/deep/path"));
        assertTrue(Urls.isWebUrl("http://localhost:8080/x"));
        assertTrue(Urls.isWebUrl("https://[::1]/index.html"));
    }

    @Test
    public void blankAndErrorPagesNeverCountAsWebUrls() {
        assertFalse(Urls.isWebUrl("about:blank"));
        assertFalse(Urls.isWebUrl("about:home"));
        assertFalse(Urls.isWebUrl("data:text/html,<h1>error</h1>"));
        assertFalse(Urls.isWebUrl(null));
        assertFalse(Urls.isWebUrl(""));
        assertFalse(Urls.isWebUrl("https://"));
        assertFalse(Urls.isWebUrl("https:///path-only"));
        assertFalse(Urls.isWebUrl("ftp://files.example.com"));
        assertFalse(Urls.isWebUrl("file:///sdcard/x.html"));
    }

    @Test
    public void displayStripsSchemeButNeverShowsInternalPages() {
        assertEquals("", Urls.displayUrl("about:blank"));
        assertEquals("", Urls.displayUrl("about:home"));
        assertEquals("", Urls.displayUrl(null));
        assertEquals("example.com/path", Urls.displayUrl("https://example.com/path"));
        assertEquals("example.com", Urls.displayUrl("http://example.com"));
    }
}
