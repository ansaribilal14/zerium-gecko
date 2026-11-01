# Roadmap

## v1.7 (shipped)

- Two-menu split mirroring Zerium Browser 1.13.0: the bottom task bar's menu is a compact quick-actions sheet (tabs, spaces, destinations, common toggles); the top menu keeps the full list (quick-nav row, page tools, add-ons, app entries).

## v1.6 (shipped)

- Tab groups inside Spaces: Chrome-style drag-and-drop grouping within the active space as pure metadata (`Tab.groupId`) — moving tabs never recreates the GeckoSession, its history or the space's engine profile. Colored collapsible group headers, persistence per space and across the per-space tab archive. Spec: `docs/issues/tab-groups-inside-spaces.md`.
- Android browser integration: exported browser activity with `ACTION_VIEW` + `category.BROWSABLE` http/https intent filters; Settings → Links with a `RoleManager.ROLE_BROWSER` default-browser request (Android 10+) and "External links open in" (default space / current space / ask every time); links bound for another isolated space are parked across the space-switch restart — isolation is never silently crossed. Spec: `docs/issues/android-browser-integration.md`.

## v1.2 (shipped)

The add-on platform landed in v1.2.0: a full WebExtension manager (curated AMO catalog + local signed `.xpi`, enable/disable, private-mode grants, uninstall, install-time permission prompts), the Brave-style menu and start page, delete-browsing-data, tab search and HTTPS-only mode. See `CHANGELOG.md`.

## v1.3 (shipped)

- Add-on action popups are surfaced in the app: a puzzle-piece toolbar button appears when the active tab has browser/page actions, lists them with badges, and renders the add-on's popup UI in an anchored window (the app owns popup sessions per the GeckoView contract).

## v1.5 (shipped)

- Zerium Spaces: tab groups with per-space logins. Isolated spaces own a real engine profile directory selected with the standard `-profile` launch argument (cookies, site data, cache, installed add-ons); switching spaces restarts the process into the target profile with tabs persisted and restored from a per-space archive. Space chips + manager sheet (switch/rename/delete with directory wipe, including the post-restart wipe for the space you are in), move-tab-between-spaces across isolation boundaries, and space-scoped "Delete browsing data" with a plain scope note.

## v1.4 - blocking depth

- Filter-list refresh channel for the shield (validated, atomic, same discipline as the WebView edition updater) — in-app rule updates between releases.
- Procedural cosmetic selectors (`:has-text`, `:-abp-…`) and domain-scoped element hiding.
- Per-site cosmetic exceptions (`$generichide`/`$specifichide` honoring).
- Reader font-size and typography controls in-app.

## v2.0 - differentiation

- Background media playback controls.
- Sync evaluation (same honest framework as `docs/SYNC_EVALUATION.md` in the main repo).

## Download-manager track (opened in v1.4.0)

- BitTorrent/magnet support: needs an in-app engine decision (library size vs. APK weight next to GeckoView); evaluate libtorrent4j and a native-service design before committing.
- Per-site download rules and download-location picker.
- DASH (mpd) segment assembly beyond the current playlist handling; encrypted HLS (AES-128) via a key-fetch path if legally unencumbered use cases demand it.
- Extension-mediated blob capture once `tabs.sendMessage` coverage is confirmed across GeckoView trains (currently probed at runtime with an honest failure path).
