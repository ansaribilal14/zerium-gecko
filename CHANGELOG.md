# Changelog

All notable changes to Zerium G are documented in this file (Keep a Changelog format). Stable builds are published as immutable `vX.Y.Z` releases; the rolling `latest` tag tracks the newest successful build of `main`.

## [1.3.0] — 2026-09-20

Add-on action popups — the toolbar gap noted in v1.2.0's honest-scope section is closed.

### Added
- **Add-on action popups in the toolbar** — a puzzle-piece button appears in the top bar whenever the active tab has visible browser/page actions from installed add-ons (e.g. uBlock Origin's block counts, Dark Reader's controls). Tapping it opens an anchored list of those actions with their live badges (colored with the add-on's own badge colors); tapping a row triggers the action. When the action defines a popup, the add-on's popup UI renders in a rounded, anchored window built on a dedicated popup session — created, opened and closed by the app exactly per the GeckoView contract (the engine marks the session as an extension popup and loads the popup URI into it; dismissing the window closes the session, and `window.close()` from the popup dismisses the window).
- Action state is tracked both engine-wide (default actions) and per-tab (session overrides, the way page actions appear/disappear per site), so the button reflects the tab you are actually on. The popup session inherits the active tab's private mode and the global JavaScript setting; Enhanced Tracking Protection stays on.

### Honest scope
- The action list and popups work for add-ons that declare a `browser_action`/`page_action`. Add-ons without actions are unaffected and keep running their background/content logic as before. The built-in Zerium G Shield defines no action and adds no toolbar button.
- Popups render in a plain anchored window: links inside a popup that force a new-tab open are dropped (the popup session has no tab delegate), matching the minimal-popup contract of GeckoView embedding; desktop Firefox behaves similarly inside panels.

## [1.2.0] — 2026-09-19

Brave-inspired UI overhaul, an add-on platform, and a stronger engine-level YouTube blocker.

### Added
- **Add-ons (WebExtensions)** — a full extension manager in the Gecko engine. Installed add-ons list with icons, names and versions; enable/disable, allow-in-private-browsing and uninstall per add-on; the built-in Zerium G Shield is pinned (disable-able, not removable). Installs come from a curated AMO catalog (uBlock Origin, Dark Reader, SponsorBlock, Privacy Badger, Bitwarden, I don't care about cookies, LocalCDN, Decentraleyes — resolved to the current signed package through the public AMO v5 API at install time) or from a local `.xpi` file. Install-time permission prompts (with a private-mode grant checkbox), optional/update permission prompts, and honest engine error mapping (unsigned packages are rejected with a clear explanation — Gecko enforces Mozilla signatures).
- **Brave-style menu** — the old popup menu is now a sectioned bottom sheet: a circular quick-action row (back / forward / refresh / share), then sections for browsing, page tools (reader, desktop site, JavaScript, translate, print, save as PDF, per-site blocking) and app entries (Add-ons, Delete browsing data, Settings, Exit).
- **Brave-style start page** — a three-metric **Privacy Stats** card (Trackers & Ads Blocked, Est. Data Saved, Est. Time Saved), favicon tiles with letter-monogram fallback (bookmarks first, then most-visited local history), and search suggestions from DuckDuckGo's suggestion endpoint while typing on the start page only (labelled in the UI; nothing fires anywhere else).
- **Delete browsing data** — Brave-style dialog with history, cookies/site data and caches; the engine `StorageController` clears cookies, DOM storages, auth sessions and image/network caches. Reachable from the menu and the tab switcher.
- **Search your tabs** — the tab switcher (already grid + live previews) gained Brave's filter bar over open-tab titles and URLs, with an empty state.
- **HTTPS-only mode** — off / private tabs only (default) / all tabs, through the engine's `allowInsecureConnections`; applied live from Settings.
- **Curated YouTube engine filters** — the shield rules now include YouTube ad/telemetry endpoint blocks (`/api/stats/ads`, `/pagead/`, `/ptracking`, `/get_midroll_info`, `/error_204`, `static.doubleclick.net`) and the ad-tier segment marker `ctier=` as URL patterns, on top of the InnerTube response pruning and the page script. The engine suite grew from 42 to 52 cases covering the new layer (ad-tier segments blocked, normal playback untouched).

### Changed
- Tab switcher header holds the delete-browsing-data shortcut; settings gained Add-ons and HTTPS-only rows.
- Start-page search no longer autofocuses the keyboard on every new tab (Brave behavior), and the page keeps the engine's dark-scheme support.

### Honest scope
- Only **Mozilla-signed** add-ons install (the engine enforces it): the catalog is AMO-hosted, and locally picked files must be signed Firefox add-ons — Chrome `.crx` packages and unsigned builds are rejected by the engine. Action popups rendered by add-ons (e.g. toolbar popups) are engine-supported but not surfaced in the toolbar yet — tracked on the roadmap.
- Privacy-stats data/time figures are honest estimates (≈50 KB and ≈50 ms per blocked request, Brave's published conservative formula), computed on-device and labelled "Est.".
- Search suggestions call DuckDuckGo only while typing on the start page; the omnibox makes no suggestion requests.

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
