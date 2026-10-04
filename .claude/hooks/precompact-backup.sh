#!/usr/bin/env bash
# Lani PreCompact Hook
# Creates safety backup before conversation compaction.
# Delegates path resolution to lani_paths.py so paths match the Python hooks exactly.
set -euo pipefail

HOOKS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DATA_DIR="$(HOOKS_DIR="$HOOKS_DIR" python3 -c "
import os, sys
sys.path.insert(0, os.environ['HOOKS_DIR'])
from lani_paths import data_dir
print(data_dir())
")"

BACKUP_DIR="$DATA_DIR/.backups/precompact"
mkdir -p "$BACKUP_DIR"

if compgen -G "$DATA_DIR"/*.json > /dev/null; then
  find "$DATA_DIR" -maxdepth 1 -name "*.json" -exec cp {} "$BACKUP_DIR/" \;
  # Every other language's databases (languages/<code>/*.json), in the same layout.
  for dir in "$DATA_DIR"/languages/*/; do
    [ -d "$dir" ] || continue
    code="$(basename "$dir")"
    mkdir -p "$BACKUP_DIR/languages/$code"
    find "$dir" -maxdepth 1 -name "*.json" -exec cp {} "$BACKUP_DIR/languages/$code/" \;
  done
  echo "[Lani] 🔒 Pre-compact backup saved to $BACKUP_DIR"
fi
