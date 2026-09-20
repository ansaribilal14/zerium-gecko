/* Zerium G Shield v1.1.0 — engine-level blocking (background script).
 *
 * Network layer:
 *   - hosts-file blocking (StevenBlack unified hosts, MIT): an entry covers
 *     the domain and its subdomains.
 *   - Adblock-syntax network rules compiled by scripts/gen_gecko_rules.py:
 *       ||host^                 -> domains (host + subdomains)
 *       ||host/path^            -> filters (path prefix + options)
 *       @@...                   -> exceptions (matched before everything)
 *     with an honestly-supported option subset: script, image, stylesheet,
 *     xmlhttprequest, subdocument, object, media, font, websocket, ping,
 *     other, important, third-party, first-party, domain= (incl. ~negation).
 *     $popup, $csp, $redirect, $replace, regex filters, $generichide/
 *     $specifichide/$elemhide and domain-scoped element hiding are NOT
 *     implemented — see docs/BLOCKING.md for the full honest scope.
 *   - Curated URL substring patterns carried over from the WebView edition.
 *
 * YouTube layer (engine-level, stronger than any WebView-class blocker):
 *   Responses from the InnerTube player API endpoints are streamed through
 *   filterResponseData and pruned of ad structures BEFORE the page receives
 *   them — the same AD_KEYS/deepPrune logic as the WebView edition's
 *   page-level yt-block.js, applied inside the engine. The page-level
 *   script (content/yt-inject.js -> page/yt-block.js) still runs for the
 *   layers only it can do (initial player response, XHR/fetch reads,
 *   UI sweep, in-stream fallback). SSAP server-stitched mid-rolls remain
 *   impossible to remove client-side; the watchdog fast-forward applies.
 *
 * App link: the Android app connects via native messaging ("browser").
 * It can push {type:"config", enabled, allowlist} and receives
 * {type:"count", blocked} deltas and download requests.
 */

let enabled = true;
let allowlist = new Set();

/* ---- rule storage (loaded from blocklist/rules.json) ---- */
let hosts = new Set();        // hosts-file semantics: exact + subdomains
let domains = new Set();      // ||host^ : exact + subdomains
let filters = [];             // [{h, p, o}]  ||host/path^$options
let filterIndex = new Map();  // host -> [filter, ...]
let exceptions = [];          // [{h, p, o}]  @@ rules
let exceptionIndex = new Map();
let urlPatterns = [];         // legacy curated substrings
let loaded = false;

let blockedCount = 0;
let countSinceFlush = 0;

/* supported option tokens for block rules */
const TYPE_OPTS = {
  script: 'script',
  image: 'image',
  stylesheet: 'stylesheet',
  css: 'stylesheet',
  xmlhttprequest: 'xmlhttprequest',
  xhr: 'xmlhttprequest',
  subdocument: 'subdocument',
  frame: 'subdocument',
  object: 'object',
  media: 'media',
  font: 'font',
  websocket: 'websocket',
  ping: 'ping',
  other: 'other'
};

/** Parses "third-party,script,domain=~a.com|b.com" into a matcher object. */
function parseOpts(str) {
  const o = {
    types: null,          // null = all types
    notTypes: [],
    thirdParty: null,     // null = any
    important: false,
    docScope: false,      // @@...$document page-level exception
    domains: null,        // Set of hosts the rule applies to
    notDomains: new Set()
  };
  if (!str) return o;
  for (const part of String(str).split(',')) {
    const t = part.trim().toLowerCase();
    if (!t) continue;
    if (t === 'third-party' || t === '3p') { o.thirdParty = true; continue; }
    if (t === 'first-party' || t === '1p') { o.thirdParty = false; continue; }
    if (t === 'important') { o.important = true; continue; }
    if (t === 'document') { o.docScope = true; continue; }
    if (t === 'match-case' || t === 'all') { continue; }
    if (t.startsWith('domain=')) {
      o.domains = new Set();
      for (const d of t.slice(7).split('|')) {
        const dd = d.trim().toLowerCase();
        if (!dd) continue;
        if (dd.startsWith('~')) { if (dd.length > 1) o.notDomains.add(dd.slice(1)); }
        else o.domains.add(dd);
      }
      if (o.domains.size === 0 && o.notDomains.size === 0) o.domains = null;
      continue;
    }
    if (t.startsWith('~') && TYPE_OPTS[t.slice(1)]) { o.notTypes.push(TYPE_OPTS[t.slice(1)]); continue; }
    if (TYPE_OPTS[t]) {
      if (o.types === null) o.types = new Set();
      o.types.add(TYPE_OPTS[t]);
      continue;
    }
    /* unsupported option present — caller decides */
    o.unsupported = t;
  }
  return o;
}

