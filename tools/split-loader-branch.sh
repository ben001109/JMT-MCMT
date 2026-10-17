#!/usr/bin/env bash
# Generates the read-only single-loader branch <mcversion>-<loader> from the current checkout.
#
#   tools/split-loader-branch.sh <fabric|neoforge> [--push]
#
# The generated tree keeps common/, <loader>/, buildSrc/, the root Gradle files, tests, docs and
# LICENCE, with settings.gradle and build.gradle trimmed to that loader so it builds standalone.
# History is not carried over: each run produces one commit whose message names the source
# commit on the development branch. With --push the branch is force-pushed to origin.
set -euo pipefail
LOADER="${1:?usage: split-loader-branch.sh <fabric|neoforge> [--push]}"
PUSH="${2:-}"
case "$LOADER" in fabric|neoforge) ;; *) echo "unknown loader $LOADER" >&2; exit 2;; esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MC_VERSION="$(sed -nE 's/^minecraft_version=(.*)$/\1/p' gradle.properties | tr -d '\r')"
SRC_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
SRC_SHA="$(git rev-parse --short HEAD)"
TARGET="${MC_VERSION}-${LOADER}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Generating $TARGET from $SRC_BRANCH@$SRC_SHA"
mkdir -p "$WORK/tree"
cp -r common buildSrc "$LOADER" gradle tests docs gradlew gradlew.bat gradle.properties LICENCE .gitignore .gitattributes "$WORK/tree/"
rm -rf "$WORK/tree/tests/smoke/bot/node_modules" "$WORK/tree/common/build" "$WORK/tree/$LOADER/build" "$WORK/tree/$LOADER/runs" "$WORK/tree/buildSrc/build" "$WORK/tree/buildSrc/.gradle"

# settings.gradle: only this loader
sed -E "/^include\('(fabric|neoforge)'\)$/d" settings.gradle > "$WORK/tree/settings.gradle"
echo "include('$LOADER')" >> "$WORK/tree/settings.gradle"

# build.gradle: buildAll only depends on this loader
sed -E "s/dependsOn\(':fabric:build', ':neoforge:build'\)/dependsOn(':$LOADER:build')/" build.gradle > "$WORK/tree/build.gradle"

cat > "$WORK/tree/README.md" <<EOF
# MCMT for Minecraft $MC_VERSION ($LOADER only)

**Generated branch. Do not commit here.** This is the \`$LOADER\` slice of the
\`$SRC_BRANCH\` development branch at commit \`$SRC_SHA\`, produced by
\`tools/split-loader-branch.sh\`. Fixes and pull requests go to \`$SRC_BRANCH\`.

Build:

    ./gradlew buildAll

The jar lands in \`$LOADER/build/libs/\`. Java 21 is required.
EOF

(
  cd "$WORK/tree"
  git init -q -b "$TARGET"
  git -c user.name="mcmt-ci" -c user.email="mcmt-ci@users.noreply.github.com" add -A
  git -c user.name="mcmt-ci" -c user.email="mcmt-ci@users.noreply.github.com" commit -q -m "Generated $TARGET from $SRC_BRANCH@$SRC_SHA"
)

git fetch -q "$WORK/tree" "$TARGET"
git branch -f "$TARGET" FETCH_HEAD
echo "Local branch $TARGET updated ($(git rev-parse --short "$TARGET"))"

if [ "$PUSH" = "--push" ]; then
  git push -f -q origin "$TARGET"
  echo "Pushed origin/$TARGET"
fi
