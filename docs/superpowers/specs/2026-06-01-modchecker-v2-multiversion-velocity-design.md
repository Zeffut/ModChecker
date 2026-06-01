# ModChecker v2 — Multi-version + Velocity + Tests (Design)

**Date :** 2026-06-01
**Statut :** validé (brainstorming v2)
**Étend :** `2026-06-01-modchecker-standalone-design.md` (v1, déjà implémentée pour 1.21.11)

## Nouvelles exigences (utilisateur)

1. **Velocity** : un plugin Velocity natif qui **enforce le mod-check au niveau du proxy** (handshake +
   kick avant d'atteindre un backend), en plus du plugin Paper backend.
2. **Toutes les versions ≥ 1.21.11** : plugin ET mod disponibles pour `1.21.11`, `26.1`, `26.1.1`,
   `26.1.2` (versioning calendaire après 1.21.11).
3. **Suite de tests exhaustive (pragmatique)** : unit protocole + intégration plugin (MockBukkit) +
   matrice de build (chaque version×loader compile/package) + checklist e2e.

## Décisions

| Sujet | Décision |
|-------|----------|
| Velocity | Plugin Velocity natif (`velocity-api 3.4.0`), enforcement au proxy. Backend Paper conservé. |
| Versions cibles | `1.21.11`, `26.1`, `26.1.1`, `26.1.2` (× Fabric/NeoForge pour le mod). |
| Multi-version mod | **Stonecutter** (`dev.kikugie.stonecutter 0.9.4`) : un code, variantes par version. |
| Tests | Exhaustif pragmatique (unit + MockBukkit + matrice de build + checklist e2e). |
| Protocole partagé | Module `common` (codec + modèle) partagé par Paper et Velocity. |

## Faits de plateforme (vérifiés le 2026-06-01)

- **paper-api** publié : `1.21.11-R0.1-SNAPSHOT` et `26.1.2.build.*-stable` uniquement. Les artifacts
  `26.1` / `26.1.1` sont **purgés** du Maven. → Le plugin se **compile** contre 1.21.11 et 26.1.2 ;
  il **tourne** sur les 4 versions via la stabilité de l'API Bukkit (compat runtime couvre 26.1/26.1.1).
  **Un seul jar plugin** suffit (pas de build par version).
- **velocity-api** : `3.4.0` (release). Indépendant de la version MC → **un seul jar Velocity**.
- **NeoForge** : `21.11.42` (1.21.11), `26.1.0.19-beta` (26.1), `26.1.1.15-beta` (26.1.1),
  `26.1.2.70-beta` (26.1.2). Build mod par version.
- **Fabric** : supporte les 4 versions ; yarn/loader/fabric-api à résoudre par version (agents).

## Architecture cible

```
ModChecker/
├── PROTOCOL.md                      ← contrat réseau (inchangé : hello S2C + modlist C2S)
├── README.md
├── server/                          ← Maven multi-module (protocole partagé)
│   ├── pom.xml                      (parent ; modules common, paper, velocity)
│   ├── common/                      ← PUR Java : ModInfo, ModListCodec, ModStatus, BanPolicy
│   │   └── tests unit (codec + politique de ban)
│   ├── paper/                       ← plugin Bukkit/Paper/Purpur (ex-`plugin/`), dépend de common
│   │   └── tests MockBukkit
│   └── velocity/                    ← plugin Velocity (proxy), dépend de common
│       └── tests logique
├── mod/                             ← Stonecutter multi-version, 2 loaders
│   ├── settings.gradle / stonecutter.gradle / gradle.properties
│   ├── versions: 1.21.11, 26.1, 26.1.1, 26.1.2
│   ├── fabric/                      ← source unique, directives Stonecutter si l'API diffère
│   └── neoforge/
├── build-all.sh                     ← matrice : build tout (server + mod × versions × loaders)
└── docs/superpowers/{specs,plans}
```

### Module `common` (nouveau, pur Java)

Extraction de ce qui ne dépend ni de Bukkit ni de Velocity, pour single-sourcer le protocole :

- `ModInfo` (record) — déplacé depuis paper.
- `ModListCodec` — `encode/decode` (VarInt+UTF-8), `parse` (JSON) — déplacé depuis paper.
- `ModStatus` (enum `ALLOWED, BANNED, UNKNOWN`) — remplace les chaînes magiques (cleanup revue v1).
- `BanPolicy` / `ModRegistry` — logique pure : « étant donné une liste de mods + une map de statuts,
  quels mods sont bannis ? ». Réutilisée identiquement par Paper et Velocity → comportement garanti
  cohérent. Testable sans serveur.

Paper et Velocity ne contiennent plus que la glue spécifique à leur API (events, kick, I/O config).

### Module `paper` (ex-`plugin/`)

Inchangé fonctionnellement (handshake hello, /mods, GUI, persistance). Refactoré pour consommer
`common` (statuts via `ModStatus`, détection bannis via `BanPolicy`). **Un seul jar**, compat 4 versions.

### Module `velocity` (nouveau)

Plugin Velocity (`@Plugin`, `velocity-api 3.4.0`). Au proxy :

- Enregistre les `ChannelIdentifier` `modchecker:hello` et `modchecker:modlist`.
- À la connexion d'un joueur (`ServerPostConnectEvent` ou `PostLoginEvent`), envoie le hello.
- Sur `PluginMessageEvent` (channel modlist), décode via `common.ModListCodec`, applique `BanPolicy`,
  **déconnecte** le joueur au proxy s'il a un mod banni.
- Config propre Velocity (`@DataDirectory`, fichier YAML/JSON) : liste des statuts de mods +
  `kick-without-mod`, `grace-period-seconds`, messages. (Le store n'est pas partagé avec le backend ;
  chaque couche a sa config — documenté.)
- **Un seul jar**, indépendant de la version MC.

### Mod multi-version (Stonecutter)

- Une base de code par loader (`fabric/`, `neoforge/`), variantes de version gérées par Stonecutter.
- Le protocole (hello receiver → envoi modlist) est identique ; directives `//? if >=26.1 {` …
  uniquement si l'API réseau Fabric/NeoForge diffère entre 1.21.11 et 26.x (à déterminer par agent).
- Sortie : `modchecker-fabric-<mcver>.jar` et `modchecker-neoforge-<mcver>.jar` pour chaque version.

## Stratégie de tests (exhaustif pragmatique)

| Niveau | Cible | Outil |
|--------|-------|-------|
| Unit protocole | `common` : ModListCodec (encode/decode/parse, VarInt multi-octets, UTF-8) | JUnit 5 |
| Unit politique | `common` : BanPolicy / ModStatus (transitions, détection bannis, exempt) | JUnit 5 |
| Intégration plugin | `paper` : join→hello envoyé, réception modlist→enregistrement, mod banni→kick, /mods, bypass | MockBukkit |
| Logique Velocity | `velocity` : décodage modlist + décision ban/kick (logique extraite, sans réseau réel) | JUnit 5 |
| Matrice de build | `build-all.sh` : build server (common+paper+velocity) + mod (4 versions × 2 loaders) ; échoue si un seul target casse | script |
| E2E | checklist manuelle documentée (clients réels Fabric/NeoForge × versions, Paper/Purpur/Velocity) | doc |

Un **agent dédié** construit la suite de tests (unit + MockBukkit + matrice). Les **agents par
version** valident chacun que leur variante mod (×2 loaders) compile et package.

## Parallélisation (exécution)

1. **Phase 1 (séquentiel)** — restructurer `server/` en multi-module + extraire `common`. La suite de
   tests existante doit rester verte.
2. **Phase 2 (séquentiel)** — plugin Velocity (dépend de `common`).
3. **Phase 3 (séquentiel)** — scaffold Stonecutter du mod (config + source Fabric/NeoForge).
4. **Phase 4 (PARALLÈLE)** — 1 agent par version MC (`1.21.11`, `26.1`, `26.1.1`, `26.1.2`) : résout
   mappings + vérifie build Fabric **et** NeoForge de sa variante.
5. **Phase 5 (PARALLÈLE avec 4)** — 1 agent « tests » : suite unit + MockBukkit + `build-all.sh`.
6. **Phase 6 (séquentiel)** — README, compat plugin 26.1.2, merge.

## Hors périmètre (YAGNI)

- E2E automatisé (gametests / serveurs headless) — explicitement écarté.
- Synchronisation de config proxy↔backend — chaque couche a sa config.
- Versions < 1.21.11 et snapshots non-stables.
- Forge legacy.
