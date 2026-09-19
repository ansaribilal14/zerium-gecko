# Zerium G

**The GeckoView edition of [Zerium Browser](https://github.com/ansaribilal14/zerium-browser)** — a free and open-source privacy browser for Android built on Mozilla's Gecko engine, shipped as a standalone app in its own repository.

GPL-3.0 · no accounts · no analytics · no telemetry.

## Why a second browser?

Zerium Browser (the original) ships on the Android System WebView because it is small, fast and CI-buildable — but WebView hard-caps what a privacy browser can do. Zerium G moves to GeckoView to remove those caps entirely:

| Capability | Zerium (WebView) | Zerium G (GeckoView) |
|---|---|---|
| Ad/tracker blocking | `shouldInterceptRequest` (app-level) | **Engine-level** `webRequest` blocking + cosmetic CSS inside the engine |
| Tracking protection | none (WebView limit) | **Strict Enhanced Tracking Protection** built into Gecko |
| Private tabs | shares the WebView cookie jar (documented limitation) | **True private sessions** — separate storage, no persistence |
| Per-site JavaScript | kill-list with reload heuristics | **Per-session switch**, engine-native |
| Desktop site | user-agent swap + reload | **UA + viewport mode**, engine-native |
| Extensions | impossible | Possible (WebExtensions platform) |

Both apps keep the same honest-scope discipline: everything documented here is in the code, and limitations are stated plainly.

## Features

- **Engine-level blocking (Zerium G Shield)** — a built-in WebExtension cancels requests to ~80,000 known ad/tracker/malware hosts (StevenBlack unified hosts list, MIT) plus 84 curated URL patterns *inside the Gecko engine*, before the page ever sees them. Site allowlist applies live through native messaging; every block is counted.
- **Cosmetic filtering** — 649 element-hiding rules (the Zerium Browser sanitized EasyList generic-hide subset, CC-BY-SA-3.0) applied as engine content CSS at document start, so ad containers never paint.
- **Strict ETP** — Gecko's Enhanced Tracking Protection at the strict level: social trackers, fingerprinters, cryptominers and email trackers.
- **True private tabs** — GeckoView private sessions with their own transient storage. Nothing about a private tab lands in history, bookmarks or the session snapshot.
- **Per-tab JavaScript and Desktop-site toggles** — engine-native session settings, applied live, with reload.
- **Tabs with live previews** — compositor-captured screenshots (never for private tabs), grid switcher, close-all.
- **Find in page** — the engine Finder API with a live counter.
- **Downloads** — Content-Disposition downloads saved into the public Downloads collection; long-press image downloads run through the extension's `browser.downloads` API.
- **Context menus** — long-press links and images: open in new tab, copy, share, download.
- **HTTPS everywhere the site allows, honest error pages** — certificate and network failures render an explicit error page; nothing is bypassed silently.
- **Page color scheme** — force light/dark rendering for sites via the engine's preferred-color-scheme (dark sites respond properly).
- **Bookmarks & history** — local SQLite only; one-tap clearing.
- **Material 3 soft UI** — the same tonal, rounded design language as the WebView edition, with Material You dynamic color on Android 12+.

## Honest limitations (v1.0.0)

- **Website content-permission prompts (location, camera, microphone, notifications) are denied by default** — a deliberate privacy-first position for the first release, documented in `docs/PRIVACY.md`.
- **Printing and Save-as-PDF are not wired yet** — GeckoView exposes no print framework bridge in the versions this tracks; roadmap.
- **Reader view is not shipped yet** — content script plumbing; roadmap.
- **Password autofill is not wired yet** — GeckoView's autofill delegate integration; roadmap. Zerium G stores no passwords.
- Blocking uses hosts-file semantics plus curated patterns — it is not a full filter-list DSL; uBlock Origin-class filter syntax requires the extension platform work listed on the roadmap.
- The YouTube suppression layer of the WebView edition is **not** ported; on Gecko the network-level blocking + ETP apply, and a dedicated Gecko-side YouTube scriptlet is on the roadmap.

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

CI builds, signs (via the `KEYSTORE_B64` secret), checksums and publishes both channels on every push to `main`.

## Repository layout

```
app/src/main/java/com/zerium/gecko/   Browser UI, tabs, delegates, storage
app/src/main/assets/extension/        Zerium G Shield (built-in WebExtension):
  manifest.json, background.js        engine-level webRequest blocking + native messaging
  blocklist/rules.json                hosts + URL patterns (generated, reproducible)
  content/cosmetic.css                element-hiding rules
docs/                                 Privacy, blocking scope, roadmap
.github/workflows/build.yml           CI: build, sign, checksums, two-channel release
```

## License

GPL-3.0, same as the Zerium Browser project. Third-party: Mozilla GeckoView (MPL-2.0), StevenBlack hosts (MIT), EasyList-derived cosmetic selectors (CC-BY-SA-3.0) — see `docs/BLOCKING.md` and the in-repo attributions.
