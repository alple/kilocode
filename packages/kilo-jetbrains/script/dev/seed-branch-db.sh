#!/usr/bin/env bash
# Seed the branch-suffixed CLI database from the release database so lux and sandbox builds see
# the real conversation history.
#
# Why: repo-CLI builds stamp their installation channel from the building branch
# (packages/script/src/index.ts falls back to `git branch --show-current`), and non-release
# channels get their own database (packages/opencode/src/storage/db.ts getChannelPath):
# kilo-<channel>.db. A freshly built lux or sandbox CLI therefore starts with empty history.
# The isolation is deliberate — dev schema changes must never touch kilo.db, so the stable
# plugin keeps working — and this script copies instead of sharing.
#
# The copy is a consistent SQLite snapshot (VACUUM INTO) taken over a read-only connection, so
# the source database and its WAL are never written. Re-running replaces the stale branch DB.
#
# Usage: script/dev/seed-branch-db.sh   (from packages/kilo-jetbrains/)
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(git -C "$script" rev-parse --show-toplevel)"

branch="$(git -C "$repo" branch --show-current)"
if [[ -z "$branch" ]]; then
  echo "error: detached HEAD has no branch; the CLI build could not have stamped a channel." >&2
  exit 1
fi

# Same sanitization as the CLI build's channel fallback (packages/script/src/index.ts).
channel="$(printf '%s' "$branch" | sed 's/[^0-9A-Za-z-]/-/g')"

case "$channel" in
  latest|beta|prod)
    echo "channel '$channel' shares kilo.db with release builds; nothing to seed."
    exit 0
    ;;
esac

data_root="${XDG_DATA_HOME:-$HOME/.local/share}/kilo"
source_db="$data_root/kilo.db"
target_db="$data_root/kilo-$channel.db"

if [[ ! -f "$source_db" ]]; then
  echo "error: no source database at $source_db" >&2
  exit 1
fi

command -v bun >/dev/null 2>&1 || { echo "error: bun is required for the snapshot copy." >&2; exit 1; }

# VACUUM INTO refuses an existing target; stale sidecars would corrupt a fresh copy too.
rm -f "$target_db" "$target_db-wal" "$target_db-shm"

SRC="$source_db" DST="$target_db" bun -e '
const { Database } = require("bun:sqlite")
const src = new Database(process.env.SRC, { readonly: true })
try {
  src.run("VACUUM INTO ?", [process.env.DST])
} finally {
  src.close()
}
'

echo "seeded $target_db from $source_db (channel $channel)"
echo "restart any running lux/sandbox session so the CLI reopens the refreshed database"
