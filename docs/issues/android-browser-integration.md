# Android Browser Integration: Open With chooser + Default Browser role

> Tracked as the implementation shipped in v1.6.0. Kept here as the feature's
> spec of record (the repository token has no Issues scope to open it on GitHub).

## Summary
Make Zerium appear in Android's **Open with** chooser (WhatsApp/Telegram/Gmail
links) and let users set it as the default browser.

## Required implementation (implemented in v1.12.0)
- **A. Manifest**: `MainActivity` is exported with `ACTION_VIEW` + `DEFAULT` +
  `BROWSABLE` and `http`/`https` data schemes (verified present since the
  Spaces release).
- **B. Default browser setting**: Settings → **Links → Default browser**.
  On Android 10+ the row requests `RoleManager.ROLE_BROWSER` via
  `createRequestRoleIntent()`; result and `onResume` refresh the status text
  ("Zerium is your default browser" / "Set as default browser"). Below API 29
  and whenever the role is unavailable, the system default-apps screen opens
  with a hint toast. The app only ever *requests* the role — Android owns the dialog.
- **C. Incoming link handling**:
  - `handleIntent` validates the URL (http/https only) and opens it as a new
    tab in the space chosen under **Links → External links open in**:
    *Default space* (default) / *Current space* / *Ask every time* (space picker).
  - Works for cold start (`onCreate`) and warm start (`onNewIntent`), preserves
    existing tabs, and never lands a link in an isolated profile the user did
    not explicitly pick — the default space is the landing zone.
  - Shared-sheet text (`ACTION_SEND`) follows the same routing.

## Acceptance
- Zerium is offered in the link chooser of other apps and can be picked once or
  set default through the system dialog.
- With "Default space", a link tapped in another app opens with the default
  space's cookies, never an isolated space's.
