#!/bin/bash
# Installs the Quota collector as a LaunchAgent that runs every 180 s.
#
#   bash collector/install.sh                 # install and start
#   bash collector/install.sh --uninstall     # stop and remove
#
# State lives in ~/.quota (config.json, pairing.txt, collector.log), never in the repo.
set -euo pipefail

LABEL="com.gorisse.thomas.quota.collector"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/quota_collector.py"
STATE_DIR="$HOME/.quota"
LOG="$STATE_DIR/collector.log"
INTERVAL=180

if [[ "${1:-}" == "--uninstall" ]]; then
  launchctl bootout "gui/$UID/$LABEL" 2>/dev/null || true
  rm -f "$PLIST"
  echo "Uninstalled $LABEL (state kept in $STATE_DIR)."
  exit 0
fi

mkdir -p "$STATE_DIR" "$HOME/Library/LaunchAgents"
chmod 700 "$STATE_DIR"

# Pair on first run and print the pairing URL before the agent takes over.
/usr/bin/python3 "$SCRIPT" || echo "First run failed; the agent will retry every ${INTERVAL}s."

cat > "$PLIST" <<PLIST_EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/python3</string>
    <string>$SCRIPT</string>
  </array>
  <key>StartInterval</key><integer>$INTERVAL</integer>
  <key>RunAtLoad</key><true/>
  <key>StandardOutPath</key><string>$LOG</string>
  <key>StandardErrorPath</key><string>$LOG</string>
  <key>ProcessType</key><string>Background</string>
</dict>
</plist>
PLIST_EOF

launchctl bootout "gui/$UID/$LABEL" 2>/dev/null || true
launchctl bootstrap "gui/$UID" "$PLIST"
launchctl kickstart -k "gui/$UID/$LABEL"

echo "Installed $LABEL (every ${INTERVAL}s)."
echo "Log:     $LOG"
echo "Pairing: $STATE_DIR/pairing.txt"
