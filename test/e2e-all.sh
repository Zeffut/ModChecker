#!/usr/bin/env bash
# Lance l'e2e sur toutes les cibles (4 versions × 2 loaders) et agrège les résultats.
# Variables : VERSIONS="1.21.11 26.1 …", LOADERS="fabric neoforge".
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSIONS="${VERSIONS:-1.21.11 26.1 26.1.1 26.1.2}"
LOADERS="${LOADERS:-fabric neoforge}"

results=()
port=25599
for mc in $VERSIONS; do
  for loader in $LOADERS; do
    PORT="$port" "$ROOT/test/e2e.sh" "$mc" "$loader"
    case $? in 0) r="✔ PASS";; 2) r="• SKIP";; *) r="✘ FAIL";; esac
    results+=("$mc-$loader : $r")
    port=$((port + 1))
  done
done

echo
echo "════════════ Récapitulatif e2e ════════════"
printf '  %s\n' "${results[@]+"${results[@]}"}"
# code de sortie non-zéro si au moins un FAIL
printf '%s\n' "${results[@]+"${results[@]}"}" | grep -q "FAIL" && exit 1 || exit 0
