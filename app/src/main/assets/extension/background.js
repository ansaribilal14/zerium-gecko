/* Zerium G Shield — engine-level blocking (background script).
 *
 * Network layer: cancels requests to known ad/tracker hosts (hosts-file
 * semantics: an entry covers the domain and its subdomains) plus curated
 * URL substring patterns, using the GeckoView webRequest blocking API —
 * something the WebView edition cannot do at engine level.
 *
 * Cosmetic layer: element-hiding CSS is applied by the content script
 * declared in manifest.json (content/cosmetic.css).
 *
 * App link: the Android app connects via native messaging ("browser").
 * It can push {type:"config", enabled, allowlist} and receive
 * {type:"count", blocked} deltas.
 */

let enabled = true;
let allowlist = new Set();
let hosts = new Set();
let urlPatterns = [];
let blockedCount = 0;
let countSinceFlush = 0;
let ready = false;

function hostMatches(host) {
  if (!host) return false;
  const h = host.toLowerCase();
  if (allowlist.has(h)) return false;
  if (hosts.has(h)) return true;
  // hosts-file semantics: parent domains, e.g. a.example.com under example.com
  const parts = h.split(".");
  for (let i = 1; i < parts.length - 1; i++) {
    const parent = parts.slice(i).join(".");
    if (hosts.has(parent) && !allowlist.has(parent)) return true;
  }
  return false;
}

function shouldBlock(url) {
  if (!enabled) return false;
  try {
    const u = new URL(url);
    const scheme = u.protocol;
    if (scheme !== "http:" && scheme !== "https:") return false;
    if (hostMatches(u.hostname)) return true;
    for (let i = 0; i < urlPatterns.length; i++) {
      if (url.indexOf(urlPatterns[i]) !== -1) {
        const h = u.hostname.toLowerCase();
        if (allowlist.has(h)) return false;
        return true;
      }
    }
  } catch (e) { /* not a parseable URL */ }
  return false;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (shouldBlock(details.url)) {
      blockedCount++;
      countSinceFlush++;
      maybeFlushCount();
      return { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);

let flushTimer = null;
function maybeFlushCount() {
  if (countSinceFlush === 0) return;
  if (countSinceFlush >= 10) { flushCount(); return; }
  if (!flushTimer) {
    flushTimer = setTimeout(() => { flushTimer = null; flushCount(); }, 5000);
  }
}
function flushCount() {
  if (countSinceFlush === 0 || !port) return;
  try {
    port.postMessage({ type: "count", blocked: countSinceFlush });
  } catch (e) { /* port not ready */ }
  countSinceFlush = 0;
}

async function loadRules() {
  try {
    const res = await fetch(browser.runtime.getURL("blocklist/rules.json"));
    const data = await res.json();
    hosts = new Set(data.hosts || []);
    urlPatterns = data.urls || [];
    ready = true;
  } catch (e) {
    ready = false;
  }
}
loadRules();

/* ---- Native messaging with the Android app ---- */
let port = null;

try {
  port = browser.runtime.connectNative("browser");
  port.onMessage.addListener((msg) => {
    if (!msg || msg.type !== "config") return;
    if (typeof msg.enabled === "boolean") enabled = msg.enabled;
    if (Array.isArray(msg.allowlist)) {
      allowlist = new Set(msg.allowlist.map((h) => String(h).toLowerCase()));
    }
  });
  port.onDisconnect.addListener(() => { port = null; });
} catch (e) {
  port = null;
}
