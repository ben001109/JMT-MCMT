#!/usr/bin/env bash
# Usage: tests/smoke/run-smoke.sh <fabric|neoforge> [tick-seconds]
# Starts a headless dedicated server through Gradle, feeds tests/smoke/commands.txt over
# stdin, stops the server and scans the output for errors. Exit 0 == PASS.
set -uo pipefail
LOADER="${1:?usage: run-smoke.sh <fabric|neoforge> [tick-seconds]}"
TICK_SECONDS="${2:-120}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/tests/smoke/out"; mkdir -p "$OUT"
RUNDIR="$ROOT/$LOADER/runs/server"
REPORT="$OUT/$LOADER-report.txt"
STDOUT="$OUT/$LOADER-stdout.log"
CMDFILE="$OUT/$LOADER-stdin.txt"

rm -rf "$RUNDIR/world" "$RUNDIR/logs" "$STDOUT" "$CMDFILE"
mkdir -p "$RUNDIR"
echo "eula=true" > "$RUNDIR/eula.txt"
cat > "$RUNDIR/server.properties" <<EOF
online-mode=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=60000
level-seed=mcmt-smoke
level-type=minecraft\\:flat
generator-settings={"layers"\\:[{"block"\\:"minecraft\\:bedrock","height"\\:1},{"block"\\:"minecraft\\:stone","height"\\:126},{"block"\\:"minecraft\\:grass_block","height"\\:1}],"biome"\\:"minecraft\\:plains"}
generate-structures=false
sync-chunk-writes=false
EOF
: > "$REPORT"

fail() {
  echo "FAIL: $*" | tee -a "$REPORT"
  kill "$GRADLE_PID" 2>/dev/null
  exit 1
}

# Commands are streamed through a background writer so the server's stdin stays open.
: > "$CMDFILE"
cd "$ROOT"
( tail -f "$CMDFILE" & echo $! > "$OUT/$LOADER-tail.pid"; wait ) | ./gradlew ":$LOADER:runServer" --no-daemon --console=plain --quiet > "$STDOUT" 2>&1 &
GRADLE_PID=$!
TAIL_PID="$(cat "$OUT/$LOADER-tail.pid" 2>/dev/null || true)"

send() { echo "$1" >> "$CMDFILE"; }

for i in $(seq 1 600); do
  grep -q 'Done (' "$STDOUT" 2>/dev/null && break
  kill -0 "$GRADLE_PID" 2>/dev/null || fail "server process exited before Done (see $STDOUT)"
  sleep 1
done
grep -q 'Done (' "$STDOUT" || fail "server did not reach Done within 600s"
echo "server started" >> "$REPORT"

# Player bot: environment ticks and entity tracking only run with a player nearby.
BOT_DIR="$ROOT/tests/smoke/bot"
if [ ! -d "$BOT_DIR/node_modules" ]; then (cd "$BOT_DIR" && npm ci --no-audit --no-fund --silent) || fail "npm ci for the smoke bot failed"; fi
(cd "$BOT_DIR" && node bot.js --host 127.0.0.1 --port 25565 --seconds $((TICK_SECONDS + 3)) --name MCMTBot > "$OUT/$LOADER-bot.log" 2>&1) &
BOT_PID=$!
for i in $(seq 1 90); do
  grep -q 'MCMTBot joined the game' "$STDOUT" 2>/dev/null && break
  kill -0 "$BOT_PID" 2>/dev/null || fail "bot process exited before joining (see $OUT/$LOADER-bot.log)"
  sleep 1
done
grep -q 'MCMTBot joined the game' "$STDOUT" || fail "bot did not join within 90s (see $OUT/$LOADER-bot.log)"
echo "bot joined" >> "$REPORT"

while IFS= read -r line || [ -n "$line" ]; do
  [[ -z "$line" || "$line" == \#* ]] && continue
  if [[ "$line" == sleep\ * ]]; then
    secs="${line#sleep }"; [[ "$secs" == "120" ]] && secs="$TICK_SECONDS"; sleep "$secs"
  elif [[ "$line" == repeat\ * ]]; then
    n="$(echo "$line" | awk '{print $2}')"; cmd="$(echo "$line" | cut -d' ' -f3-)"
    for _ in $(seq 1 "$n"); do send "$cmd"; done
  else
    send "$line"
  fi
done < "$ROOT/tests/smoke/commands.txt"

for i in $(seq 1 90); do kill -0 "$GRADLE_PID" 2>/dev/null || break; sleep 1; done
kill -0 "$GRADLE_PID" 2>/dev/null && fail "server did not stop within 90s after /stop"
for i in $(seq 1 30); do kill -0 "$BOT_PID" 2>/dev/null || break; sleep 1; done
kill "$BOT_PID" 2>/dev/null
grep -q 'spawned at' "$OUT/$LOADER-bot.log" || fail "bot never spawned (see $OUT/$LOADER-bot.log)"
grep -qE 'kicked:|error:' "$OUT/$LOADER-bot.log" && fail "bot was kicked or errored (see $OUT/$LOADER-bot.log)"
grep -q 'MCMTBot left the game' "$STDOUT" || fail "bot did not leave cleanly"
echo "bot stayed connected" >> "$REPORT"
[ -n "$TAIL_PID" ] && kill "$TAIL_PID" 2>/dev/null
echo "server stopped cleanly" >> "$REPORT"

grep -v '^#' "$ROOT/tests/smoke/allowlist.txt" | grep -v '^\s*$' > "$OUT/$LOADER-allow.re"
grep -vE -f "$OUT/$LOADER-allow.re" "$STDOUT" > "$OUT/$LOADER-filtered.log"
PATTERN='Exception|Error:|/ERROR\]|ERROR\]|FATAL|Watchdog|A single server tick took|Mixin apply failed|Could not load mod'
if grep -qE "$PATTERN" "$OUT/$LOADER-filtered.log"; then
  echo "--- offending lines ---" >> "$REPORT"
  grep -nE "$PATTERN" "$OUT/$LOADER-filtered.log" | head -50 >> "$REPORT"
  fail "errors found in server log (see $REPORT)"
fi
grep -q 'MCMT is currently' "$STDOUT" || fail "/mcmt config state produced no output"
grep -q 'Current max threads' "$STDOUT" || fail "/mcmt stats produced no output"
grep -q 'MCMT thread pool started' "$STDOUT" || fail "thread pool was never started"
TASKS="$(grep -o 'Parallel tasks since start: World:[0-9]* Entity:[0-9]* TE:[0-9]* Env:[0-9]*' "$STDOUT" | tail -1)"
echo "$TASKS" >> "$REPORT"
WORLDS="$(echo "$TASKS" | sed -E 's/.*World:([0-9]+).*/\1/')"; ENTS="$(echo "$TASKS" | sed -E 's/.*Entity:([0-9]+).*/\1/')"
[ "${WORLDS:-0}" -gt 0 ] || fail "no level ticks ran on the worker pool"
[ "${ENTS:-0}" -gt 0 ] || fail "no entity ticks ran on the worker pool"
TES="$(echo "$TASKS" | sed -E 's/.*TE:([0-9]+).*/\1/')"
[ "${TES:-0}" -gt 0 ] || fail "no block entity ticks ran on the worker pool"
ENVS="$(echo "$TASKS" | sed -E 's/.*Env:([0-9]+).*/\1/')"
[ "${ENVS:-0}" -gt 0 ] || fail "no environment ticks ran on the worker pool"
echo "PASS" | tee -a "$REPORT"
