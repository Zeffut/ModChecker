#!/usr/bin/env bash
# Prépare un serveur Paper jetable pour une version MC dans test/work/<mc>/.
# Usage : [PORT=25599] test/setup-server.sh <mc_version>   → echo le chemin du dossier serveur.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/paper.sh
source "$ROOT/test/lib/paper.sh"

mc="${1:?usage: setup-server.sh <mc>}"
PORT="${PORT:-25599}"
WORK="$ROOT/test/work/$mc"
CACHE="$ROOT/test/cache"

mkdir -p "$WORK/plugins/ModChecker"

# 1. Plugin (build si absent)
PLUGIN="$ROOT/server/paper/target/ModChecker-1.0.0.jar"
if [ ! -f "$PLUGIN" ]; then
  ( cd "$ROOT" && mvn -q -f server/pom.xml -pl paper -am package -DskipTests )
fi
cp "$PLUGIN" "$WORK/plugins/"

# 2. Jar serveur Paper
JAR="$(paper_download "$mc" "$CACHE")" || { echo "ERREUR: pas de build Paper pour $mc" >&2; exit 2; }
cp "$JAR" "$WORK/paper.jar"

# 3. EULA + server.properties (offline, flat, léger)
echo "eula=true" > "$WORK/eula.txt"
cat > "$WORK/server.properties" <<EOF
online-mode=false
server-port=$PORT
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
max-players=5
motd=ModChecker e2e
EOF

# 4. Config plugin : kicker vite si pas de mod (détection rapide d'un échec)
cat > "$WORK/plugins/ModChecker/config.yml" <<EOF
server-name: "E2E"
kick-without-mod: true
grace-period-seconds: 8
bypass-permission: "modchecker.bypass"
messages:
  banned-mod: "Banned mod detected:"
  missing-mod: "ModChecker required."
EOF

echo "$WORK"
