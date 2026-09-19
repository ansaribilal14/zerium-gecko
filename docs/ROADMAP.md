# Roadmap

## v1.2 (shipped)

The add-on platform landed in v1.2.0: a full WebExtension manager (curated AMO catalog + local signed `.xpi`, enable/disable, private-mode grants, uninstall, install-time permission prompts), the Brave-style menu and start page, delete-browsing-data, tab search and HTTPS-only mode. See `CHANGELOG.md`.

## v1.3 (shipped)

- Add-on action popups are surfaced in the app: a puzzle-piece toolbar button appears when the active tab has browser/page actions, lists them with badges, and renders the add-on's popup UI in an anchored window (the app owns popup sessions per the GeckoView contract).

## v1.4 - blocking depth

- Filter-list refresh channel for the shield (validated, atomic, same discipline as the WebView edition updater) — in-app rule updates between releases.
- Procedural cosmetic selectors (`:has-text`, `:-abp-…`) and domain-scoped element hiding.
- Per-site cosmetic exceptions (`$generichide`/`$specifichide` honoring).
- Reader font-size and typography controls in-app.

## v2.0 - differentiation

- Background media playback controls.
- Sync evaluation (same honest framework as `docs/SYNC_EVALUATION.md` in the main repo).
