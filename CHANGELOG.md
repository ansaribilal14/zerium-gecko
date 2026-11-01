# Changelog

All notable changes to Zerium G are documented in this file (Keep a Changelog format). Stable builds are published as immutable `vX.Y.Z` releases; the rolling `latest` tag tracks the newest successful build of `main`.

## [Unreleased]

### Planned
- Torrent engine evaluation (APK-size tradeoff), DASH assembly, encrypted HLS — see the download track in ROADMAP.
- Spaces follow-ups: per-space colored identities, per-space add-on presets documented at creation time.

## [1.6.0] — 2026-11-01

The "tab groups inside Spaces + a real Android browser citizen" release, mirroring the same two features that landed in Zerium Browser 1.12.0.

### Added
- **Tab Groups inside Spaces** — Chrome-style drag-and-drop grouping within the active space. Long-press a card and drag: drop it on another tab to start a group, on a group's header or a member to join that group, on the "Ungrouped" header or empty space to ungroup, and on a Space chip to move it to that space. Groups render as coloured header rows (8-colour palette) with a live tab count and a collapse chevron; collapsed groups keep their tabs running. Long-press a header to rename, recolour or ungroup all. The switcher's tab search keeps working across the group structure.
- **Sessions are never recreated for organization** — grouping is pure metadata (`Tab.groupId`): moving tabs between groups never touches the GeckoSession, its history or the space's engine profile. Groups persist per space, ride along the per-space tab archive (each archived tab keeps its group), survive space switches and app restarts, and disappear when their last member leaves.
- **Default browser support** — Settings → Links → "Default browser" requests `RoleManager.ROLE_BROWSER` on Android 10+, so Zerium G appears in other apps' link choosers and can be set as the system default. Below Android 10 the row opens the system default-apps screen. The app only requests the role — Android owns the dialog.
- **External links open in** (Settings → Links) — where links handed over by other apps land: *Default space* (default), *Current space*, or *Ask every time* with a space picker. Only http(s) URLs are accepted. The engine runs one profile per process, so the router honours isolation honestly: links land on the shared default profile only while the active space is non-isolated, and a link bound for a different isolated space is parked and opens right after the space-switch restart — it can never silently ride another space's cookies.

### Notes
- Quick long-press (no drag) on a tab card keeps the old behaviour: the move-to-space dialog. Private tabs stay out of groups, as they stay out of spaces.
- Feature specs live in `docs/issues/` (the repo token cannot open GitHub issues).

## [1.5.0] — 2026-11-01

The Zerium Spaces release: tab groups with per-space logins — one isolated space per identity, with a real engine profile directory behind each.

