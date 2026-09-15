#!/bin/bash
# Rebuilds everything web/downloads/ serves: the collector zip, plus the TRMNL files.
#
#   bash tools/build-collector-zip.sh
#
# The zip is the only artefact the public download and the /install procedure fetch, so it
# is versioned: rebuild it in the same commit as any collector change. Timestamps come from
# the collector's last commit, which makes the archive byte-reproducible — rebuilding an
# unchanged collector produces no diff.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/web/downloads/quota-collector.zip"
FILES=(install.sh quota_collector.py)

STAMP="$(TZ=UTC git -C "$ROOT" log -1 --format=%cd --date=format-local:%Y%m%d%H%M.%S -- collector/ 2>/dev/null || true)"
[[ -n "$STAMP" ]] || STAMP="$(TZ=UTC date +%Y%m%d%H%M.%S)"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/collector"
for file in "${FILES[@]}"; do
  cp "$ROOT/collector/$file" "$STAGE/collector/$file"
  TZ=UTC touch -t "$STAMP" "$STAGE/collector/$file"
done

rm -f "$OUT"
# -X drops the extra attributes that would otherwise change on every machine.
(cd "$STAGE" && zip -q -X -9 "$OUT" "${FILES[@]/#/collector/}")

echo "Built $OUT"
unzip -l "$OUT"

# web/downloads/ is what the /install procedure curls, so the TRMNL files it fetches are
# mirrored from their source of truth here rather than copied by hand.
cp "$ROOT/trmnl/setup.sh" "$ROOT/web/downloads/trmnl-setup.sh"
cp "$ROOT/trmnl/quota.liquid" "$ROOT/web/downloads/quota.liquid"
echo "Mirrored trmnl/setup.sh and trmnl/quota.liquid into web/downloads/"
