#!/usr/bin/env bash
# Zerium G — release publisher.
#
# Responsibilities:
#   1. Compute SHA-256 checksums for the built APKs (SHA256SUMS.txt).
#   2. Generate structured release notes for two channels:
#        - rolling  -> the `latest` tag, recreated on every successful build of main
#        - stable   -> immutable `vX.Y.Z` tag, created whenever versionName changes
#   3. Publish both channels with the GitHub CLI (only when PUBLISH=1).
#
# Environment (provided by GitHub Actions):
#   GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_RUN_NUMBER
# Set PUBLISH=1 to actually publish; any other value performs a local dry run
# (notes + checksums are still generated, nothing is uploaded).
#
# Usage: publish_release.sh [artifact-dir]   (default: out, containing *.apk)
set -euo pipefail

cd "$(dirname "$0")/../.."

VER=$(grep -oP 'versionName "\K[^"]+' app/build.gradle)
FULL_SHA=$(git rev-parse HEAD)
BUILD_NO=${GITHUB_RUN_NUMBER:-dev}
STAMP=$(date -u +"%Y-%m-%d %H:%M UTC")
REPO_URL="https://github.com/${GITHUB_REPOSITORY:-ansaribilal14/zerium-browser}"

RELEASE_DIR=${1:-out}
mkdir -p out          # notes + checksums always live here (matches the CI layout)
mkdir -p "$RELEASE_DIR"

# ---------------------------------------------------------------------------
# 1. Checksums
# ---------------------------------------------------------------------------
cd "$RELEASE_DIR"
sha256sum *.apk > SHA256SUMS.txt
CHECKSUMS=$(cat SHA256SUMS.txt)
cd - >/dev/null

# ---------------------------------------------------------------------------
# 2. Changelog since the previous stable tag
# ---------------------------------------------------------------------------
PREV_TAG=$(git describe --tags --match 'v[0-9]*' --abbrev=0 "$FULL_SHA" 2>/dev/null || true)
if [ -n "$PREV_TAG" ] && [ "$PREV_TAG" != "v${VER}" ]; then
  LOG_RANGE=("${PREV_TAG}..${FULL_SHA}")
else
  LOG_RANGE=(--max-count=15)
fi
CHANGES=$(git log --no-merges --pretty=format:"- %s" "${LOG_RANGE[@]}")
if [ -z "$CHANGES" ]; then
  CHANGES="- No user-facing changes in this window; see the full commit history."
fi

# ---------------------------------------------------------------------------
# 3. Release notes (one file per channel)
# ---------------------------------------------------------------------------
gen_notes() { # $1 = channel: rolling|stable, $2 = output file
  local channel="$1" file="$2" blurb

  if [ "$channel" = "rolling" ]; then
    blurb="> **Rolling channel** — the \`latest\` tag is recreated on every successful build of \`main\`. For immutable, tagged stable releases, see the [release overview](${REPO_URL}/releases)."
  else
    blurb="> **Stable release** — immutable and permanently tied to the \`v{VER}\` tag. The same build is available on the rolling \`latest\` channel while it is the newest successful build of \`main\`."
  fi

  cat <<'TEMPLATE' | awk \
      -v ver="$VER"         -v repo="$REPO_URL" \
      -v stamp="$STAMP"     -v build="$BUILD_NO" -v blurb="$blurb" \
      -v changes="$CHANGES" -v sums="$CHECKSUMS" '
    { gsub(/\{BLURB\}/, blurb); gsub(/\{CHANGES\}/, changes); gsub(/\{CHECKSUMS\}/, sums) }
    { gsub(/\{VER\}/, ver); gsub(/\{REPO_URL\}/, repo) }
    { gsub(/\{STAMP\}/, stamp); gsub(/\{BUILD_NO\}/, build); print }
  ' > "$file"
**Zerium G v{VER}** — build #{BUILD_NO} · {STAMP}

{BLURB}

## What's changed

{CHANGES}

## Downloads

| File | Description |
|------|-------------|
| `Zerium-G-v{VER}-release.apk` | Signed release build — recommended for daily use |
| `Zerium-G-v{VER}-debug.apk` | Debug build — for development and testing |
| `SHA256SUMS.txt` | SHA-256 checksums for both APKs |

**Requirements:** Android 8.0+ (API 26) · universal APK (arm64-v8a, armeabi-v7a, x86, x86_64) · ~5 MB · no account or telemetry of any kind.

## Install

1. Download `Zerium-G-v{VER}-release.apk` from the assets below.
2. Open the file and allow installs from your browser or file manager when prompted.
3. Optional — verify integrity before installing: `sha256sum -c SHA256SUMS.txt`

## Verify integrity

```text
{CHECKSUMS}
```

## Known limitations

- Private tabs share no session data with regular tabs, but downloads and bookmarks storage follow Android platform behavior. Details: [docs/PRIVACY.md]({REPO_URL}/blob/main/docs/PRIVACY.md).
- First release: printing, password autofill and reader view are not yet wired on the Gecko engine; they are on the roadmap. Website content-permission prompts (location/camera/mic) are denied by default — see [docs/PRIVACY.md]({REPO_URL}/blob/main/docs/PRIVACY.md).
- Do Not Track / GPC headers apply to main-frame requests only.

The full, honest limitation list lives in the [README]({REPO_URL}#honest-limitations).

## Links

- Documentation: [docs/]({REPO_URL}/tree/main/docs)
- Changelog: [CHANGELOG.md]({REPO_URL}/blob/main/CHANGELOG.md)
- Issues: [{REPO_URL}/issues]({REPO_URL}/issues)
- License: GPL-3.0
TEMPLATE
}

gen_notes rolling out/RELEASE-NOTES-ROLLING.md
gen_notes stable  out/RELEASE-NOTES-STABLE.md
echo "Release notes generated: out/RELEASE-NOTES-ROLLING.md, out/RELEASE-NOTES-STABLE.md"

# ---------------------------------------------------------------------------
# 4. Publish
# ---------------------------------------------------------------------------
if [ "${PUBLISH:-0}" != "1" ]; then
  echo "PUBLISH != 1 -> dry run, nothing was uploaded."
  exit 0
fi

export GH_TOKEN

# --- Stable channel: immutable tag + release, only when the tag is new ------
if ! git ls-remote --tags origin "refs/tags/v${VER}" | grep -q "refs/tags/v${VER}"; then
  git config user.name  "zerium-release-bot"
  git config user.email "release-bot@zerium.invalid"
  git tag -a "v${VER}" -m "Zerium G v${VER}" "$FULL_SHA"
  git push origin "v${VER}"
  gh release create "v${VER}" \
    "out/Zerium-G-v${VER}-release.apk" \
    "out/Zerium-G-v${VER}-debug.apk" \
    "out/SHA256SUMS.txt" \
    --title "Zerium G v${VER}" \
    --notes-file out/RELEASE-NOTES-STABLE.md
  echo "Published stable release v${VER}."
else
  echo "Tag v${VER} already exists — stable release left unchanged."
fi

# --- Rolling channel: recreate `latest` -------------------------------------
gh release delete latest --yes --cleanup-tag 2>/dev/null || true
gh release create latest \
  "out/Zerium-G-v${VER}-release.apk" \
  "out/Zerium-G-v${VER}-debug.apk" \
  "out/SHA256SUMS.txt" \
  --target "$FULL_SHA" \
  --title "Zerium G v${VER} — Rolling Release" \
  --notes-file out/RELEASE-NOTES-ROLLING.md \
  --latest=false
echo "Published rolling release 'latest' (v${VER})."
