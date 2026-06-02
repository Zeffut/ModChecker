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
MOD_PROJECT_ID="${MOD_PROJECT_ID:-pZZSQM2X}"     # zeffut-mod-checker (mod)
# `-` (et non `:-`) : PLUGIN_PROJECT_ID="" (vide) → on saute les plugins ; absent → défaut.
PLUGIN_PROJECT_ID="${PLUGIN_PROJECT_ID-oIAAfSll}"  # zeffut-mod-checker-plugin (plugin)

MOD_VERSION="2.0.1"
PLUGIN_VERSION="1.0.0"
PLUGIN_GAME_VERSIONS='["1.21.11","26.1","26.1.1","26.1.2"]'

# Changelog (EN — toute la vitrine Modrinth est en anglais)
CHANGELOG="2.0.1 - Fixes the handshake on Paper/Bukkit servers: the client now reliably reports its mod list (detected via native channel registration instead of a server-to-client packet, which Bukkit does not deliver reliably). The client still only talks to servers running ModChecker. Validated end-to-end on Fabric and NeoForge."

# --- Validation token ---
echo "▶ Validation du token Modrinth…"
who="$(curl -fsS -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" "$API/user" | sed -n 's/.*"username":"\([^"]*\)".*/\1/p')"
[ -n "$who" ] || { echo "Token invalide." >&2; exit 1; }
echo "  ✔ authentifié : $who"

# Vrai si le numéro de version existe déjà sur le projet (idempotence : évite les doublons).
version_exists() {
  local project="$1" vnum="$2"
  curl -fsS -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" "$API/project/$project/version" 2>/dev/null \
    | python3 -c "import sys,json; sys.exit(0 if any(v['version_number']==sys.argv[1] for v in json.load(sys.stdin)) else 1)" "$vnum"
}

# upload_version <project_id> <version_number> <name> <loaders_json> <game_versions_json> <file>
upload_version() {
  local project="$1" vnum="$2" name="$3" loaders="$4" gvs="$5" file="$6"
  [ -f "$file" ] || { echo "  ⚠ jar absent, ignoré : $file"; return 0; }
  if $PUBLISH && version_exists "$project" "$vnum"; then
    echo "  = déjà publié, ignoré : $name ($vnum)"; return 0
  fi
  local data
  data=$(cat <<JSON
{"project_id":"$project","name":"$name","version_number":"$vnum","version_type":"release",
 "changelog":"$CHANGELOG","loaders":$loaders,"game_versions":$gvs,
 "dependencies":[],"featured":false,"file_parts":["file"]}
JSON
)
  if $PUBLISH; then
    # --form-string (et NON -F) pour le champ data : sinon curl interprète ';' '@' '<' dans le
    # JSON (le changelog contient des ';') et casse le payload → 400 "EOF while parsing string".
    if curl -fsS -X POST -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: $UA" \
         --form-string "data=$data" -F "file=@$file" "$API/version" >/dev/null; then
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
# Filtre par MOD_VERSION : sinon d'anciens jars laissés dans build/libs seraient republiés.
for jar in $(find mod/versions -path "*/build/libs/*-${MOD_VERSION}+*.jar" ! -name '*-sources.jar' | sort); do
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