/* ---- per-request context ---- */
function hostOf(maybeUrl) {
  try { return new URL(maybeUrl).hostname.toLowerCase(); } catch (e) { return ''; }
}

function parentsOf(host) {
  const parts = host.split('.');
  const out = [host];
  for (let i = 1; i < parts.length - 1; i++) out.push(parts.slice(i).join('.'));
  return out;
}

function thirdPartyOf(details, reqHost) {
  const doc = details.documentUrl || details.originUrl || details.initiator || '';
  if (!doc) return false;               /* main frame: treat as first-party */
  const docHost = hostOf(doc);
  if (!docHost) return false;
  return docHost !== reqHost;
}

/** true when a rule's option set matches the request context */
function optsMatch(o, ctx) {
  if (o.unsupported) return false;      /* never guess with unknown options */
  if (o.types && !o.types.has(ctx.type)) return false;
  for (const nt of o.notTypes) { if (nt === ctx.type) return false; }
  if (o.thirdParty !== null && o.thirdParty !== ctx.thirdParty) return false;
  if (o.domains && !(ctx.docHost && hostOrParentIn(o.domains, ctx.docHost))) return false;
  if (o.notDomains.size && ctx.docHost && hostOrParentIn(o.notDomains, ctx.docHost)) return false;
  return true;
}

function hostOrParentIn(set, host) {
  for (const h of parentsOf(host)) { if (set.has(h)) return true; }
  return false;
}

function indexAdd(map, host, entry) {
  const arr = map.get(host);
  if (arr) arr.push(entry);
  else map.set(host, [entry]);
}

function loadRulesObject(data) {
  hosts = new Set(data.hosts || []);
  domains = new Set(data.domains || []);
  urlPatterns = data.urls || [];
  filters = [];
  exceptions = [];
  filterIndex = new Map();
  exceptionIndex = new Map();
  for (const f of (data.filters || [])) {
    const rule = { h: f.h, p: f.p || '', o: parseOpts(f.o) };
    if (rule.o.unsupported) continue;
    filters.push(rule);
    indexAdd(filterIndex, rule.h, rule);
  }
  for (const x of (data.exceptions || [])) {
    if (!x.h) continue;                 /* hostless @@ rules are out of scope */
    const rule = { h: x.h, p: x.p || '', o: parseOpts(x.o) };
    if (rule.o.unsupported) continue;
    exceptions.push(rule);
    indexAdd(exceptionIndex, rule.h, rule);
  }
  loaded = true;
}

function loadRules() {
  fetch(browser.runtime.getURL('blocklist/rules.json'))
    .then((r) => r.json())
    .then((data) => { loadRulesObject(data); })
    .catch(() => { loaded = false; });
}

/* ---- matching ---- */

/** user allowlist wins over everything (exact host or any parent) */
function userAllowed(host) {
  if (allowlist.size === 0) return false;
  return hostOrParentIn(allowlist, host);
}

/** @@ exception: rule host must cover the request host, path prefix must
 *  match, options must match. $document exceptions apply to any request
 *  whose DOCUMENT host is covered by the rule. */
function exceptionFor(url, ctx) {
  for (const host of parentsOf(ctx.host)) {
    const arr = exceptionIndex.get(host);
    if (!arr) continue;
    for (const rule of arr) {
      if (rule.o.docScope) {
        if (ctx.docHost && hostOrParentIn(new Set([rule.h]), ctx.docHost)) return true;
        continue;
      }
      if (rule.p && !url.pathname.startsWith(rule.p)) continue;
      if (!optsMatch(rule.o, ctx)) continue;
      return true;
    }
  }
  return false;
}

