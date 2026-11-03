package com.zerium.gecko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure-JVM omnibox suggestion core: per-engine suggest endpoints, response
 * parsing across provider shapes, merge/dedup/cap and the direct-URL row.
 */
public class SuggestEngineTest {

    // ---------- Endpoints ----------

    @Test
    public void remoteUrlCoversBuiltInEnginesAndEncodesQuery() {
        assertEquals("https://suggestqueries.google.com/complete/search?client=firefox&q=hello+world",
                SuggestEngine.remoteUrl(new Utils.Engine("Google", "https://www.google.com/search?q=%s", false), "hello world"));
        assertEquals("https://duckduckgo.com/ac/?q=abc&type=list",
                SuggestEngine.remoteUrl(new Utils.Engine("DuckDuckGo", "https://duckduckgo.com/?q=%s", false), "abc"));
        assertEquals("https://search.brave.com/api/suggest?q=abc",
                SuggestEngine.remoteUrl(new Utils.Engine("Brave Search", "https://search.brave.com/search?q=%s", false), "abc"));
        assertEquals("https://api.bing.com/osjson.aspx?query=abc",
                SuggestEngine.remoteUrl(new Utils.Engine("Bing", "https://www.bing.com/search?q=%s", false), "abc"));
        assertTrue(SuggestEngine.remoteUrl(
                new Utils.Engine("Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s", false),
                "abc").startsWith("https://en.wikipedia.org/w/api.php?action=opensearch"));
    }

    @Test
    public void customEnginesHaveNoRemoteEndpoint() {
        assertNull(SuggestEngine.remoteUrl(
                new Utils.Engine("Mine", "https://mine.example/?q=%s", true), "abc"));
        assertNull(SuggestEngine.remoteUrl(null, "abc"));
        assertNull(SuggestEngine.remoteUrl(
                new Utils.Engine("Google", "https://www.google.com/search?q=%s", false), "  "));
    }

    // ---------- Response parsing ----------

    @Test
    public void parsesFirefoxOpenSearchShape() {
        // Google (client=firefox), Bing osjson, DuckDuckGo type=list
        List<String> out = SuggestEngine.parseResponse("[\"hel\",[\"hello world\",\"hello again\"]]");
        assertEquals(Arrays.asList("hello world", "hello again"), out);
    }

    @Test
    public void parsesBareStringArray() {
        assertEquals(Arrays.asList("one", "two"),
                SuggestEngine.parseResponse("[\"one\",\"two\"]"));
    }

    @Test
    public void parsesBraveResultsObject() {
        List<String> out = SuggestEngine.parseResponse(
                "{\"results\":[{\"text\":\"brave one\"},{\"text\":\"brave two\"}]}");
        assertEquals(Arrays.asList("brave one", "brave two"), out);
    }

    @Test
    public void parsesSuggestionsArrayObject() {
        List<String> out = SuggestEngine.parseResponse(
                "{\"suggestions\":[\"alpha\",\"beta\"]}");
        assertEquals(Arrays.asList("alpha", "beta"), out);
    }

    @Test
    public void brokenBodiesNeverThrow() {
        assertTrue(SuggestEngine.parseResponse(null).isEmpty());
        assertTrue(SuggestEngine.parseResponse("").isEmpty());
        assertTrue(SuggestEngine.parseResponse("not json at all").isEmpty());
        assertTrue(SuggestEngine.parseResponse("{\"x\":1}").isEmpty());
    }

    // ---------- Merge ----------

    private static List<String[]> hist(String title, String url) {
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{title, url});
        return l;
    }

    @Test
    public void mergeOrdersCurrentPageSearchHistoryRemote() {
        List<SuggestEngine.Suggestion> rows = SuggestEngine.merge(
                "Example Site", "https://current.example/page", "exa",
                hist("Example History", "https://history.example/exa"),
                Arrays.asList("exa remote", "example extra"),
                "Google");
        // "exa" is not host-like → no URL row.
        assertEquals(SuggestEngine.TYPE_CURRENT, rows.get(0).type);
        assertEquals("https://current.example/page", rows.get(0).payload);
        assertEquals(SuggestEngine.TYPE_SEARCH, rows.get(1).type);
        assertEquals("exa", rows.get(1).payload);
        assertEquals(SuggestEngine.TYPE_HISTORY, rows.get(2).type);
        assertEquals("https://history.example/exa", rows.get(2).payload);
        assertEquals(SuggestEngine.TYPE_SEARCH, rows.get(3).type);
        assertEquals("exa remote", rows.get(3).payload);
        assertEquals(SuggestEngine.TYPE_SEARCH, rows.get(4).type);
        assertEquals("example extra", rows.get(4).payload);
    }

    @Test
    public void mergeSearchRowUsesEngineName() {
        List<SuggestEngine.Suggestion> rows = SuggestEngine.merge(
                null, null, "news today", new ArrayList<>(),
                new ArrayList<>(), "DuckDuckGo");
        assertEquals(1, rows.size());
        assertEquals(SuggestEngine.TYPE_SEARCH, rows.get(0).type);
        assertTrue(rows.get(0).title.startsWith("DuckDuckGo:"));
        assertEquals("news today", rows.get(0).payload);
    }

    @Test
    public void mergeDeduplicatesCaseInsensitively() {
        List<SuggestEngine.Suggestion> rows = SuggestEngine.merge(
                null, null, "example",
                hist("Example History", "https://example.com"),
                Arrays.asList("example"), "Google");
        // URL row (example → https://example.com) + search row; remote
        // "example" dedupes against the search row.
        boolean hasHistory = false;
        for (SuggestEngine.Suggestion s : rows) {
            if (s.type == SuggestEngine.TYPE_HISTORY
                    && "https://example.com".equals(s.payload)) hasHistory = true;
        }
        assertTrue(hasHistory);
        int searchRows = 0;
        for (SuggestEngine.Suggestion s : rows) {
            if (s.type == SuggestEngine.TYPE_SEARCH
                    && "example".equals(s.payload)) searchRows++;
        }
        assertEquals(1, searchRows);
    }

    @Test
    public void mergeCapsAtEight() {
        List<String[]> local = new ArrayList<>();
        for (int i = 0; i < 20; i++) local.add(new String[]{"t" + i, "https://h" + i + ".example"});
        List<String> remote = new ArrayList<>();
        for (int i = 0; i < 20; i++) remote.add("remote " + i);
        List<SuggestEngine.Suggestion> rows = SuggestEngine.merge(
                null, null, "q", local, remote, "Google");
        assertEquals(SuggestEngine.MAX_SUGGESTIONS, rows.size());
    }

    @Test
    public void directUrlMirrorsSmartUrlHostRules() {
        assertEquals("https://example.com", SuggestEngine.directUrl("example.com"));
        assertEquals("https://localhost", SuggestEngine.directUrl("localhost"));
        assertEquals("http://x.example", SuggestEngine.directUrl("http://x.example"));
        assertNull(SuggestEngine.directUrl("two words"));
        assertNull(SuggestEngine.directUrl("nodot"));
        assertNull(SuggestEngine.directUrl("trailing."));
        assertNull(SuggestEngine.directUrl("about:blank"));
    }

    @Test
    public void directUrlRowAppearsForHostLikeInput() {
        List<SuggestEngine.Suggestion> rows = SuggestEngine.merge(
                null, null, "example.com", new ArrayList<>(),
                new ArrayList<>(), "Google");
        assertEquals(SuggestEngine.TYPE_URL, rows.get(0).type);
        assertEquals("https://example.com", rows.get(0).payload);
    }
}
