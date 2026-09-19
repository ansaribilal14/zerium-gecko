# Changelog

All notable changes to Zerium G are documented in this file (Keep a Changelog format). Stable builds are published as immutable `vX.Y.Z` releases; the rolling `latest` tag tracks the newest successful build of `main`.

## [1.1.0] — 2026-09-19

Every honest limitation of v1.0.0 is resolved in this release, plus a batch of pro-grade features.

### Added
- **Printing and Save as PDF** — Print opens the Android print dialog through GeckoView's built-in print bridge (`session.printPageContent()`); Save as PDF renders the page via `session.saveAsPdf()` and stores it in the public Downloads collection (app-folder fallback below API 29).
- **Reader view** — Mozilla Readability (Apache-2.0) runs as a shield content script on every page; the parsed article renders in the same session with reading time, byline, dark-mode awareness and a restore-to-page path. Reader files are removed from the app cache on exit.
- **Website content permissions, with prompts** — location, notifications, persistent storage, storage access, XR and DRM show a dialog with allow / deny once or remember-per-site; camera and microphone additionally pass the Android platform permission dialog. Tracking permission and autoplay grants are never allowed. A settings switch switches the default to deny-silently; remembered decisions are clearable.
- **Password autofill (platform)** — GeckoView's form fields are exposed to the Android autofill framework, so any password manager (Bitwarden, KeePassDX, …) fills forms. Zerium G still stores no passwords; a settings row shows status and links to system settings.
- **Adblock-syntax subset in the shield** — `||host^`, `||host/path^` and `@@` exception rules with resource types, `$third-party`/`$first-party`, `$domain=` (with `~` exclusions) and `$important` (which beats exceptions, uBlock semantics). Rules compiled from EasyList + EasyPrivacy: 91,450 domains, 11,577 path filters, 1,131 exceptions on top of the 79,963 StevenBlack hosts. Cosmetic list grew from 649 to 4,000 sanitized selectors.
- **YouTube suppression, engine-level** — the player API responses (`player`, `next`, `get_watch`, `ssap`, …) are pruned of ad structures inside the engine via `filterResponseData`, and the WebView edition's proven page-level script (setter trap, order-independent XHR getters, fetch hook, UI sweep, confirmed-ad watchdog) runs in the page context at document_start. The 21-case black-box test suite of the WebView edition validates the page script unchanged; a new 42-case suite covers the background engine.
- **Popup blocking** — popups denied by default with a per-site allow-once / always-allow prompt; global switch in Settings.
- **Cookie-banner auto-rejection** — Gecko's cookie-banner service in reject mode, regular and private sessions, with a settings switch.
- **Pull-to-refresh** — scroll-aware (the engine's `ScrollDelegate` reports the page position, so the gesture only arms at the actual top of a page), with a settings switch.
- **Edge-swipe tab switching** — 24dp edge inset, horizontal-only trigger, settings switch.
- **Session restore with full history** — tabs survive process death with their complete back/forward state (`SessionState`), not just URLs.
- **Downloads screen** — lists the public Downloads collection (API 29+) or the app download folder (API 26–28) with sizes and dates; entries open with a tap.
- **Custom search engines** — JSON-defined engines appear in the picker next to the six built-ins (same model as the WebView edition).
- **Start page tiles and stats** — most-visited sites from local history plus the lifetime block counter.
- **Text size** — page text scaling (85–150%) via the engine's `fontSizeFactor`; **content language** override via `setLocales`.

### Changed
- Find-in-page counter now shows real `x/y` totals (`FinderResult.total`).
- Settings reorganized: search engines (incl. custom), blocking (allowlist, cookie banners, popups, site permissions, autofill), appearance (theme, page scheme, text size, language, gestures, pull-to-refresh).

### Honest scope
- The filter engine is an Adblock-syntax **subset**; regex/wildcard filters, `$popup`, `$csp`, `$redirect`, `$removeparam`, procedural selectors and domain-scoped element hiding are dropped by the generator, not approximated — see `docs/BLOCKING.md`.
- SSAP server-stitched YouTube mid-rolls remain impossible to remove client-side; the watchdog fast-forward remains the fallback (unchanged from the WebView edition).
- Reader parses the DOM snapshot (paywalls yield stubs); reader renders are cache files deleted on exit; private-tab reader files never persist beyond exit.
- datetime-local / month / week input prompts are dismissed rather than approximated with a different control.

## [1.0.0] — 2026-09-19

### Added
- **Initial release.** Zerium G is the GeckoView edition of the Zerium privacy browser: a standalone app, separate repository, separate package name (`com.zerium.gecko`).
- **Zerium G Shield** — built-in WebExtension performing engine-level request blocking (StevenBlack hosts semantics + 84 curated URL patterns) and cosmetic element-hiding CSS; live allowlist and on/off through native messaging; blocked-request counters.
- **Strict Enhanced Tracking Protection** from the Gecko engine (social/analytics/fingerprinting/cryptominer/email-tracker categories).
- **True private tabs** (GeckoView private sessions) with live-preview capture suppressed.
- **Per-tab JavaScript and Desktop-site toggles** (engine session settings, applied live).
- **Tab switcher with compositor previews**, find-in-page (engine Finder), downloads to the public Downloads collection (plus `browser.downloads` for image saves), link/image context menus, intent/market dispatch, honest network error pages, page color-scheme forcing, local bookmarks/history, Material 3 soft UI with dynamic color.

### Honest scope
- Content-permission prompts are denied by default; printing, reader view and password autofill are not wired yet; no YouTube-specific scriptlet yet — all listed on the roadmap.
