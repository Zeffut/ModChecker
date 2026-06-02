#!/usr/bin/env bash
# Test e2e d'une cible : démarre un serveur Paper + lance le dev client (runClient, quick-play),
# vérifie que le handshake ModChecker aboutit (le serveur reçoit la liste de mods).
# Usage : [PORT=25599] test/e2e.sh <mc_version> <fabric|neoforge>
# Sortie : PASS (exit 0) / FAIL (exit 1) / SKIP (exit 2, pas de serveur pour cette version).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mc="${1:?usage: e2e.sh <mc> <loader>}"
loader="${2:?usage: e2e.sh <mc> <loader>}"
node="$mc-$loader"
PORT="${PORT:-25599}"
WORK="$ROOT/test/work/$mc"
RESULTS="$ROOT/test/results/$node"
mkdir -p "$RESULTS"
SRVLOG="$RESULTS/server.log"; CLILOG="$RESULTS/client.log"

JDK21="${JDK21_HOME:-/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home}"
JDK25="${JDK25_HOME:-/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home}"
case "$mc" in 26.*) SERVER_JDK="$JDK25";; *) SERVER_JDK="$JDK21";; esac
# Loom exige Gradle sur JDK 25 pour 26.x-fabric ; sinon JDK 21.
if [ "$loader" = "fabric" ] && [[ "$mc" == 26.* ]]; then CLIENT_JDK="$JDK25"; else CLIENT_JDK="$JDK21"; fi

SRV_PID=""; CLI_PID=""
cleanup() {
  # Le client est forké par Gradle → on le tue par son marqueur JVM, pas par le PID gradlew.
  pkill -f "modchecker.e2e.client=1" 2>/dev/null || true
  [ -n "$CLI_PID" ] && kill "$CLI_PID" 2>/dev/null
  [ -n "$SRV_PID" ] && kill "$SRV_PID" 2>/dev/null
  pkill -f "modchecker.e2e.server=$PORT" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Pré-vol : retire un éventuel lock de monde laissé par un run précédent crashé.
rm -f "$WORK/world/session.lock" 2>/dev/null || true
pkill -f "modchecker.e2e.server=$PORT" 2>/dev/null || true

echo "▶ [$node] préparation du serveur…"
PORT="$PORT" "$ROOT/test/setup-server.sh" "$mc" >/dev/null || { echo "SKIP [$node] : pas de serveur Paper pour $mc"; exit 2; }

echo "▶ [$node] démarrage du serveur (JDK $("$SERVER_JDK/bin/java" -version 2>&1 | head -1 | grep -oE '[0-9]+' | head -1))…"
# exec → le sous-shell EST remplacé par java, donc $! = vrai PID java (kill fiable).
( cd "$WORK" && exec "$SERVER_JDK/bin/java" -Dmodchecker.e2e.server="$PORT" -Xmx2G -jar paper.jar nogui ) >"$SRVLOG" 2>&1 &
SRV_PID=$!
for _ in $(seq 1 180); do grep -q "Done (" "$SRVLOG" 2>/dev/null && break; kill -0 "$SRV_PID" 2>/dev/null || break; sleep 1; done
if ! grep -q "Done (" "$SRVLOG" 2>/dev/null; then echo "FAIL [$node] : serveur pas prêt (voir $SRVLOG)"; exit 1; fi
echo "  serveur prêt sur localhost:$PORT"

echo "▶ [$node] lancement du client (runClient quick-play)… (1er run = téléchargement assets)"
( cd "$ROOT/mod" && JAVA_HOME="$CLIENT_JDK" ./gradlew ":$node:runClient" -PtestServer="localhost:$PORT" --console=plain ) >"$CLILOG" 2>&1 &
CLI_PID=$!

RESULT="TIMEOUT"
for _ in $(seq 1 240); do
  if grep -q "détecté" "$SRVLOG" 2>/dev/null; then RESULT="PASS"; break; fi
  if grep -q "mod checker absent" "$SRVLOG" 2>/dev/null; then RESULT="FAIL_KICK"; break; fi
  kill -0 "$CLI_PID" 2>/dev/null || { sleep 3; grep -q "détecté" "$SRVLOG" 2>/dev/null && RESULT="PASS" || RESULT="CLIENT_EXIT"; break; }
  sleep 2
done

echo "════════ [$node] : $RESULT ════════"
echo "— client [ModChecker] —"; grep -i "\[ModChecker\]" "$CLILOG" 2>/dev/null | tail -6 || echo "  (aucune ligne [ModChecker])"
echo "— serveur —"; grep -iE "détecté|mod checker absent|interdit" "$SRVLOG" 2>/dev/null | tail -4 || echo "  (rien)"

[ "$RESULT" = "PASS" ] && exit 0 || exit 1
