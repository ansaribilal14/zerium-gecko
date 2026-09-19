# Roadmap

## v1.1 - near term

- Reader view (Mozilla Readability via a content-script channel).
- Content-permission prompt UI (per-site allow for location/camera/microphone) behind the privacy-first default.
- Password autofill via GeckoView's autocomplete/autofill delegates (no Zerium-side storage; delegate to system credential managers).
- Custom search engines (same model as the WebView edition).
- GeckoView session-state restore (`SessionState`) for in-history tab restoration across restarts.

## v1.2 - blocking depth

- Filter-list refresh channel for the shield (validated, atomic, same discipline as the WebView edition updater).
- uBlock Origin-class filter syntax support (exception rules, `$domain=`, resource types) inside the shield.
- Per-site cosmetic exceptions.

## v2.0 - differentiation

- uBlock Origin-compatible extension support (installable user extensions), which the Gecko platform makes possible.
- Background media playback controls.
- Sync evaluation (same honest framework as `docs/SYNC_EVALUATION.md` in the main repo).
