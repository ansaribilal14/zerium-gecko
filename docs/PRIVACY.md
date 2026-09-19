# Privacy

## What Zerium G does and does not collect

**Short version: nothing.** No accounts, no analytics, no crash reporting, no remote config. The app makes network calls for exactly these things: the pages you visit, downloads you start, search suggestions while you type on the start page, resolving a curated add-on to its signed package when you tap Add, and the add-on packages the engine downloads on your request. The blocking rules are bundled in the APK and refreshed only when you install an update (or rebuilt weekly in CI, which also only ships inside the APK).

## Blocking

- The Zerium G Shield extension runs inside the engine and blocks requests to known ad/tracker hosts and URL patterns. It reads no user data; it sees URLs because blocking requests requires seeing them.
- The site allowlist lives in the app's private storage and is pushed to the extension over an in-process native-messaging channel. It never leaves the device.
- Strict Enhanced Tracking Protection runs on every session, including private ones. The `PERMISSION_TRACKING` content permission is always denied — a site can never ask its way past it.
- Cookie-banner auto-rejection uses Gecko's built-in cookie-banner service in reject mode; it runs locally, in the engine.

## Add-ons

- The Add-ons screen installs **Mozilla-signed** WebExtensions only: the Gecko engine validates signatures and the check cannot be disabled by an embedder. Curated entries are resolved through the public addons.mozilla.org v5 API at the moment you tap Add; locally picked files must be signed `.xpi` packages. Chrome `.crx` or unsigned files are rejected by the engine and reported honestly.
- Install-time and runtime permission prompts show the exact permission list before anything is granted; the private-mode grant is a separate, explicit checkbox.
- An installed add-on can see the pages it declares host permissions for — the same trust decision as installing it in any Firefox-based browser. The built-in Shield cannot be uninstalled and is disabled-able from the same screen.

## Start-page suggestions and stats

- Search suggestions come from DuckDuckGo's suggestion endpoint (`duckduckgo.com/ac/`) and are requested **only while you type on the start page's search box** — nowhere else in the app, and never in the omnibox. This includes private tabs (which show the same start page): the request carries only what you have typed, and DuckDuckGo receives nothing else about the session.
- The Privacy Stats card computes two estimates on-device: ≈50 KB of data and ≈50 ms of time per blocked request (Brave's published conservative formula). The counters live in `SharedPreferences` on the device and never leave it.

## Private tabs

Private tabs use GeckoView private sessions: cookies, cache and site storage exist only for the lifetime of the session and are not shared with regular tabs. Private tabs are excluded from history, bookmarks, previews and the session-restore snapshot. Reader view works in private tabs, but the rendered article file is removed from the app cache when the reader closes.

## Permissions

- **Content permissions ask.** Location, notifications, persistent storage, storage access, XR and DRM (protected media) show a prompt; the user can allow or deny once, or remember the decision per site. Private tabs can decide per prompt but nothing is remembered. A settings switch changes the default to deny-silently for people who prefer zero prompts. Tracking permission and autoplay grants are **never** forwarded to sites, with or without prompts.
- **Camera and microphone** additionally go through the Android platform permission dialog before any website sees them; the app ships the `<uses-permission>` entries but requests them at runtime only when a site asks.
- **Password autofill** uses the Android platform autofill framework: GeckoView exposes its form fields to the system, and your chosen password manager (Bitwarden, KeePassDX, Proton Pass, …) fills them. **Zerium G stores no passwords and ships no credential storage** — nothing is added to what the platform autofill service already knows.
- Popups are blocked by default; sites can be allowed once or permanently from the popup prompt, or the feature can be switched off globally.

## Data handling

- History and bookmarks live in local SQLite databases inside the app sandbox; clearing is one tap and unrecoverable. **Delete browsing data** (menu and tab switcher) clears history, cookies, DOM storages, auth sessions and the image/network caches through the engine's `StorageController` in one dialog.
- Remembered content-permission decisions and the popup allowlist live in `SharedPreferences` in the app sandbox and can be cleared from Settings → Site permissions.
- The session-restore snapshot (up to 10 tabs with their full back/forward history, private tabs excluded) is stored locally only and never synced anywhere.
- Reader-mode articles are cached as a single HTML file in the app cache directory and deleted when the reader closes, when the tab closes, and when the app exits.
