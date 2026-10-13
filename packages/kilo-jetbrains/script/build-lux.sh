#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<EOF
Usage: $0 [lux-number] [base-version]

Builds a local "lux" JetBrains plugin (unsigned, unverified) and records the build as a
git tag jetbrains-lux/v<base>-lux<lux-number>, pushed to origin.

The lux number is resolved from the existing jetbrains-lux/v* tags on origin, so builds
track across machines and clones. Lux builds install side-by-side with published releases
under a separate plugin id (ai.kilocode.jetbrains.lux / "Kilo Code (lux)") and never touch
the jetbrains/v* release tags that the release workflow owns.

Lux builds always bundle the repo-built CLI (kilo.cli.pinned=false): the plugin ships a
CLI compiled from this worktree, and the plugin forces the shared official Kilo Code
database at runtime (KILO_DISABLE_CHANNEL_DB, see KiloBackendCliManager), so bundled dev
CLIs never fork session history into a branch-scoped database. Run the jetbrains-cli-pin
skill's unpin flow first so the staged CLI is fresh:
bun .kilo/skills/jetbrains-cli-pin/script/cli-pin.ts unpin

Arguments:
  lux-number    Optional explicit lux counter (default: highest existing + 1, first is 1).
  base-version  Optional stable base version x.y.z (default: latest stable jetbrains/v* tag on origin).

Examples:
  $0                       # next lux build on the latest stable release
  $0 2 7.1.8               # rebuild a specific counter on a specific stable base
EOF
}

if [[ $# -gt 2 ]] || [[ $# -ge 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]] && exit 0 || exit 1
fi

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
plugin="$(cd "${script}/.." && pwd)"
cd "$plugin"

if [[ $# -ge 2 ]]; then
  base="${2#v}"
else
  # Lux builds track the latest stable jetbrains/v* release on origin, never an RC or the
  # local dev version. Stable means a plain x.y.z tag (no -rc.n prerelease).
  base="$(git ls-remote --tags origin 'refs/tags/jetbrains/v*' \
    | sed 's|.*refs/tags/jetbrains/v||; s|\^.*$||' \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' \
    | sort -uV | tail -1)"
  if [[ -z "$base" ]]; then
    echo "No stable jetbrains/v* tags found on origin; pass an explicit stable base version." >&2
    exit 1
  fi
fi

if [[ ! "$base" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Lux builds target stable releases only; '$base' is not a stable x.y.z base (rc builds are not eligible)." >&2
  exit 1
fi

prefix="refs/tags/jetbrains-lux/v${base}-lux"
remote="$(git ls-remote --tags origin "${prefix}*")"
if [[ -z "$remote" && $# -eq 0 ]]; then
  next=1
else
  highest=0
  while read -r sha ref; do
    n="${ref#"$prefix"}"
    [[ "$n" =~ ^[0-9]+$ ]] || continue
    (( n > highest )) && highest="$n"
  done <<<"$remote"
  next=$(( highest + 1 ))
fi

if [[ $# -ge 1 ]]; then
  if [[ ! "$1" =~ ^[0-9]+$ ]]; then
    echo "Lux number must be a plain integer, got '$1'." >&2
    exit 1
  fi
  next="$1"
fi

version="${base}-lux${next}"
tag="jetbrains-lux/v${version}"

if git ls-remote --exit-code --tags origin "$tag" >/dev/null 2>&1; then
  echo "Tag $tag already exists on origin; refusing to overwrite it." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Warning: the working tree has uncommitted changes; the tag records the last commit only." >&2
fi

# Lux builds bundle the repo-built CLI, which requires the unpinned state. A pinned
# worktree would silently ship the released CLI instead (the lux2 vs lux4/5 history fork).
if grep -Eq '^[[:space:]]*kilo\.cli\.pinned[[:space:]]*=[[:space:]]*true[[:space:]]*$' "$plugin/gradle.properties"; then
  echo "Lux builds bundle the repo-built CLI, but this worktree is pinned (kilo.cli.pinned=true)." >&2
  echo "Run: bun .kilo/skills/jetbrains-cli-pin/script/cli-pin.ts unpin" >&2
  exit 1
fi

root="$(git -C "$plugin" rev-parse --show-toplevel)"
if [[ -z "$(find "$root/packages/opencode/dist" -type f -name kilo 2>/dev/null)" ]]; then
  echo "Warning: no repo CLI binary under packages/opencode/dist; the Gradle build fails until you run: bun .kilo/skills/jetbrains-cli-pin/script/cli-pin.ts unpin" >&2
elif [[ -n "$(find "$root/packages/opencode/src" "$root/packages/core/src" -type f -newer "$root/packages/opencode/dist" -print -quit 2>/dev/null)" ]]; then
  echo "Warning: CLI sources changed after the last repo CLI build; run 'cli-pin.ts regen' so the bundle is fresh." >&2
fi

echo "Building $version (lux build #$next for base $base) ..."
./script/build-version.sh "$version" --skip-signing --skip-verification

if git rev-parse -q --verify "$tag" >/dev/null 2>&1; then
  echo "Local tag $tag already exists; refusing to recreate it." >&2
  exit 1
fi

git tag "$tag"
git push origin "$tag"

zip="$(find /tmp/kilo/jetbrains-build build -type f -name "*-${version}.zip" 2>/dev/null | sort | head -1 || true)"
if [[ -z "$zip" ]]; then
  echo "No ZIP matching *-${version}.zip was produced." >&2
  exit 1
fi

# Drop the artifact at the repo root so it is one `ls` away instead of buried in the
# (machine-locally relocated) Gradle build directory.
dest="$(git rev-parse --show-toplevel)/kilo.jetbrains-${version}.zip"
cp "$zip" "$dest"

printf '\nBuilt %s\nTagged and pushed %s (commit %s)\nZIP: %s\n' \
  "$version" "$tag" "$(git rev-parse --short HEAD)" "$dest"
