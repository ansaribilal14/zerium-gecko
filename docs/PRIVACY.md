# Privacy

## What Zerium G does and does not collect

**Short version: nothing.** No accounts, no analytics, no crash reporting, no remote config. The app makes network calls for exactly three things: the pages you visit, downloads you start, and nothing else. The blocking rules are bundled in the APK and refreshed only when you install an update (or rebuild them weekly in CI, which also only ships inside the APK).

## Blocking

- The Zerium G Shield extension runs inside the engine and blocks requests to known ad/tracker hosts and URL patterns. It reads no user data; it sees URLs because blocking requests requires seeing them.
- The site allowlist lives in the app's private storage and is pushed to the extension over an in-process native-messaging channel. It never leaves the device.
- Strict Enhanced Tracking Protection runs on every session, including private ones. The `PERMISSION_TRACKING` content permission is always denied — a site can never ask its way past it.
- Cookie-banner auto-rejection uses Gecko's built-in cookie-banner service in reject mode; it runs locally, in the engine.

## Private tabs

Private tabs use GeckoView private sessions: cookies, cache and site storage exist only for the lifetime of the session and are not shared with regular tabs. Private tabs are excluded from history, bookmarks, previews and the session-restore snapshot. Reader view works in private tabs, but the rendered article file is removed from the app cache when the reader closes.

## Permissions

- **Content permissions ask.** Location, notifications, persistent storage, storage access, XR and DRM (protected media) show a prompt; the user can allow or deny once, or remember the decision per site. Private tabs can decide per prompt but nothing is remembered. A settings switch changes the default to deny-silently for people who prefer zero prompts. Tracking permission and autoplay grants are **never** forwarded to sites, with or without prompts.
- **Camera and microphone** additionally go through the Android platform permission dialog before any website sees them; the app ships the `<uses-permission>` entries but requests them at runtime only when a site asks.
- **Password autofill** uses the Android platform autofill framework: GeckoView exposes its form fields to the system, and your chosen password manager (Bitwarden, KeePassDX, Proton Pass, …) fills them. **Zerium G stores no passwords and ships no credential storage** — nothing is added to what the platform autofill service already knows.
- Popups are blocked by default; sites can be allowed once or permanently from the popup prompt, or the feature can be switched off globally.

## Data handling

- History and bookmarks live in local SQLite databases inside the app sandbox; clearing is one tap and unrecoverable.
- Remembered content-permission decisions and the popup allowlist live in `SharedPreferences` in the app sandbox and can be cleared from Settings → Site permissions.
- The session-restore snapshot (up to 10 tabs with their full back/forward history, private tabs excluded) is stored locally only and never synced anywhere.
- Reader-mode articles are cached as a single HTML file in the app cache directory and deleted when the reader closes, when the tab closes, and when the app exits.
