#!/usr/bin/env bash
# Usage: tests/client/run-client.sh <fabric|neoforge> [seconds]
#
# Starts the dev client (Gradle runClient) straight into a singleplayer world with
# -Dmcmt.autotest=<seconds>. The mod's AutoTest creates load, ticks, logs a RESULT line and
# closes the client. This covers the integrated server running next to the client threads,
# which the dedicated-server smoke test cannot. Exit 0 == PASS.
set -uo pipefail
LOADER="${1:?usage: run-client.sh <fabric|neoforge> [seconds]}"
SECONDS_TO_RUN="${2:-60}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/tests/client/out"; mkdir -p "$OUT"
CLIENT_DIR="$ROOT/$LOADER/runs/client"
SERVER_DIR="$ROOT/$LOADER/runs/server"
TEMPLATE="$ROOT/tests/client/work/$LOADER-template-world"
WORLD_NAME="mcmt-smoke"
REPORT="$OUT/$LOADER-report.txt"
STDOUT="$OUT/$LOADER-stdout.log"
: > "$REPORT"

fail() { echo "FAIL: $*" | tee -a "$REPORT"; [ -n "${GRADLE_PID:-}" ] && kill "$GRADLE_PID" 2>/dev/null; exit 1; }

# 1. World template: generated once per loader by a dedicated server, then reused.
if [ ! -f "$TEMPLATE/level.dat" ]; then
  echo "generating world template" | tee -a "$REPORT"
  rm -rf "$SERVER_DIR/world" "$SERVER_DIR/logs"; mkdir -p "$SERVER_DIR"
  echo "eula=true" > "$SERVER_DIR/eula.txt"
  {
    echo 'online-mode=false'
    echo 'level-seed=mcmt-smoke'
    echo 'level-type=minecraft\:flat'
    echo 'generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:126},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains"}'
    echo 'generate-structures=false'
    echo 'spawn-protection=0'
  } > "$SERVER_DIR/server.properties"
  CMD="$OUT/$LOADER-gen-stdin.txt"; : > "$CMD"
  cd "$ROOT"
  ( tail -f "$CMD" ) | ./gradlew ":$LOADER:runServer" --no-daemon --console=plain --quiet > "$OUT/$LOADER-gen.log" 2>&1 &
  GEN_PID=$!
  for i in $(seq 1 600); do grep -q 'Done (' "$OUT/$LOADER-gen.log" 2>/dev/null && break; kill -0 $GEN_PID 2>/dev/null || fail "template server exited early"; sleep 1; done
  echo "stop" >> "$CMD"
  for i in $(seq 1 120); do kill -0 $GEN_PID 2>/dev/null || break; sleep 1; done
  pkill -f "tail -f $CMD" 2>/dev/null
  [ -f "$SERVER_DIR/world/level.dat" ] || fail "template world was not created"
  mkdir -p "$(dirname "$TEMPLATE")"; rm -rf "$TEMPLATE"; cp -r "$SERVER_DIR/world" "$TEMPLATE"
fi

# 2. Fresh client game directory state.
mkdir -p "$CLIENT_DIR/saves"
rm -rf "$CLIENT_DIR/saves/$WORLD_NAME" "$CLIENT_DIR/logs" "$STDOUT"
cp -r "$TEMPLATE" "$CLIENT_DIR/saves/$WORLD_NAME"
CRASHES_BEFORE="$(ls "$CLIENT_DIR/crash-reports" 2>/dev/null | wc -l)"
# The game must keep ticking while the window is unfocused; no first-run screens, no audio.
cat > "$CLIENT_DIR/options.txt" <<'EOF'
version:3955
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
joinedFirstServer:true
narrator:0
renderDistance:6
simulationDistance:6
maxFps:30
enableVsync:false
fullscreen:false
soundCategory_master:0.0
soundCategory_music:0.0
EOF

# 3. Launch.
cd "$ROOT"
echo "launching $LOADER client for ${SECONDS_TO_RUN}s" | tee -a "$REPORT"
timeout $((SECONDS_TO_RUN + 600)) ./gradlew ":$LOADER:runClient" -PmcmtWorld="$WORLD_NAME" -PmcmtAutotest="$SECONDS_TO_RUN" \
  --no-daemon --console=plain --quiet > "$STDOUT" 2>&1 &
GRADLE_PID=$!
wait "$GRADLE_PID"; RC=$?
GRADLE_PID=""
echo "client process exit code $RC" >> "$REPORT"

# 4. Verdict from the log.
LOG="$CLIENT_DIR/logs/latest.log"
[ -f "$LOG" ] || fail "no client log written"
cp "$LOG" "$OUT/$LOADER-latest.log"
CRASHES_AFTER="$(ls "$CLIENT_DIR/crash-reports" 2>/dev/null | wc -l)"
[ "$CRASHES_AFTER" -gt "$CRASHES_BEFORE" ] && fail "client wrote a crash report ($CLIENT_DIR/crash-reports)"
grep -q 'MCMT-AUTOTEST enabled' "$LOG" || fail "autotest never started (world did not open?)"
RESULT="$(grep -o 'MCMT-AUTOTEST RESULT .*' "$LOG" | tail -1)"
[ -n "$RESULT" ] || fail "no RESULT line; see $OUT/$LOADER-latest.log"
echo "$RESULT" >> "$REPORT"
for k in World Entity TE Env; do
  v="$(echo "$RESULT" | grep -oE "$k:[0-9]+" | cut -d: -f2)"
  [ "${v:-0}" -gt 0 ] || fail "no $k ticks ran on the worker pool"
done
echo "$RESULT" | grep -q 'players=1' || fail "local player was not on the integrated server"
grep -q 'MCMT-AUTOTEST shutting down' "$LOG" || fail "autotest did not request shutdown"
[ "$RC" -eq 0 ] || fail "client exited with code $RC"

grep -v '^#' "$ROOT/tests/client/allowlist.txt" | grep -v '^\s*$' > "$OUT/$LOADER-allow.re"
grep -vE -f "$OUT/$LOADER-allow.re" "$LOG" > "$OUT/$LOADER-filtered.log"
PATTERN='Exception|/ERROR\]|ERROR\]|FATAL|Mixin apply failed|parallel task failed|Watchdog'
if grep -qE "$PATTERN" "$OUT/$LOADER-filtered.log"; then
  echo "--- offending lines ---" >> "$REPORT"
  grep -nE "$PATTERN" "$OUT/$LOADER-filtered.log" | head -40 >> "$REPORT"
  fail "errors in client log (see $REPORT)"
fi
echo "PASS" | tee -a "$REPORT"