function filterFor(url, ctx) {
  for (const host of parentsOf(ctx.host)) {
    const arr = filterIndex.get(host);
    if (!arr) continue;
    for (const rule of arr) {
      if (rule.p && !url.pathname.startsWith(rule.p)) continue;
      if (!optsMatch(rule.o, ctx)) continue;
      return rule;
    }
  }
  return null;
}

/** Like filterFor but only returns $important rules — those beat
 *  exceptions (uBlock semantics). */
function importantFilterFor(url, ctx) {
  for (const host of parentsOf(ctx.host)) {
    const arr = filterIndex.get(host);
    if (!arr) continue;
    for (const rule of arr) {
      if (!rule.o.important) continue;
      if (rule.p && !url.pathname.startsWith(rule.p)) continue;
      if (!optsMatch(rule.o, ctx)) continue;
      return rule;
    }
  }
  return null;
}

function shouldBlock(details) {
  if (!enabled || !loaded) return false;
  let u;
  try { u = new URL(details.url); } catch (e) { return false; }
  const scheme = u.protocol;
  if (scheme !== 'http:' && scheme !== 'https:') return false;
  const host = u.hostname.toLowerCase();
  if (!host) return false;
  const ctx = {
    host,
    type: String(details.type || 'other'),
    docHost: hostOf(details.documentUrl || details.originUrl || details.initiator || ''),
    thirdParty: thirdPartyOf(details, host)
  };
  if (userAllowed(host)) return false;
  if (importantFilterFor(u, ctx)) return true;   // $important beats @@ exceptions
  if (exceptionFor(u, ctx)) return false;
  if (hosts.has(host) || hostOrParentIn(hosts, host)) return true;
  if (domains.has(host) || hostOrParentIn(domains, host)) return true;
  if (filterFor(u, ctx)) return true;
  for (let i = 0; i < urlPatterns.length; i++) {
    if (details.url.indexOf(urlPatterns[i]) !== -1) return true;
  }
  return false;
}

/* ---- webRequest: cancel blocked requests ---- */
browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (shouldBlock(details)) {
      blockedCount++;
      countSinceFlush++;
      maybeFlushCount();
      return { cancel: true };
    }
    ytStreamFilter(details);
    return {};
  },
  { urls: ['<all_urls>'] },
  ['blocking']
);

/* ---- YouTube InnerTube response pruning (engine level) ---- */

/* Exact port of the WebView edition's yt-block.js v1.4.0 prune table —
   keep in sync with page/yt-block.js. */
const YT_AD_KEYS = {
  adPlacements: 1,
  adSlots: 1,
  playerAds: 1,
  adBreaks: 1,
  adBreakHeartbeatParams: 1,
  adPlacementsForThirdParty: 1,
  adSlotRenderer: 1,
  adBreakAdRenderer: 1,
  adPlacementRenderer: 1,
  inVideoAdCta: 1
};
const YT_PLAYER_API = /\/youtubei\/v1\/(player|next|video_details|get_midroll_info|viewer|playlist|get_watch|ssap)(?:[?#]|$)/;
const YT_HOST_RE = /^([a-z0-9-]+\.)*(youtube\.com|youtube-nocookie\.com)$/;

function ytDeepPrune(node, depth) {
  if (!node || typeof node !== 'object' || depth > 60) return node;
  if (Array.isArray(node)) {
    for (let i = 0; i < node.length; i++) ytDeepPrune(node[i], depth + 1);
    return node;
  }
  for (const key in node) {
    if (!Object.prototype.hasOwnProperty.call(node, key)) continue;
    if (Object.prototype.hasOwnProperty.call(YT_AD_KEYS, key)) {
      try { delete node[key]; } catch (e) { node[key] = undefined; }
      continue;
    }
    ytDeepPrune(node[key], depth + 1);
  }
  return node;
}

/** Streams the response through a pruning filter for InnerTube endpoints. */
function ytStreamFilter(details) {
  if (!enabled) return;
  try {
    const u = new URL(details.url);
    if (u.protocol !== 'https:') return;
    if (!YT_HOST_RE.test(u.hostname.toLowerCase())) return;
    if (!YT_PLAYER_API.test(details.url)) return;
    if (userAllowed(u.hostname.toLowerCase())) return;
    const filter = browser.webRequest.filterResponseData(details.requestId);
    const chunks = [];
    filter.ondata = (e) => { chunks.push(e.data); };
    filter.onstop = () => {
      try {
        const td = new TextDecoder();
        let text = '';
        for (const c of chunks) text += td.decode(c, { stream: true });
        text += td.decode();
        const data = JSON.parse(text);
        ytDeepPrune(data, 0);
        const out = new TextEncoder().encode(JSON.stringify(data));
        filter.write(out.buffer);
        filter.close();
        blockedCount++;
        countSinceFlush++;
        maybeFlushCount();
      } catch (e) {
        /* not JSON or pruning failed: pass the original body through */
        try {
          for (const c of chunks) filter.write(c);
          filter.close();
        } catch (e2) { /* filter already dead */ }
      }
    };
  } catch (e) { /* filterResponseData unavailable — page layer still applies */ }
}

/* ---- count reporting to the app ---- */
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
    port.postMessage({ type: 'count', blocked: countSinceFlush });
  } catch (e) { /* port not ready */ }
  countSinceFlush = 0;
}

