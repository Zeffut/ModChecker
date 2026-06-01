#!/usr/bin/env bash
# =============================================================================
# publish-modrinth.sh — publie ModChecker sur Modrinth (mod + plugins serveur).
#
# Sécurité : DRY-RUN par défaut (n'envoie rien). Ajoute --publish pour publier réellement.
# Le token est lu depuis ./.env (MODRINTH_TOKEN, gitignoré). Jamais affiché.
#
# Prérequis : avoir créé les projets Modrinth et renseigné leurs IDs/slugs ci-dessous
# (ou via variables d'env MOD_PROJECT_ID / PLUGIN_PROJECT_ID).
#
# Usage :
#   ./publish-modrinth.sh                 # dry-run : valide token + liste ce qui serait publié
#   MOD_PROJECT_ID=xxx PLUGIN_PROJECT_ID=yyy ./publish-modrinth.sh --publish
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PUBLISH=false
[ "${1:-}" = "--publish" ] && PUBLISH=true

# --- Token ---
[ -f .env ] || { echo "Erreur : .env introuvable (MODRINTH_TOKEN attendu)." >&2; exit 1; }
# shellcheck disable=SC1091
MODRINTH_TOKEN="$(grep -E '^MODRINTH_TOKEN=' .env | head -1 | cut -d= -f2-)"
[ -n "$MODRINTH_TOKEN" ] || { echo "Erreur : MODRINTH_TOKEN vide dans .env." >&2; exit 1; }
UA="Zeffut/ModChecker/2.0.0 (tom77ds@gmail.com)"
API="https://api.modrinth.com/v2"

# --- Projets (à renseigner après création sur modrinth.com) ---
MOD_PROJECT_ID="${MOD_PROJECT_ID:-}"        # projet type "mod"
PLUGIN_PROJECT_ID="${PLUGIN_PROJECT_ID:-}"  # projet type "plugin"

MOD_VERSION="2.0.0"
PLUGIN_VERSION="1.0.0"
GAME_VERSIONS='["1.21.11","26.1","26.1.1","26.1.2"]'

# --- Validation token ---
echo "▶ Validation du token Modrinth…"
who="$(curl -fsS -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" "$API/user" | sed -n 's/.*"username":"\([^"]*\)".*/\1/p')"
[ -n "$who" ] || { echo "Token invalide." >&2; exit 1; }
echo "  ✔ authentifié : $who"

# --- Récupère les jars du mod (hors -sources) ---
MOD_JARS=()
while IFS= read -r _jar; do MOD_JARS+=("$_jar"); done \
  < <(find mod/versions -path '*/build/libs/*.jar' ! -name '*-sources.jar' | sort)
PLUGIN_JAR="server/paper/target/ModChecker-${PLUGIN_VERSION}.jar"
VELOCITY_JAR="server/velocity/target/ModChecker-velocity-${PLUGIN_VERSION}.jar"

echo
echo "▶ Mod (loaders: fabric, neoforge ; versions: 1.21.11/26.1/26.1.1/26.1.2)"
printf '   %s\n' "${MOD_JARS[@]:-<aucun — lance ./build-all.sh>}"
echo "▶ Plugins (loaders: paper, purpur, velocity)"
printf '   %s\n' "$PLUGIN_JAR" "$VELOCITY_JAR"

# upload_version <project_id> <version_number> <loaders_json> <files...>
upload_version() {
  local project="$1" vnum="$2" loaders="$3"; shift 3
  local files=("$@")
  local data
  data=$(cat <<JSON
{"project_id":"$project","name":"ModChecker $vnum","version_number":"$vnum",
 "version_type":"release","loaders":$loaders,"game_versions":$GAME_VERSIONS,
 "dependencies":[],"featured":true,"file_parts":[$(printf '"%s",' $(seq 0 $((${#files[@]}-1))) | sed 's/,$//')]}
JSON
)
  local args=(-H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA"
              -F "data=$data")
  local i=0; for f in "${files[@]}"; do args+=(-F "$i=@$f"); i=$((i+1)); done
  if $PUBLISH; then
    curl -fsS -X POST "${args[@]}" "$API/version" >/dev/null && echo "  ✔ publié : $project $vnum"
  else
    echo "  (dry-run) POST $API/version  project=$project  version=$vnum  files=${#files[@]}"
  fi
}

echo
if ! $PUBLISH; then
  echo "=== DRY-RUN (rien n'est envoyé). Relance avec --publish pour publier. ==="
else
  [ -n "$MOD_PROJECT_ID" ]    || { echo "MOD_PROJECT_ID manquant." >&2; exit 1; }
  [ -n "$PLUGIN_PROJECT_ID" ] || { echo "PLUGIN_PROJECT_ID manquant." >&2; exit 1; }
fi

[ ${#MOD_JARS[@]} -gt 0 ] && upload_version "${MOD_PROJECT_ID:-DRYRUN}" "$MOD_VERSION" '["fabric","neoforge"]' "${MOD_JARS[@]}"
[ -f "$PLUGIN_JAR" ] && [ -f "$VELOCITY_JAR" ] && \
  upload_version "${PLUGIN_PROJECT_ID:-DRYRUN}" "$PLUGIN_VERSION" '["paper","purpur","velocity"]' "$PLUGIN_JAR" "$VELOCITY_JAR"

echo "▶ Terminé."