### Added
- **Zerium Spaces** — a Spaces entry in the menu and a chip bar in the tab switcher list every space with its live tab count (in memory for the active space, archived for the others). The switcher shows the active space's tabs plus private tabs, which belong to no space. New tabs open into the active space automatically.
- **Isolated spaces** — creating a space can turn on Isolation: the space then owns an engine profile directory (launched with the standard `-profile` argument), giving it its own cookies, site data, cache and installed add-ons. Log in as user A in one space and user B in another; sessions survive closing tabs, switching spaces and app restarts.
- **Space switching** — GeckoView allows one engine runtime per process, so switching spaces restarts the browser process (tabs persist with full session state, the engine relaunches with the target space's profile, and its tabs restore with cookies intact). A toast says what is happening; the same flow serves creating your first isolated space and switching back.
- **Space management** — the manager sheet supports rename and delete with an explicit confirmation: tabs close, the archived tabs and the space's whole profile directory are erased, other spaces keep their sessions. Deleting the space you are in switches to the default space first (its directory is wiped after the restart). The default space can be renamed but not deleted or isolated.
- **Move tabs between spaces** — long-press a tab card to move it. Cross-isolation moves archive the tab with its session state (it reopens under the target space's profile on the next switch); moves between non-isolated spaces re-tag instantly.
- **Per-space tab archive** — only the active space's tabs live in memory; every other space's tabs (URL + full session state) wait in an archive and restore when their space becomes active, so memory stays flat no matter how many spaces you keep.

### Changed
- "Close all" in the tab switcher now closes the current space's tabs (the scope the switcher shows).
- "Delete browsing data" states its scope plainly when isolated spaces exist: it clears the running engine profile — the current space's data.

### Honest scope
- Switching isolated spaces restarts the engine — by design, not a bug: one engine profile runs per process. Tab state, logins and site data persist across the restart; a brief toast covers the gap.
- Installed add-ons are per-space (they live in the engine profile), so a new isolated space starts with the built-in Shield and without third-party add-ons until you install them there. The Shield works in every space.
- Private tabs stay outside spaces (they never persist), so they are reachable from every space's switcher.
- Isolation is browsing-session separation between your own spaces, not device-level anonymity.
- The shield extension's blocking pipeline is untouched (Shield suite green; the WebView-parity YouTube scripts unchanged).

## [1.4.0] — 2026-09-20

The Turbo Downloads release: a complete download manager with an in-page media grabber, plus Jetpack Compose (Material 3) screens and deeper appearance customization.

### Added
- **Turbo download engine** — downloads now run through Zerium G's own manager: multi-connection range downloads (up to 8 connections per file where the server supports ranges, automatic single-connection fallback when a server silently ignores ranges), pause and resume with persisted partial progress, a bounded queue (3 concurrent), automatic retry with backoff, and honest error mapping (403/404/range/encrypted/storage/network). Content-Disposition downloads (`onExternalResponse`) are saved as before and recorded in the same manager so everything lands in one list.
- **Background downloads** — a foreground service keeps transfers running while you browse or leave the app; per-task notifications show live progress, speed and ETA with pause/resume/cancel actions, an Open action on completion and Retry on failure (notification permission requested on Android 13+).
- **Media grabber** — a "Media on this page" menu entry lists videos, audio, HLS/DASH playlists, direct file links and blob-backed players detected by the shield extension's new in-page scanner content script plus network-level response sniffing (background `webRequest` observer); each row downloads with one tap.
- **Blob capture (best effort, honestly reported)** — single-file `blob:` players are fetched by the page itself and streamed back in base64 chunks (512 KB each, 300 MB cap). The relay to the page goes through the shield's native-messaging port and `tabs.sendMessage`; when the engine does not support that relay, the tap fails with an explicit message instead of a silent no-op.
- **HLS stream downloads** — m3u8 playlists resolve master→highest-bandwidth variant and download segments sequentially into a single `.ts` file with per-segment progress; encrypted playlists are refused with a clear error.
- **Category folders** — finished files land in `Download/Zerium/<Video|Audio|Images|Docs|Archives|APKs|Other>` via MediaStore on Android 10+ (direct paths with a stated app-dir fallback on older devices).
- **Jetpack Compose download manager** — the Downloads screen is rebuilt in Compose (Material 3): live progress with speed/ETA, filter chips (All/Active/Finished), per-task pause/resume/cancel/retry, tap to open; unit tests cover the task/category/registry layer.
- **Appearance screen (Compose)** — theme mode (System/Light/Dark), Material You dynamic color toggle (Android 12+), and six accent colors, with an honest note about when the accent reaches classic screens.
- **Settings** — switches for accelerated downloads and page-media detection, plus an Appearance row.

### Changed
- `ZeriumGApp` now honours the dynamic-color preference instead of applying Material You unconditionally.
- The shield extension's blocking pipeline is untouched by the media layer: the scanner observes and reports, the engine rules are unchanged (21/21 WebView-suite parity maintained by the shared YouTube scripts).

### Honest scope
- MSE/DASH media served as many small encrypted or indexed segments is only partially capturable; DRM (Widevine) streams are never downloadable.
- Downloads send User-Agent and Referer headers; the engine-internal Gecko cookie jar is not consulted for transfer connections (a direct download of a cookie-gated URL may need to go through the page's own flow).
- Multi-connection speedups only materialize when the server supports HTTP ranges and the network allows parallelism.
- Torrent downloads are not supported (no engine in-app); tracked in ROADMAP.
- The static accent applies instantly to the Compose screens; classic View screens follow on next launch.

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
