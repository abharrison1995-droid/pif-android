#!/bin/bash
# Copies a game folder to the device over USB (adb). Only changed files are
# sent on later runs, so this is also how to apply a game update.
#
#   scripts/push-game.sh /path/to/InfiniteFusion-game-folder
set -euo pipefail

SRC="${1:?usage: scripts/push-game.sh <game folder containing Game.ini>}"
DEST="/storage/emulated/0/InfiniteFusion"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"

[[ -f "$SRC/Game.ini" ]] || { echo "No Game.ini in $SRC" >&2; exit 1; }

"$ADB" shell mkdir -p "$DEST"
for entry in Audio Data Fonts Graphics Game.ini; do
  [[ -e "$SRC/$entry" ]] || continue
  echo "Pushing $entry..."
  "$ADB" push --sync "$SRC/$entry" "$DEST/"
done
echo "Done. Game folder: $DEST"
