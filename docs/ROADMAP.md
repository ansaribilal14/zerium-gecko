# Roadmap

## v1.1 (shipped)

Everything listed below under "v1.1 - near term" landed in v1.1.0, together with printing, Save-as-PDF, popup blocking, cookie-banner auto-rejection, pull-to-refresh, edge-swipe gestures, an Adblock-syntax subset in the shield and the YouTube InnerTube pruning port. See `CHANGELOG.md`.

## v1.2 - blocking depth

- Filter-list refresh channel for the shield (validated, atomic, same discipline as the WebView edition updater) — in-app rule updates between releases.
- Procedural cosmetic selectors (`:has-text`, `:-abp-…`) and domain-scoped element hiding.
- Per-site cosmetic exceptions (`$generichide`/`$specifichide` honoring).
- Reader font-size and typography controls in-app.

## v2.0 - differentiation

- uBlock Origin-compatible extension support (installable user extensions), which the Gecko platform makes possible.
- Background media playback controls.
- Sync evaluation (same honest framework as `docs/SYNC_EVALUATION.md` in the main repo).
