#!/usr/bin/env bash
#
# Build the release bundle and push it to a Google Play track.
#
#   android/scripts/sync-play.sh                       # internal track, next versionCode
#   android/scripts/sync-play.sh --dry-run             # check auth + versionCode, upload nothing
#   android/scripts/sync-play.sh --draft               # first upload on a never-published app
#   android/scripts/sync-play.sh --version-code 7
#
# Modelled on ar-model-viewer's store/scripts/sync-play.py: stdlib Python plus the system
# openssl for the JWT, so there is nothing to pip install. The API half lives in
# play_upload.py next door; this file owns the build and the refusals.
#
# It will not run on a dirty tree, and it will not upload a versionCode Play has already
# seen. Promotion beyond `internal` stays a click in the console, deliberately.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(dirname "$SCRIPT_DIR")"
REPO_DIR="$(dirname "$ANDROID_DIR")"

PACKAGE="${PLAY_PACKAGE_NAME:-com.gorisse.thomas.quota}"
TRACK="internal"
VERSION_CODE=""
DRAFT=()
DRY_RUN=()
SKIP_BUILD=""
ALLOW_DIRTY=""

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --track)        TRACK="${2:?--track needs a value}"; shift 2 ;;
    --version-code) VERSION_CODE="${2:?--version-code needs a value}"; shift 2 ;;
    --package)      PACKAGE="${2:?--package needs a value}"; shift 2 ;;
    --draft)        DRAFT=(--draft); shift ;;
    --dry-run)      DRY_RUN=(--dry-run); shift ;;
    --skip-build)   SKIP_BUILD=1; shift ;;
    --allow-dirty)  ALLOW_DIRTY=1; shift ;;   # escape hatch, never in CI
    -h|--help)      awk 'NR>1{if(/^#/){sub(/^# ?/,"");print}else exit}' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)              die "unknown argument: $1" ;;
  esac
done

case "$TRACK" in
  internal) ;;
  alpha|beta|production)
    die "this script only ships to 'internal'. Promoting to '$TRACK' is a deliberate
  click in the Play Console — an app that reads your usage limits should not reach
  production from a shell script." ;;
  *) die "unknown track '$TRACK'" ;;
esac

# --- refusal 1: a dirty tree ------------------------------------------------------------
# What ships must be exactly what is committed, or the tag means nothing.
if [[ -z "$ALLOW_DIRTY" ]]; then
  if ! git -C "$REPO_DIR" diff --quiet HEAD -- || \
     [[ -n "$(git -C "$REPO_DIR" ls-files --others --exclude-standard -- "$ANDROID_DIR")" ]]; then
    git -C "$REPO_DIR" status --short
    die "working tree is dirty. Commit or stash first (--allow-dirty to override locally)."
  fi
fi

# --- versionCode ------------------------------------------------------------------------
# Default 1, matching app/build.gradle.kts; bump explicitly, Play never forgets a code.
if [[ -z "$VERSION_CODE" ]]; then
  VERSION_CODE="${VERSION_CODE_OVERRIDE:-1}"
fi
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || die "versionCode must be an integer, got '$VERSION_CODE'"

AAB="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"

# --- build ------------------------------------------------------------------------------
if [[ -z "$SKIP_BUILD" ]]; then
  echo "building :app:bundleRelease (versionCode=$VERSION_CODE)"
  ( cd "$ANDROID_DIR" && ./gradlew --quiet :app:bundleRelease -PVERSION_CODE="$VERSION_CODE" )
fi
[[ -f "$AAB" ]] || die "no AAB at $AAB (drop --skip-build?)"

# --- refusal 2: an unsigned bundle ------------------------------------------------------
# Play rejects an unsigned upload with a vague error; catch it here where the cause is
# obvious (the four QUOTA_UPLOAD_* properties are missing from ~/.gradle/gradle.properties).
if ! unzip -l "$AAB" | grep -q 'META-INF/.*\.\(RSA\|DSA\|EC\)$'; then
  die "the AAB is not signed — the release build fell back to unsigned.
  Set QUOTA_UPLOAD_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD in
  ~/.gradle/gradle.properties. See android/docs/release.md."
fi

exec python3 "$SCRIPT_DIR/play_upload.py" \
  --aab "$AAB" \
  --package "$PACKAGE" \
  --track "$TRACK" \
  --version-code "$VERSION_CODE" \
  "${DRAFT[@]+"${DRAFT[@]}"}" "${DRY_RUN[@]+"${DRY_RUN[@]}"}"
