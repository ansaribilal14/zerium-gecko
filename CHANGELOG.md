# Changelog

All notable changes to Zerium G are documented in this file (Keep a Changelog format). Stable builds are published as immutable `vX.Y.Z` releases; the rolling `latest` tag tracks the newest successful build of `main`.

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
