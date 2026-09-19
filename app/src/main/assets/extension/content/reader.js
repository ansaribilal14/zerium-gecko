/* Zerium G Shield — reader-view extraction (content script).
 *
 * Runs at document_idle on http(s) pages. Uses Mozilla Readability
 * (Apache-2.0, loaded before this file in the same content-script scope)
 * on a CLONE of the document — parse() mutates the tree, so the visible
 * page is never touched.
 *
 * The parsed article is posted to the app through runtime messaging.
 * The app registers a per-session MessageDelegate
 * (GeckoSession.getWebExtensionController().setMessageDelegate), so the
 * MessageSender.session identifies the tab — no background relay and no
 * tab-id mapping is involved.
 *
 * The app caches the latest article per session and renders it when the
 * user opens Reader view. Honest scope: the parse is a snapshot of the
 * current DOM at document_idle — paywalled or JS-gated content yields
 * what is actually in the DOM, exactly like the WebView edition.
 */
(function () {
  try {
    var url = String(location.href || '');
    if (!/^https?:/i.test(url)) return;
    if (url.indexOf('.translate.goog') !== -1) return;

    var cloned = document.cloneNode(true);
    var reader = new Readability(cloned);

    var article = null;
    try {
      if (reader.isProbablyReaderable()) article = reader.parse();
    } catch (e) { article = null; }

    var payload = {
      type: 'reader',
      url: url,
      readerable: !!(article && article.content),
      title: (article && article.title) || document.title || '',
      byline: (article && article.byline) || '',
      siteName: (article && article.siteName) || '',
      content: (article && article.content) || '',
      length: (article && article.textContent) ? article.textContent.length : 0
    };

    try {
      var p = browser.runtime.sendMessage(payload);
      if (p && typeof p.catch === 'function') p.catch(function () {});
    } catch (e) { /* receiver not ready */ }
  } catch (e) { /* never break the page */ }
})();
