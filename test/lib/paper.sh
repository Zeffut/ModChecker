#!/usr/bin/env bash
# Helper : résout + télécharge le jar serveur Paper pour une version MC (API PaperMC v2).
# Usage : paper_download <mc_version> <cache_dir>  → echo le chemin du jar (ou code !=0 si absent).

paper_download() {
  local mc="$1" cache="$2"
  local api="https://api.papermc.io/v2/projects/paper"
  local meta
  meta="$(curl -fsS "$api/versions/$mc/builds" 2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    b = d['builds'][-1]                      # dernier build (le plus récent)
    print(b['build'], b['downloads']['application']['name'])
except Exception:
    pass
")"
  [ -n "$meta" ] || return 1
  local build name
  build="${meta%% *}"; name="${meta##* }"
  mkdir -p "$cache"
  local jar="$cache/$name"
  if [ ! -f "$jar" ]; then
    curl -fsS -o "$jar" "$api/versions/$mc/builds/$build/downloads/$name" || return 1
  fi
  echo "$jar"
}
