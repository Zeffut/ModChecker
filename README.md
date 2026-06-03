# ModChecker

Mod-checker Minecraft **générique et multi-plateforme**. Un **mod client** (Fabric + NeoForge)
envoie au serveur la liste des mods installés ; côté serveur, un **plugin Paper/Purpur** et/ou un
**plugin Velocity** enregistrent ces mods, permettent de les autoriser/bannir, et kickent les
joueurs porteurs d'un mod interdit. L'enforcement peut se faire **au backend** (Paper) **et/ou au
proxy** (Velocity).

Le contrat réseau (handshake serveur→client) est décrit dans [PROTOCOL.md](PROTOCOL.md).

## Comment ça marche

1. Le joueur rejoint → le serveur (Paper ou Velocity) envoie un paquet `modchecker:hello` (preuve
   qu'un plugin ModChecker est présent).
2. Le mod client répond **uniquement** s'il a reçu le hello : il envoie sa liste de mods sur
   `modchecker:modlist`.
3. Le serveur enregistre les mods (`UNKNOWN` par défaut) et **kicke** si un mod est `BANNED`.
   Un client vanilla (sans le mod) ignore le hello ; un serveur sans plugin n'en envoie jamais.

## Matrice de compatibilité

| Composant | Plateforme | Versions Minecraft | Artifact |
|-----------|-----------|--------------------|----------|
| Mod client | **Fabric** | 1.21.11, 26.1, 26.1.1, 26.1.2 | `modchecker-fabric-<mc>.jar` |
| Mod client | **NeoForge** | 1.21.11, 26.1, 26.1.1, 26.1.2 | `modchecker-neoforge-<mc>.jar` |
| Plugin serveur | **Paper / Purpur** | 1.21.11 → 26.1.2 (un seul jar) | `ModChecker-1.0.0.jar` |
| Plugin proxy | **Velocity** 3.4.0+ | indépendant de la version MC | `ModChecker-velocity-1.0.0.jar` |

> Le plugin Paper est compilé contre 1.21.11 et vérifié contre 26.1.2 ; il tourne sur toutes les
> versions ≥ 1.21.11 grâce à la stabilité de l'API Bukkit. Le plugin Velocity est version-agnostique
> (le proxy ne dépend pas de la version MC).

## Structure du dépôt

```
server/           Maven multi-module (protocole partagé)
├── common/       Pur Java : ModInfo, ModListCodec, ModStatus, BanPolicy (+ tests)
├── paper/        Plugin Bukkit/Paper/Purpur (handshake, /mods, GUI, persistance) — tests MockBukkit
└── velocity/     Plugin Velocity (enforcement au proxy)
mod/              Mod client Stonecutter (multi-version × multi-loader Fabric/NeoForge)
build-all.sh      Matrice : build + tests de TOUT (server + 8 nœuds mod)
PROTOCOL.md       Contrat réseau figé
```

## Build

### Plugins serveur (Paper + Velocity)
```bash
mvn -f server/pom.xml clean package
# → server/paper/target/ModChecker-1.0.0.jar              (plugins/ d'un serveur Paper/Purpur)
# → server/velocity/target/ModChecker-velocity-1.0.0.jar  (plugins/ d'un proxy Velocity)
```

### Mod client (toutes versions / loaders)
```bash
cd mod
JAVA_HOME=<jdk21> ./gradlew :1.21.11-fabric:build     # → mod/versions/1.21.11-fabric/build/libs/*.jar
JAVA_HOME=<jdk25> ./gradlew :26.1.2-fabric:build      # nœuds : <mc>-<loader>
```
- **JVM de lancement de Gradle, par nœud** (Fabric Loom exige que Gradle tourne sur le Java du MC ;
  NeoForge se contente du toolchain) : `26.x-fabric` → **JDK 25** ; tout le reste (`1.21.11-*`,
  `26.x-neoforge`) → **JDK 21**. `./build-all.sh` choisit automatiquement le bon JDK par nœud.

### Tout valider d'un coup
```bash
./build-all.sh                # server (mvn verify) + 8 nœuds mod, échoue si un seul target casse
./build-all.sh --server-only  # uniquement la partie Maven (CI rapide)
```

## Configuration

### Plugin Paper (`plugins/ModChecker/config.yml`)
```yaml
server-name: "Mon Serveur"        # en-tête des messages de kick
kick-without-mod: false           # kicker les clients sans le mod
grace-period-seconds: 5           # délai d'attente de la liste de mods
bypass-permission: "modchecker.bypass"
messages:
  banned-mod: "Mod(s) interdit(s) détecté(s) :"
  missing-mod: "Le mod ModChecker est requis. Installe-le et reconnecte-toi."
```
Commande : `/mods` (GUI) · `/mods list|player <joueur>|allow|ban|reset <mod-id>`.
Permission admin : `modchecker.admin`. Exemption : `modchecker.bypass` ou OP.

### Plugin Velocity (data dir du proxy)
Config propre au proxy (statuts de mods, `kick-without-mod`, `server-name`, messages). L'enforcement
(handshake + kick des mods bannis) se fait **au niveau du proxy**, avant d'atteindre un backend.

## Télémétrie (PostHog)

ModChecker envoie une télémétrie d'usage (PostHog) pour suivre le parc d'installation, les versions
MC/loader, l'adoption, les kicks et les mods détectés. Envoi **asynchrone, fire-and-forget**, sans
impact sur le jeu ni le serveur. Le mod client émet `client_started`, `server_joined`, `modlist_sent`,
`modlist_send_failed` ;
les plugins Paper/Velocity émettent `plugin_enabled`/`proxy_enabled`, `player_join`, `modlist_received`,
`mod_discovered`, `player_kicked`, `player_no_mod`, `mod_status_changed`, `command_used`.

**Désactiver :**
- Plugins (Paper / Velocity) : `telemetry: false` dans `config.yml` (Paper) / `config.json` (Velocity).
- Mod client : `-Dmodchecker.telemetry=false` au lancement, ou `"telemetry": false` dans
  `config/modchecker.json`.

Host d'ingestion configurable via `telemetry-host` (plugins) / `-Dmodchecker.telemetry.host=` (mod).
Région par défaut : EU (`https://eu.i.posthog.com`).

## Tests

- `mvn -f server/pom.xml verify` — 44 tests : protocole (`ModListCodec`/`BanPolicy`/`ModStatus`),
  intégration plugin Paper (**MockBukkit**), logique de décision Velocity.
- `./build-all.sh` — valide en plus que les 8 jars du mod compilent.

## Checklist de validation manuelle (e2e — clients réels)

- [ ] Serveur Paper/Purpur (1.21.11 et 26.1.2) + jar plugin → log `ModChecker activé`.
- [ ] Client **Fabric** et client **NeoForge** (chaque version) → au join, log `Mods de <joueur> : N`.
- [ ] **Serveur sans plugin** → le client modé n'envoie **rien** (handshake : pas de hello).
- [ ] **Client vanilla** → aucune erreur ; traité comme « sans mod ».
- [ ] `/mods ban <id>` → le porteur est kické ; permission `modchecker.bypass` → jamais kické.
- [ ] **Proxy Velocity** + jar velocity → un mod banni est kické **au proxy** (avant le backend).

## Publication

Publié sur Modrinth (mod Fabric/NeoForge + plugins serveur). Voir le script de publication.

## Protocole

Channels figés `modchecker:hello` (S2C) et `modchecker:modlist` (C2S) — voir [PROTOCOL.md](PROTOCOL.md).
Le même contrat est appliqué par le plugin Paper et par le plugin Velocity (code de protocole
partagé dans `server/common`).
