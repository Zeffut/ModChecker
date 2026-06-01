#!/usr/bin/env bash
# =============================================================================
# publish-modrinth.sh — publie ModChecker sur Modrinth.
#
# Sécurité : DRY-RUN par défaut (n'envoie rien). Ajoute --publish pour publier réellement.
# Le token est lu depuis ./.env (MODRINTH_TOKEN, gitignoré). Jamais affiché.
#
# Modèle de publication :
#   - MOD (projet type "mod")    : une version par (loader × version MC), soit 8 versions.
#   - PLUGIN (projet type "plugin"): 2 versions — jar Paper (loaders paper+purpur) et jar Velocity.
#
# Usage :
#   ./publish-modrinth.sh                       # dry-run
#   ./publish-modrinth.sh --publish             # publie le mod (projet déjà connu ci-dessous)
#   PLUGIN_PROJECT_ID=xxx ./publish-modrinth.sh --publish   # + publie aussi les plugins
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PUBLISH=false
[ "${1:-}" = "--publish" ] && PUBLISH=true

# --- Token ---
[ -f .env ] || { echo "Erreur : .env introuvable (MODRINTH_TOKEN attendu)." >&2; exit 1; }
MODRINTH_TOKEN="$(grep -E '^MODRINTH_TOKEN=' .env | head -1 | cut -d= -f2-)"
[ -n "$MODRINTH_TOKEN" ] || { echo "Erreur : MODRINTH_TOKEN vide dans .env." >&2; exit 1; }
UA="Zeffut/ModChecker/2.0.0 (tom77ds@gmail.com)"
API="https://api.modrinth.com/v2"

# --- Projets Modrinth ---
MOD_PROJECT_ID="${MOD_PROJECT_ID:-pZZSQM2X}"     # zeffut-mod-checker (type mod) — déjà créé
PLUGIN_PROJECT_ID="${PLUGIN_PROJECT_ID:-}"       # projet type "plugin" — à renseigner si dispo

MOD_VERSION="2.0.0"
PLUGIN_VERSION="1.0.0"
PLUGIN_GAME_VERSIONS='["1.21.11","26.1","26.1.1","26.1.2"]'

# Changelog (EN — toute la vitrine Modrinth est en anglais)
CHANGELOG="Reports the client mod list to the server; the server/proxy can allow or ban mods and kicks players carrying a banned one. Server-side hello handshake: the client only sends its mod list to a server running ModChecker. Supports Fabric and NeoForge on Minecraft 1.21.11, 26.1, 26.1.1 and 26.1.2."

# --- Validation token ---
echo "▶ Validation du token Modrinth…"
who="$(curl -fsS -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" "$API/user" | sed -n 's/.*"username":"\([^"]*\)".*/\1/p')"
[ -n "$who" ] || { echo "Token invalide." >&2; exit 1; }
echo "  ✔ authentifié : $who"

# upload_version <project_id> <version_number> <name> <loaders_json> <game_versions_json> <file>
upload_version() {
  local project="$1" vnum="$2" name="$3" loaders="$4" gvs="$5" file="$6"
  [ -f "$file" ] || { echo "  ⚠ jar absent, ignoré : $file"; return 0; }
  local data
  data=$(cat <<JSON
{"project_id":"$project","name":"$name","version_number":"$vnum","version_type":"release",
 "changelog":"$CHANGELOG","loaders":$loaders,"game_versions":$gvs,
 "dependencies":[],"featured":false,"file_parts":["file"]}
JSON
)
  if $PUBLISH; then
    if curl -fsS -X POST -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" \
         -F "data=$data" -F "file=@$file" "$API/version" >/dev/null; then
      echo "  ✔ publié : $name"
    else
      echo "  ✘ échec  : $name" >&2; return 1
    fi
  else
    echo "  (dry-run) $project ← $name  [$loaders $gvs]  $(basename "$file")"
  fi
}

echo
echo "▶ MOD → projet $MOD_PROJECT_ID (une version par loader × version MC)"
for jar in $(find mod/versions -path '*/build/libs/*.jar' ! -name '*-sources.jar' | sort); do
  node="$(echo "$jar" | sed -E 's#mod/versions/([^/]+)/.*#\1#')"   # ex. 26.1.2-fabric
  mc="${node%-*}"; loader="${node##*-}"
  upload_version "$MOD_PROJECT_ID" "${MOD_VERSION}+${node}" \
    "ModChecker ${MOD_VERSION} (${loader} ${mc})" "[\"${loader}\"]" "[\"${mc}\"]" "$jar"
done

echo
echo "▶ PLUGINS → projet ${PLUGIN_PROJECT_ID:-<non défini>}"
if [ -n "$PLUGIN_PROJECT_ID" ]; then
  upload_version "$PLUGIN_PROJECT_ID" "${PLUGIN_VERSION}-paper" "ModChecker ${PLUGIN_VERSION} (Paper/Purpur)" \
    '["paper","purpur"]' "$PLUGIN_GAME_VERSIONS" "server/paper/target/ModChecker-${PLUGIN_VERSION}.jar"
  upload_version "$PLUGIN_PROJECT_ID" "${PLUGIN_VERSION}-velocity" "ModChecker ${PLUGIN_VERSION} (Velocity)" \
    '["velocity"]' "$PLUGIN_GAME_VERSIONS" "server/velocity/target/ModChecker-velocity-${PLUGIN_VERSION}.jar"
else
  echo "  (ignoré — définis PLUGIN_PROJECT_ID pour publier les plugins serveur)"
fi

echo
$PUBLISH && echo "▶ Publication terminée." || echo "=== DRY-RUN. Relance avec --publish pour publier. ==="
