# ModChecker — Design (projet standalone)

**Date :** 2026-06-01
**Statut :** validé (brainstorming)

## Contexte

Le mod-checker existait éparpillé dans le projet `Zeffut-SMP` :

- **Mod client Fabric** (`saison-2/FabricMod`) — au join, liste les mods chargés et les envoie au
  serveur sur le channel `zeffsmp:modlist`.
- **Plugin serveur** (`saison-3/Plugin`, fondu dans le gros `ZeffutSMPPlugin`) — `ModChecker.java`
  (réception, statuts, kick, commande `/mods`, tab-complete) + `ModCheckerGUI.java` (interface
  inventaire de gestion des statuts).

On en fait un **projet à part entière**, **générique et réutilisable** (configurable, non lié à
Zeffut-SMP), avec un **mod client multi-loader (Fabric + NeoForge)** et un **plugin serveur**.

Les originaux dans `Zeffut-SMP` restent **intacts** (on copie, on ne déplace pas).

## Décisions

| Sujet | Décision |
|-------|----------|
| Branding | Générique & configurable via `config.yml` |
| Loaders client | Fabric **+** NeoForge, deux modules simples (pas d'Architectury) |
| Originaux Zeffut-SMP | Laissés intacts (copie) |
| Version Minecraft | 1.21.11 |
| ProtocolLib | **Retiré** (le mod-checker n'en a pas besoin) |
| Package | `fr.zeffut.modchecker` (namespace d'auteur conservé) |
| Nom | ModChecker, commande `/mods` |

## Architecture

Monorepo `ModChecker/` :

```
ModChecker/
├── README.md
├── PROTOCOL.md            ← contrat réseau (channel + format JSON), source de vérité
├── mod/                   ← mod client (Gradle multi-projet)
│   ├── settings.gradle    ← inclut :fabric et :neoforge
│   ├── gradle.properties  ← versions MC/loaders centralisées
│   ├── fabric/
│   └── neoforge/
└── plugin/                ← plugin serveur (Maven, Purpur/Bukkit)
    ├── pom.xml
    └── src/main/...
```

### Flux (inchangé — c'est ce qui fonctionne déjà)

1. Le joueur rejoint → le mod client liste tous ses mods chargés (`id`, `name`, `version`).
2. Sérialisation JSON → envoi d'**un seul paquet** sur le channel `modchecker:modlist`.
3. Le plugin reçoit, parse, enregistre les mods inconnus (`UNKNOWN`), kick si un mod est `BANNED`
   (ou si le mod-checker est absent et que `kick-without-mod` est activé).

## Contrat réseau (`PROTOCOL.md`)

Le channel et le format sont un **contrat figé** partagé par le mod et le plugin. Le channel
**n'est pas** dans le config serveur : le mod est compilé avec, les deux côtés doivent rester
synchrones.

- **Channel :** `modchecker:modlist` (namespace générique, remplace `zeffsmp:modlist`).
- **Sens :** Client → Serveur (C2S), un seul paquet, envoyé au join.
- **Payload :** un **String MC unique** (VarInt longueur + UTF-8) contenant un tableau JSON :
  ```json
  [{"id":"fabric-api","name":"Fabric API","version":"0.x.y"}, ...]
  ```
- Les deux loaders sérialisent exactement ce String unique → le plugin décode pareil dans les deux
  cas (méthode `readMcString` : VarInt + UTF-8).

## Composant : mod client (`mod/`)

Gradle multi-projet, **sans Architectury**. La logique est ~10 lignes ; le bout commun (channel,
format) est minuscule et documenté dans `PROTOCOL.md`, donc duplication assumée plutôt qu'un
framework.

- **`fabric/`** — reprise directe de l'existant (`ModCheckerClient` + `ModListPayload`), namespace
  mis à jour. `FabricLoader.getInstance().getAllMods()` pour lister, `ClientPlayNetworking.send`
  pour envoyer. Cible Fabric 1.21.11 / Java 21.
- **`neoforge/`** — équivalent NeoForge : `ModList.get().getMods()` pour lister,
  `PayloadRegistrar.optional(...)` pour autoriser l'envoi vers un serveur **non-NeoForge** (Purpur),
  même String unique sur le fil.

**Gotcha NeoForge :** NeoForge négocie ses payloads par défaut et refuse d'envoyer à un serveur
"vanilla"/Bukkit. On enregistre le payload en `optional()` pour débloquer l'envoi vers Purpur.

**Sortie :** deux jars indépendants, `modchecker-fabric-<v>.jar` et `modchecker-neoforge-<v>.jar`.

Pas de tests sur le mod : trop fin (lister + envoyer).

## Composant : plugin serveur (`plugin/`)

Base = version **saison-3** (la plus aboutie : GUI + tab-complete), **découplée de Zeffut-SMP**.

- **`ModCheckerPlugin`** (nouveau) — vrai `JavaPlugin` autonome : instancie `ModChecker` +
  `ModCheckerGUI`, enregistre le channel entrant, la commande `/mods`, le listener join/quit, charge
  `config.yml`.
- **`ModChecker`** — repris, avec **retrait du couplage** `plugin.getCombatManager().removeCombat()`
  (spécifique Zeffut → supprimé). Lit ses réglages depuis `config.yml`.
- **`ModCheckerGUI`** — repris tel quel (aucun couplage Zeffut).
- **Dépendances :** Purpur API (`provided`) uniquement. **ProtocolLib retiré.** Java 21, Maven +
  shade (pour embarquer Gson).

### `config.yml` (rend générique ce qui était en dur)

```yaml
# Nom affiché dans les messages de kick (remplace "Zeffut SMP" en dur)
server-name: "Mon Serveur"
# Kicker les joueurs qui n'envoient pas leur liste de mods (mod-checker absent)
kick-without-mod: false
# Délai d'attente avant de considérer le mod-checker absent (était 5s en dur)
grace-period-seconds: 5
# Permission qui exempte du mod-checker (les OP restent exemptés)
bypass-permission: "modchecker.bypass"
messages:
  banned-mod: "Mod(s) interdit(s) détecté(s) :"
  missing-mod: "Le mod ModChecker est requis. Installe-le et reconnecte-toi."
```

### Commande `/mods` (inchangée)

`list` · `player <joueur>` · `allow <mod-id>` · `ban <mod-id>` · `reset <mod-id>` · GUI au lancement
sans argument. Permission `modchecker.admin`.

## Persistance, erreurs, tests

- **Persistance :** `mods.json` dans le dossier du plugin — map `modId → statut`
  (`ALLOWED`/`BANNED`/`UNKNOWN`). Inchangé.
- **Robustesse :** repris de l'existant — données illisibles → warning (pas de crash), faux
  joueurs/bots (sans adresse réseau) exemptés, parsing JSON défensif.
- **Tests (plugin) :** `SanityTest` + tests unitaires sur le parsing de la liste de mods et la
  logique de statut (transitions BANNED/ALLOWED/UNKNOWN, détection mods bannis).

## Hors périmètre (YAGNI)

- Architectury / abstraction multi-loader lourde.
- Support Forge legacy.
- Channel configurable côté serveur (contrat figé volontairement).
- Réintégration dans Zeffut-SMP (les originaux restent en place, branchement éventuel = travail
  séparé plus tard).
