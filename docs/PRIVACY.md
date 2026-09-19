# Privacy

## What Zerium G does and does not collect

**Short version: nothing.** No accounts, no analytics, no crash reporting, no remote config. The app makes network calls for exactly three things: the pages you visit, downloads you start, and nothing else. The blocking rules are bundled in the APK — no rule downloads exist in v1.0.0 (the extension reads `blocklist/rules.json` from its own assets).

## Blocking

- The Zerium G Shield extension runs inside the engine and blocks requests to known ad/tracker hosts and URL patterns. It reads no user data; it sees URLs because blocking requests requires seeing them.
- The site allowlist lives in the app's private storage and is pushed to the extension over an in-process native-messaging channel. It never leaves the device.
- Strict Enhanced Tracking Protection runs on every session, including private ones.

## Private tabs

Private tabs use GeckoView private sessions: cookies, cache and site storage exist only for the lifetime of the session and are not shared with regular tabs. Private tabs are excluded from history, bookmarks, previews and the session-restore snapshot. This is a stronger guarantee than the WebView edition can offer (documented there as its main engine limitation).

## Permissions

- **Content-permission prompts (location, camera, microphone, notifications) are denied by default** in v1.0.0 — Zerium G does not forward them to the user yet, so websites cannot obtain them. This is a deliberate privacy-first position, revisited on the roadmap with a proper per-site prompt UI.
- The app itself requests only `INTERNET` and `ACCESS_NETWORK_STATE`. It stores no passwords (`loginAutofillEnabled` is not enabled; no credential storage exists).

## Data handling

- History and bookmarks live in local SQLite databases inside the app sandbox; clearing is one tap and unrecoverable.
- Settings persist in `SharedPreferences` in the app sandbox.
- The session-restore snapshot (up to 10 URLs, private tabs excluded) is stored locally only and never synced anywhere.
