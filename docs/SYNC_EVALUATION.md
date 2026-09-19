# Sync: evaluation and current position

*"Sync" was proposed as a Zerium feature. This document records the honest
assessment: what sync would actually require, what was evaluated, and why
nothing was shipped. No sync feature exists in Zerium today, and nothing in
the app sends data to a Zerium server — this document explains why that
position is currently the honest one.*

## What "sync" means for a browser

Competitors ship one or more of:

| Capability | Examples | What it actually needs |
|---|---|---|
| Bookmarks/history sync across devices | Firefox Sync, Chrome sync | An account system, server storage, end-to-end encryption, conflict resolution, a per-platform client |
| Send tab to device | Chrome, Firefox | The same sync infrastructure, plus push channels |
| Password sync | Chrome, Firefox, Brave Sync v2 | Credential storage with E2E encryption and account recovery UX |
| Settings/allowlist sync | Brave Sync (chain of QR codes) | Encrypted group-key distribution without accounts (self-hostable in Brave's case) |

Every one of these requires a **server**. There is no honest, useful
browser sync that is fully offline and serverless — "sync" without a
transport is just backup/restore.

## Options evaluated

1. **Firefox Sync protocol (client implementation only).**
   Mozilla's syncserver is open source and self-hostable, and the protocol
   (OAuth token + FxA + encrypted records) is documented. Cost: the client
   implementation in Android Java is substantial (token flow, crypto
   [ Hawk / HKDF / AES-256-CBC record encryption ], storage-engine
   collections). Reimplementing it outside Mozilla Android Components
   (Kotlin) is months of work with high breakage risk; using Mozilla
   Android Components pulls a large Kotlin dependency tree into a GPL-3.0
   Java-only project and couples Zerium to Mozilla's release train.
   Verdict: not feasible without becoming a different project.

2. **Brave-style sync chain (no accounts, QR-paired, self-hostable server).**
   Brave publishes both client and sync server code. Still requires running
   a server for Zerium users — which the project does not have — and a
   pairing UX. Verdict: the most privacy-compatible model, but blocked on
   infrastructure Zerium does not operate.

3. **A Zerium-hosted sync service.**
   Would require: account or device-pairing system, storage, maintenance,
   abuse handling, a privacy policy for stored data, and legal exposure.
   This directly conflicts with the project's stated architecture ("no
   accounts, no analytics") and its zero-backend reality. Shipping this
   half-way would be exactly the kind of overclaiming this project avoids.

4. **Local export/import (the honest subset that needs no server).**
   Bookmarks, history, the blocking allowlist, per-site settings and search
   engines can be exported to a file the user moves themselves (or restores
   after reinstalling). This delivers most of the practical value of sync
   for a single-device privacy browser with zero network trust. **This is
   the only variant that fits Zerium today** and remains the near-term
   candidate on the roadmap.

## Current position

- No sync feature ships. No Zerium-run server exists. The app makes no
  sync-related network requests, and nothing in the UI suggests otherwise.
- The roadmap lists **local export/import of user data** as the honest
  step that requires no backend; true cross-device sync stays parked until
  the project either self-hosts an infrastructure budget or adopts an
  existing protocol wholesale (option 1) — both are architecture-level
  decisions documented in `docs/ARCHITECTURE.md` and `ROADMAP.md`.