loadRules();

/* ---- Native messaging with the Android app ---- */
let port = null;

try {
  port = browser.runtime.connectNative('browser');
  port.onMessage.addListener((msg) => {
    if (!msg) return;
    if (msg.type === 'config') {
      if (typeof msg.enabled === 'boolean') enabled = msg.enabled;
      if (Array.isArray(msg.allowlist)) {
        allowlist = new Set(msg.allowlist.map((h) => String(h).toLowerCase()));
      }
    } else if (msg.type === 'media-grab') {
      relayMediaGrab(msg);
    }
  });
  port.onDisconnect.addListener(() => { port = null; });
} catch (e) {
  port = null;
}

/* ---- Media grabber: network sniffing + capture relay ---- */

/* Media content types worth listing in the grabber. */
const MEDIA_CT = /(video\/|audio\/|mpegurl|dash\+xml)/i;

/* Passive observer: list media-typed responses (network-level detection for
 * resources the DOM scan cannot see, e.g. DASH segments). */
browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    try {
      if (!details.responseHeaders) return;
      let ct = '';
      let cl = -1;
      for (const h of details.responseHeaders) {
        const n = (h.name || '').toLowerCase();
        if (n === 'content-type') ct = h.value || '';
        else if (n === 'content-length') cl = parseInt(h.value, 10) || -1;
      }
      const url = details.url || '';
      const shaped = /\.(m3u8|mpd|mp4|webm|mkv|m4s|ts|mp3|m4a|aac|ogg|opus|flac|zip|rar|7z|pdf|apk)(\?|#|$)/i.test(url);
      if (MEDIA_CT.test(ct) || shaped) {
        if (port) {
          port.postMessage({ type: 'media-net', url: url, mime: ct, size: cl });
        }
      }
    } catch (e) { /* never break the request pipeline */ }
  },
  { urls: ['<all_urls>'] },
  ['responseHeaders']
);

/* Relays the app's blob-capture request to the page's content script.
 * tabs.sendMessage support is probed at runtime; failure is reported back
 * honestly instead of silently dropping. */
async function relayMediaGrab(msg) {
  const reply = (ok, reason) => {
    if (port) port.postMessage({ type: 'grab-result', sid: msg.sid, ok: ok, reason: reason || '' });
  };
  try {
    if (typeof browser.tabs === 'undefined' ||
        typeof browser.tabs.sendMessage !== 'function') {
      reply(false, 'no-tabs-api');
      return;
    }
    const tabs = await browser.tabs.query({ active: true, currentWindow: true });
    if (!tabs || !tabs.length) {
      reply(false, 'no-active-tab');
      return;
    }
    await browser.tabs.sendMessage(tabs[0].id, {
      type: 'media-grab', url: msg.url, sid: msg.sid, limit: msg.limit
    });
    reply(true, '');
  } catch (e) {
    reply(false, String((e && e.message) || e));
  }
}
