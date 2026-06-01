# ModChecker

Mod-checker Minecraft générique : un **mod client** (Fabric + NeoForge) envoie au serveur la
liste des mods installés, un **plugin serveur** (Purpur/Paper) les enregistre, permet de les
autoriser/bannir et kicke les joueurs porteurs d'un mod banni.

Voir [PROTOCOL.md](PROTOCOL.md) pour le contrat réseau.

## Structure
- `mod/` — mod client multi-loader (`mod/fabric`, `mod/neoforge`)
- `plugin/` — plugin serveur Maven

## Build (rempli au fil de l'implémentation)
