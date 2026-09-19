# Zerium G

**The GeckoView edition of [Zerium Browser](https://github.com/ansaribilal14/zerium-browser)** — a free and open-source privacy browser for Android built on Mozilla's Gecko engine, shipped as a standalone app in its own repository.

GPL-3.0 · no accounts · no analytics · no telemetry.

## Why a second browser?

Zerium Browser (the original) ships on the Android System WebView because it is small, fast and CI-buildable — but WebView hard-caps what a privacy browser can do. Zerium G moves to GeckoView to remove those caps entirely:

| Capability | Zerium (WebView) | Zerium G (GeckoView) |
|---|---|---|
| Ad/tracker blocking | `shouldInterceptRequest` (app-level) | **Engine-level** `webRequest` blocking + Adblock-syntax subset + cosmetic CSS inside the engine |
| YouTube ad suppression | page-level JSON pruning | **Engine-level** InnerTube response pruning + the proven page-level script |
| Tracking protection | none (WebView limit) | **Strict Enhanced Tracking Protection** built into Gecko |
| Private tabs | shares the WebView cookie jar (documented limitation) | **True private sessions** — separate storage, no persistence |
| Per-site JavaScript | kill-list with reload heuristics | **Per-session switch**, engine-native |
| Desktop site | user-agent swap + reload | **UA + viewport mode**, engine-native |
| Printing / PDF | WebView print bridge | **Engine PDF rendering** (`saveAsPdf`) + system print dialog |
| Password autofill | platform autofill | **Platform autofill** through Gecko's form structure |
| Extensions | impossible | Possible (WebExtensions platform) |

Both apps keep the same honest-scope discipline: everything documented here is in the code, and limitations are stated plainly.

## Features

- **Engine-level blocking (Zerium G Shield)** — a built-in WebExtension cancels requests inside the Gecko engine before the page ever sees them: 79,963 StevenBlack hosts (MIT), 91,450 `||host^` domains and 11,577 path filters compiled from EasyList + EasyPrivacy (CC-BY-SA-3.0), 1,131 exception rules, 84 curated URL patterns — with resource types, `$third-party`, `$domain=` and `$important` support. Site allowlist applies live through native messaging; every block is counted.
- **YouTube suppression at two layers** — player API responses are pruned of ad structures inside the engine (`filterResponseData`), and the WebView edition's battle-tested page script (setter trap, order-independent XHR getters, instant skip, confirmed-ad watchdog) runs at document_start. Client-side scheduled ads are pruned and never appear.
- **Cosmetic filtering** — 4,000 element-hiding rules applied as engine content CSS at document start, so ad containers never paint.
- **Strict ETP + cookie-banner auto-rejection** — social trackers, fingerprinters, cryptominers, email trackers; consent prompts auto-rejected where the engine supports it.
- **Popup blocking** — denied by default, allow once / always per site.
- **True private tabs** — GeckoView private sessions with their own transient storage. Nothing about a private tab lands in history, bookmarks or the session snapshot.
- **Reader view** — Mozilla Readability extraction with reading time and byline, rendered in-session, dark-mode aware.
- **Printing and Save as PDF** — the system print dialog via GeckoView's print bridge, and direct PDF export into the Downloads collection.
- **Site permissions, with memory** — location, camera, microphone, notifications, DRM and storage prompts with per-site remember; tracking and autoplay are never granted. Platform password autofill (bring your own password manager).
- **Per-tab JavaScript and Desktop-site toggles** — engine-native session settings, applied live, with reload.
- **Tabs with live previews and full session restore** — compositor-captured screenshots (never for private tabs), grid switcher, back/forward history preserved across app restarts.
- **Pull-to-refresh that respects the page** — arms only at the actual top of a page (engine scroll position); edge-swipe tab switching.
- **Find in page** — the engine Finder API with a real x/y counter.
- **Downloads** — Content-Disposition downloads into the public Downloads collection, a downloads screen with sizes/dates, long-press image downloads via the extension.
- **Context menus, intent dispatch, honest error pages** — long-press links and images; intent:// and market fallbacks; certificate failures render an explicit error page, never bypassed silently.
- **Custom search engines, text size, content language** — JSON-defined engines beside the six built-ins; 85–150% text scaling; content-language override.
- **Bookmarks & history, start page tiles and block stats** — local SQLite only; most-visited tiles and the lifetime block counter on the start page.
- **Material 3 soft UI** — the same tonal, rounded design language as the WebView edition, with Material You dynamic color on Android 12+.
- **Add-ons (WebExtensions)** — install Mozilla-signed extensions from a curated AMO catalog or a local `.xpi`; manage enable/disable, private-mode access and uninstall per add-on; install-time permission prompts with explicit permission lists; add-on **action popups** render in the toolbar (a puzzle-piece button appears when the active tab has add-on actions, listing them with live badges and opening each add-on's popup UI in an anchored window). The engine's WebExtensions platform means the broad Firefox add-on ecosystem runs in-app (the shared `chrome.*` extension namespace means most Chrome extensions that avoid Chrome-only APIs work when packaged as signed Firefox add-ons).
- **Brave-style menu and start page** — a sectioned bottom-sheet menu with a circular quick-action row, a Privacy Stats card on the start page (Trackers & Ads Blocked / Est. Data Saved / Est. Time Saved — Brave's conservative ≈50 KB + ≈50 ms per-block formula, computed on-device), favicon tiles with monogram fallback, DuckDuckGo suggestions on the start-page search box only, "Search your tabs" filtering in the tab switcher, and a Delete-browsing-data dialog backed by the engine `StorageController`.
- **HTTPS-only mode** — off / private tabs only / all tabs, applied live through the engine.

## Honest limitations (v1.3.0)

The v1.0.0 list (denied permission prompts, no printing/PDF, no reader view, no autofill wiring, hosts-only blocking, no YouTube layer) and the v1.1.0 list (same, after the resolution release) are **fully resolved**. What remains honest to state:

- The filter engine is an **Adblock-syntax subset**, not the full uBlock DSL: regex and wildcard filters, `$popup`, `$csp`, `$redirect`, `$removeparam`, procedural selectors (`:has-text`, `:-abp-…`) and domain-scoped element hiding are dropped by the generator instead of approximated — see `docs/BLOCKING.md`.
- SSAP server-stitched YouTube mid-rolls are part of the media stream itself; no client-side blocker can remove them. The confirmed-ad watchdog fast-forwards through them (same honest boundary as the WebView edition).
- **Add-ons must be Mozilla-signed** — the Gecko engine enforces signature validation and it cannot be turned off by an embedder: the catalog therefore points at AMO-hosted packages, locally picked files must be signed `.xpi` Firefox add-ons, and raw Chrome `.crx` or unsigned developer builds are rejected with an explicit message.
- **Add-on action popups** render in an anchored toolbar window since v1.3.0; one embedding boundary remains: links inside a popup that force a new-tab open are dropped (the popup session has no tab delegate).
- Reader view parses the DOM snapshot at load: paywalled or JS-gated content yields what is actually in the DOM.
- Reader-mode renders live in the app cache as a single HTML file and are deleted on exit; a crash between open and exit could leave one behind (app-private storage).
- datetime-local / month / week form prompts are dismissed rather than approximated with a different control.
- Filter lists refresh with every release and CI regenerates them weekly; in-app manual refresh is on the roadmap.

## Installation

1. Download `Zerium-G-v*-release.apk` from the [latest release](https://github.com/ansaribilal14/zerium-gecko/releases/latest).
2. Optional: verify with `sha256sum -c SHA256SUMS.txt`.
3. Requires **Android 8.0+** (API 26). Universal APK.

Zerium G and Zerium Browser are separate apps with separate package names — you can run both.

## Releases

Same two-channel model as the main project: an immutable `vX.Y.Z` stable tag and a rolling `latest` tag rebuilt on every successful push to `main`. Every release carries `SHA256SUMS.txt`.

## Building from source

```bash
./gradlew :app:assembleRelease   # JDK 17; APK lands in app/build/outputs/apk/release/
```

CI builds, signs (via the `KEYSTORE_B64` secret), checksums and publishes both channels on every push to `main`. A weekly workflow (`update-rules.yml`) regenerates the blocking rules from upstream EasyList/EasyPrivacy/StevenBlack sources.

## Repository layout

```
app/src/main/java/com/zerium/gecko/   Browser UI, tabs, delegates, storage
app/src/main/assets/extension/        Zerium G Shield (built-in WebExtension):
  manifest.json, background.js        filter engine + InnerTube pruning + native messaging
  blocklist/rules.json                hosts + compiled Adblock subset (generated, reproducible)
  content/cosmetic.css                element-hiding rules
  content/yt-inject.js, reader.js     page-context YT injection, reader extraction
  page/yt-block.js                    the WebView edition's proven YouTube script
scripts/gen_gecko_rules.py            rule compiler (EasyList/EasyPrivacy/StevenBlack)
scripts/test_gecko_shield.js          42-case black-box suite for the shield engine
docs/                                 Privacy, blocking scope, roadmap
.github/workflows/                    CI: build+sign+release; weekly rule refresh
```

## License

GPL-3.0, same as the Zerium Browser project. Third-party: Mozilla GeckoView (MPL-2.0), StevenBlack hosts (MIT), EasyList/EasyPrivacy-derived rules and cosmetic selectors (CC-BY-SA-3.0), Mozilla Readability (Apache-2.0) — see `docs/BLOCKING.md` and the in-repo attributions.
