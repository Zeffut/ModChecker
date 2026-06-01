#!/usr/bin/env bash
# =============================================================================
# build-all.sh — ModChecker full build matrix
# =============================================================================
# Construit et teste TOUS les artefacts ModChecker :
#   1. Server (Maven multi-module : common, paper, velocity)
#      → mvn clean verify (compile + tests JUnit)
#   2. Mod Fabric/NeoForge pour chaque version de MC :
#      • 1.21.11-fabric, 1.21.11-neoforge  (JDK 21)
#      • 26.1-fabric, 26.1-neoforge         (JDK 25 requis — toolchain Gradle)
#      • 26.1.1-fabric, 26.1.1-neoforge     (JDK 25 requis)
#      • 26.1.2-fabric, 26.1.2-neoforge     (JDK 25 requis)
#
# Usage :
#   ./build-all.sh               # Build complet (server + 8 nœuds mod)
#   ./build-all.sh --server-only # Uniquement le build Maven server
#
# Codes de sortie :
#   0 — tout est vert
#   1 — au moins un target a échoué
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
MOD_DIR="$SCRIPT_DIR/mod"

# JVM de lancement de Gradle, par nœud (Fabric Loom exige que Gradle tourne sur le Java du MC) :
#   26.x-fabric → JDK 25 ; tout le reste (1.21.11-*, 26.x-neoforge) → JDK 21.
JDK25_HOME="${JDK25_HOME:-/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home}"
JDK21_HOME="${JDK21_HOME:-/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home}"
[ -d "$JDK21_HOME" ] || JDK21_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || echo "$JDK21_HOME")"

# Couleurs (désactivées si pas de terminal interactif)
if [ -t 1 ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RESET='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; CYAN=''; RESET=''
fi

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
declare -a FAILURES=()
declare -a PASSES=()

pass() { echo -e "${GREEN}[PASS]${RESET} $1"; PASSES+=("$1"); }
fail() { echo -e "${RED}[FAIL]${RESET} $1"; FAILURES+=("$1"); }

print_separator() { echo -e "${CYAN}$(printf '─%.0s' {1..72})${RESET}"; }

# -----------------------------------------------------------------------------
# 1. Server (Maven)
# -----------------------------------------------------------------------------
build_server() {
  print_separator
  echo -e "${CYAN}▶ Server : mvn clean verify${RESET}"
  print_separator
  if mvn -f "$SERVER_DIR/pom.xml" clean verify; then
    pass "server/maven"
  else
    fail "server/maven"
  fi
}

# -----------------------------------------------------------------------------
# 2. Mod (Gradle — un nœud Stonecutter par appel)
# -----------------------------------------------------------------------------
build_mod_node() {
  local node="$1"            # e.g. "1.21.11-fabric"
  local mc_ver="${node%%-*}" # tout avant le premier -
  local loader="${node##*-}" # tout après le dernier -

  # Fabric Loom exige que Gradle TOURNE sur le Java du MC : 26.x-fabric → JDK 25, sinon JDK 21.
  local jdk_home="$JDK21_HOME"
  if [[ "$mc_ver" == 26.* && "$loader" == "fabric" ]]; then
    jdk_home="$JDK25_HOME"
  fi
  if [ ! -d "$jdk_home" ]; then
    echo -e "${YELLOW}[WARN]${RESET} JDK introuvable ($jdk_home) — nœud $node peut échouer."
  fi

  echo -e "${CYAN}▶ Mod : :${node}:build  (JAVA_HOME=$(basename "$(dirname "$(dirname "$jdk_home")")"))${RESET}"
  if (cd "$MOD_DIR" && JAVA_HOME="$jdk_home" ./gradlew ":${node}:build" --quiet); then
    pass "mod/$node"
  else
    fail "mod/$node"
  fi
}

# -----------------------------------------------------------------------------
# Analyse des arguments
# -----------------------------------------------------------------------------
SERVER_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --server-only) SERVER_ONLY=true ;;
    *) echo "Usage: $0 [--server-only]" >&2; exit 1 ;;
  esac
done

# -----------------------------------------------------------------------------
# Exécution
# -----------------------------------------------------------------------------
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${CYAN}║              ModChecker — Build Matrix$(date '+  %Y-%m-%d %H:%M')              ║${RESET}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════════╝${RESET}"
echo ""

build_server

if [ "$SERVER_ONLY" = false ]; then
  # Nœuds 1.21.11 (JDK 21 standard)
  for loader in fabric neoforge; do
    build_mod_node "1.21.11-${loader}"
  done
  # Nœuds 26.x (JDK 25 via toolchain Gradle — Phase 4)
  for mc in 26.1 26.1.1 26.1.2; do
    for loader in fabric neoforge; do
      build_mod_node "${mc}-${loader}"
    done
  done
fi

# -----------------------------------------------------------------------------
# Résumé des artefacts produits
# -----------------------------------------------------------------------------
print_separator
echo -e "${CYAN}▶ Artefacts produits${RESET}"
print_separator
echo ""

# Server jars
for jar in "$SERVER_DIR"/paper/target/ModChecker-*.jar \
           "$SERVER_DIR"/velocity/target/ModChecker-velocity-*.jar; do
  [ -f "$jar" ] && echo "  server  → $(basename "$jar")  ($(du -sh "$jar" | cut -f1))"
done

# Mod jars (les builds Gradle produisent des jars dans versions/<node>/build/libs/)
if [ "$SERVER_ONLY" = false ]; then
  for node_dir in "$MOD_DIR"/versions/*/; do
    node="$(basename "$node_dir")"
    for jar in "$node_dir"build/libs/*.jar; do
      [ -f "$jar" ] && echo "  mod/$node → $(basename "$jar")  ($(du -sh "$jar" | cut -f1))"
    done
  done
fi

echo ""
print_separator

# -----------------------------------------------------------------------------
# Tableau final PASS / FAIL
# -----------------------------------------------------------------------------
echo -e "${CYAN}▶ Bilan${RESET}"
print_separator

total_ok=${#PASSES[@]}
total_fail=${#FAILURES[@]}

for t in "${PASSES[@]+"${PASSES[@]}"}"; do
  echo -e "  ${GREEN}✔${RESET}  $t"
done
for t in "${FAILURES[@]+"${FAILURES[@]}"}"; do
  echo -e "  ${RED}✘${RESET}  $t"
done

echo ""
if [ $total_fail -eq 0 ]; then
  echo -e "${GREEN}[OK] $total_ok target(s) — ALL PASSED${RESET}"
  exit 0
else
  echo -e "${RED}[KO] $total_ok passed, $total_fail FAILED : ${FAILURES[*]:-}${RESET}"
  exit 1
fi
