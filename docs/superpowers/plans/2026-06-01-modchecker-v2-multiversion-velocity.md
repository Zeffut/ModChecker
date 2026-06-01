# ModChecker v2 — Multi-version + Velocity + Tests (Implementation Plan)

> **For agentic workers:** exécuté en subagent-driven-development. Phases 1-3 et 6 séquentielles ;
> phases 4 et 5 parallélisées (un agent par version MC + un agent tests). Cases `- [ ]` = suivi.

**Goal:** Étendre ModChecker (déjà fonctionnel en 1.21.11 Paper) pour : (a) un plugin **Velocity** natif
qui enforce au proxy, (b) le support **mod ET plugin** sur `1.21.11, 26.1, 26.1.1, 26.1.2`, (c) une
**suite de tests exhaustive**.

**Architecture:** `server/` devient un Maven multi-module (`common` pur Java partagé, `paper`, `velocity`).
Le mod passe sous **Stonecutter** (multi-version, 2 loaders). Une matrice `build-all.sh` valide tout.

**État de départ (v1 fait, commité sur `feat/build-modchecker`):** `plugin/` = plugin Paper 1.21.11
fonctionnel (ModInfo, ModListCodec, ModChecker, ModCheckerGUI, PlayerListener, ModCheckerPlugin,
tests codec, jar OK). `PROTOCOL.md`, `README.md`, `docs/` présents.

**Coordonnées vérifiées (2026-06-01):**
- paper-api : `1.21.11-R0.1-SNAPSHOT`, `26.1.2.build.67-stable` (26.1/26.1.1 purgés → compat runtime).
- velocity-api : `3.4.0` (repo papermc).
- NeoForge : `21.11.42`, `26.1.0.19-beta`, `26.1.1.15-beta`, `26.1.2.70-beta`.
- Stonecutter : `dev.kikugie.stonecutter` `0.9.4`.
- MockBukkit : groupe `org.mockbukkit.mockbukkit:mockbukkit-v1.21` (agent vérifie l'artifact exact pour Paper 1.21.11).

**Prérequis machine :** JDK 21, Maven 3.9+, Gradle (wrapper généré). Réseau requis (téléchargements).
Pour toute commande `mvn`/`gradle` bloquée par le sandbox → relancer avec Bash `dangerouslyDisableSandbox: true`.

---

## Phase 1 — Restructurer `server/` en multi-module + extraire `common` [SÉQUENTIEL]

**But :** `plugin/` (Paper) → `server/paper/` ; extraire le protocole pur dans `server/common/` ;
parent pom ; remplacer les chaînes de statut par un enum `ModStatus`. Les tests restent verts.

### Task 1.1 — Parent pom + déplacer le plugin sous server/paper

- Create: `server/pom.xml` (parent, packaging `pom`, modules `common`, `paper`, `velocity`)
- Move: `plugin/` → `server/paper/` (git mv de tout le contenu) ; ajuster `server/paper/pom.xml`
  pour hériter du parent et dépendre de `common`.

- [ ] **Step 1 — Créer `server/pom.xml`** (parent) :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>fr.zeffut</groupId>
    <artifactId>modchecker-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <gson.version>2.11.0</gson.version>
        <junit.version>5.10.2</junit.version>
    </properties>

    <modules>
        <module>common</module>
        <module>paper</module>
        <module>velocity</module>
    </modules>

    <repositories>
        <repository><id>papermc</id><url>https://repo.papermc.io/repository/maven-public/</url></repository>
    </repositories>
</project>
```

- [ ] **Step 2 — Déplacer le plugin :**
```
cd /Users/zeffut/Desktop/Projets/ModChecker
git mv plugin server-paper-tmp
mkdir server
git mv server-paper-tmp server/paper
```
(But : `server/paper/` contient l'ancien `plugin/`. On ajuste son pom au Step suivant.)

- [ ] **Step 3 — Réécrire `server/paper/pom.xml`** pour hériter du parent, retirer ce qui monte
  dans le parent, dépendre de `common`, garder paper-api + gson(shade) + junit + MockBukkit (ajouté
  en Phase 5). artifactId `modchecker-paper`. Garder le shade (relocation gson) et `finalName`
  `ModChecker-${project.version}`. Acceptance : voir Task 1.3.

### Task 1.2 — Extraire `common` (ModInfo, ModListCodec, ModStatus, BanPolicy)

- Create: `server/common/pom.xml` (hérite parent ; deps : gson compile, junit test)
- Move: `ModInfo.java`, `ModListCodec.java`, `ModListCodecTest.java` depuis paper → common (package
  inchangé `fr.zeffut.modchecker`)
- Create: `server/common/.../ModStatus.java` (enum) + `BanPolicy.java` (logique pure) + tests
- Modify: `server/paper/.../ModChecker.java` + `ModCheckerGUI.java` pour utiliser `ModStatus`/`BanPolicy`

- [ ] **Step 1 — `server/common/pom.xml`** : artifactId `modchecker-common`, parent = modchecker-parent,
  deps gson (compile, version `${gson.version}`) + junit (test). Pas de shade (c'est `paper` qui shade).

- [ ] **Step 2 — `git mv`** `ModInfo.java`, `ModListCodec.java` (main) et `ModListCodecTest.java` (test)
  de `server/paper/src/...` vers `server/common/src/...` (mêmes chemins de package).

- [ ] **Step 3 — Créer `ModStatus.java`** dans common :
```java
package fr.zeffut.modchecker;

/** Statut d'un mod connu du serveur. */
public enum ModStatus {
    ALLOWED, BANNED, UNKNOWN;

    public static ModStatus fromString(String s) {
        try { return valueOf(s); } catch (Exception e) { return UNKNOWN; }
    }
}
```

- [ ] **Step 4 — Créer `BanPolicy.java`** dans common (logique pure, testable, partagée Paper/Velocity) :
```java
package fr.zeffut.modchecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logique pure de décision : quels mods d'une liste sont bannis, selon une map de statuts. */
public final class BanPolicy {

    private BanPolicy() {}

    /** Retourne les libellés "nom (id)" des mods BANNED présents dans la liste. */
    public static List<String> bannedAmong(List<ModInfo> mods, Map<String, ModStatus> statuses) {
        List<String> banned = new ArrayList<>();
        for (ModInfo mod : mods) {
            if (statuses.get(mod.id()) == ModStatus.BANNED) {
                banned.add(mod.name() + " (" + mod.id() + ")");
            }
        }
        return banned;
    }

    /** Vrai si la liste contient au moins un mod banni. */
    public static boolean hasBanned(List<ModInfo> mods, Map<String, ModStatus> statuses) {
        return mods.stream().anyMatch(m -> statuses.get(m.id()) == ModStatus.BANNED);
    }
}
```

- [ ] **Step 5 — Tests common** : ajouter `BanPolicyTest.java` (bannedAmong/hasBanned avec ALLOWED/
  BANNED/UNKNOWN, liste vide, mod inconnu) + `ModStatusTest.java` (fromString robuste). TDD : écrire,
  voir échouer, ces classes existent déjà donc juste vérifier vert.

- [ ] **Step 6 — Refactor `paper` pour consommer common** : dans `ModChecker.java`, remplacer la map
  `Map<String,String> modStatus` par `Map<String, ModStatus>` et la détection des bannis par
  `BanPolicy.bannedAmong(...)` ; remplacer la persistance (gson sérialise l'enum en String
  automatiquement). Dans `ModCheckerGUI.java`, remplacer les comparaisons `"ALLOWED"/"BANNED"` par
  `ModStatus`. **Garder le comportement identique** (mêmes messages, mêmes kicks). Conserver la
  compat du `mods.json` existant (valeurs `"ALLOWED"/"BANNED"/"UNKNOWN"` = noms d'enum → compat directe).

### Task 1.3 — Vérifier le build multi-module

- [ ] **Step 1 — `mvn -f server/pom.xml clean package`** : BUILD SUCCESS, common + paper construits,
  tous les tests verts (codec + BanPolicy + ModStatus + SanityTest), jar `server/paper/target/ModChecker-1.0.0.jar`
  contient gson relocalisé + plugin.yml + classes common.
  > Note : le module `velocity` doit exister pour que le parent build. En Phase 1, créer un
  > `server/velocity/pom.xml` minimal + une classe vide compilable, OU commenter le module velocity
  > dans le parent jusqu'à la Phase 2. **Choix : commenter `<module>velocity</module>` dans le parent
  > jusqu'à la Phase 2** pour garder la Phase 1 autonome.
- [ ] **Step 2 — Commit** : `refactor(server): multi-module Maven, common partagé (ModStatus, BanPolicy)`

---

## Phase 2 — Plugin Velocity [SÉQUENTIEL, après common]

**But :** module `server/velocity/` — plugin Velocity qui enforce le mod-check au proxy, réutilisant
`common`. **Agent : research-driven** (l'API Velocity 3.4.0 est à confirmer en lisant la doc/sources).

### Task 2.1 — Module velocity + plugin

- Create: `server/velocity/pom.xml` (parent ; deps : `com.velocitypowered:velocity-api:3.4.0` provided +
  annotation processor, `modchecker-common` compile, junit test ; shade gson + common si besoin)
- Create: `server/velocity/src/main/java/fr/zeffut/modchecker/velocity/ModCheckerVelocity.java`
- Create: config par défaut + `velocity-plugin.json` si requis (selon packaging Velocity)
- Décommenter `<module>velocity</module>` dans le parent pom.

**Contrat fonctionnel (acceptance) que l'agent doit réaliser :**
1. `@Plugin(id="modchecker", ...)`. Inject `ProxyServer`, `Logger`, `@DataDirectory Path`.
2. Enregistre 2 `MinecraftChannelIdentifier` : `modchecker:hello`, `modchecker:modlist`.
3. À la connexion joueur (`ServerPostConnectEvent` recommandé, sinon `PostLoginEvent`) : envoie le
   hello au joueur via `player.sendPluginMessage(HELLO_ID, ModListCodec.encode(version))`.
4. `@Subscribe PluginMessageEvent` : si identifier == modlist et source == joueur, décode via
   `common.ModListCodec`, applique `BanPolicy` sur la map de statuts chargée de la config ; si banni
   et non exempt → `player.disconnect(Component...)`. Marque l'event `setResult(forward=false)` pour
   ne pas relayer la liste au backend.
5. Config (`@DataDirectory`/<datadir>/config.* ) : map `modId→ModStatus`, `kick-without-mod`,
   `grace-period-seconds`, `server-name`, messages. Persistée (réutiliser gson + le format `mods.json`
   pour cohérence avec Paper, ou un YAML Velocity — au choix de l'agent, documenté).
6. (Optionnel v2) commande proxy `/mods` équivalente — **hors périmètre v2 si trop coûteux**, l'agent
   le note. La priorité est l'enforcement (hello + ban kick).

- [ ] **Step 1** — L'agent lit la doc Velocity 3.4.0 (plugin messaging, events, config, annotation
  processor) et implémente le contrat ci-dessus en réutilisant `common`.
- [ ] **Step 2 — Build** : `mvn -f server/pom.xml -pl velocity -am clean package` → jar
  `server/velocity/target/*.jar` chargeable par Velocity (contient `velocity-plugin.json` généré et
  les classes common). Tests logique verts.
- [ ] **Step 3 — Commit** : `feat(velocity): plugin proxy d'enforcement (handshake + ban kick)`

---

## Phase 3 — Scaffold Stonecutter du mod [SÉQUENTIEL]

**But :** transformer `mod/` (qui n'existe pas encore — v1 ne l'avait pas construit) en projet
Stonecutter multi-version, 2 loaders, source unique. **Agent : research-driven** (config Stonecutter).

### Task 3.1 — Scaffold Stonecutter + source Fabric/NeoForge

- Create: `mod/settings.gradle`, `mod/stonecutter.gradle`, `mod/build.gradle`, `mod/gradle.properties`
- Create: source Fabric (`HelloPayload`, `ModListPayload`, `ModCheckerFabric`) + `fabric.mod.json`
- Create: source NeoForge (`HelloPayload`, `ModListPayload`, `ModCheckerNeoForge`) + `neoforge.mods.toml`
- Versions Stonecutter déclarées : `1.21.11`, `26.1`, `26.1.1`, `26.1.2`. Version active par défaut : `1.21.11`.

**Contrat (acceptance) :**
1. Stonecutter `0.9.4` configuré avec les 4 versions et les 2 loaders (Fabric + NeoForge). Suivre la
   structure recommandée par Stonecutter (chisel/branches loader). L'agent résout le template exact.
2. Le **comportement réseau** est celui de v1 (déjà spécifié dans le plan v1, section mod) : recevoir
   `modchecker:hello` (S2C) → envoyer la liste des mods sur `modchecker:modlist` (C2S). Reprendre le
   code v1 Fabric/NeoForge (HelloPayload/ModListPayload/initializer) comme base.
3. Là où l'API réseau diffère entre 1.21.11 et 26.x, utiliser les directives Stonecutter `//? if ...`.
   L'agent détermine les deltas en compilant la version active 1.21.11 d'abord.
4. `gradle.properties` centralise par version : MC, yarn, loader, fabric-api, neoforge. Valeurs de
   départ (à confirmer/compléter par les agents Phase 4) :
   - `1.21.11` : neoforge `21.11.42` ; yarn/loader/fabric-api comme v1 (yarn `1.21.11+build.4`, loader `0.18.6`, fabric `0.141.3+1.21.11`).
   - `26.1` : neoforge `26.1.0.19-beta`.
   - `26.1.1` : neoforge `26.1.1.15-beta`.
   - `26.1.2` : neoforge `26.1.2.70-beta`.

- [ ] **Step 1** — Scaffold Stonecutter + wrapper Gradle (`gradle wrapper --gradle-version 8.12` ou la
  version requise par Stonecutter 0.9.4 ; l'agent vérifie la compat).
- [ ] **Step 2** — Porter le code réseau v1 (Fabric + NeoForge) dans la structure Stonecutter.
- [ ] **Step 3 — Build de la version active (1.21.11), 2 loaders** : produit
  `modchecker-fabric-1.21.11.jar` et `modchecker-neoforge-1.21.11.jar`.
- [ ] **Step 4 — Commit** : `feat(mod): scaffold Stonecutter multi-version (Fabric + NeoForge), 1.21.11 OK`

---

## Phase 4 — Validation par version [PARALLÈLE — 1 agent par version MC]

**But :** chaque agent prend une version (`1.21.11`, `26.1`, `26.1.1`, `26.1.2`), résout les mappings
exacts, ajuste les directives Stonecutter si l'API diffère, et **vérifie que les 2 loaders buildent**.

> Lancés en parallèle APRÈS la Phase 3 (le scaffold doit exister). Chaque agent travaille sur la même
> base mais sur sa version Stonecutter — risque de conflit sur `gradle.properties` : chaque agent ne
> modifie QUE le bloc de SA version. Le contrôleur sérialise les commits ou merge les blocs.

### Task 4.x — Pour chaque version V ∈ {1.21.11, 26.1, 26.1.1, 26.1.2}

- [ ] Résoudre yarn + loader + fabric-api + neoforge exacts pour V (endpoints Fabric/NeoForge).
- [ ] `./gradlew chiselV ... :fabric:build` (activer la version V) → `modchecker-fabric-V.jar`.
- [ ] `... :neoforge:build` pour V → `modchecker-neoforge-V.jar`.
- [ ] Si l'API réseau diffère de 1.21.11 (ex. renommage `CustomPayload`/`Identifier` en 26.x), ajouter
  les directives Stonecutter `//? if` minimales et documenter le delta.
- [ ] Reporter : versions de mappings utilisées + deltas API + jars produits (statuts DONE/BLOCKED).

**Acceptance Phase 4 :** les 8 jars (4 versions × 2 loaders) sont produits. Commit groupé par le
contrôleur : `feat(mod): builds vérifiés pour 1.21.11, 26.1, 26.1.1, 26.1.2 (Fabric + NeoForge)`.

---

## Phase 5 — Suite de tests exhaustive [PARALLÈLE — 1 agent dédié]

**But :** un agent construit toute la couche de tests automatisables + la matrice de build.

### Task 5.1 — Tests + matrice

- [ ] **common** : compléter les tests unitaires — ModListCodec (déjà), `BanPolicy`, `ModStatus`,
  cas limites (liste vide, mod inconnu, version absente). Cible : couverture des chemins de décision.
- [ ] **paper (MockBukkit)** : ajouter `org.mockbukkit.mockbukkit` (artifact pour Paper 1.21.11 à
  résoudre) en test scope. Écrire des tests d'intégration :
  - join d'un joueur → un paquet `modchecker:hello` est envoyé au joueur (vérifier via MockBukkit).
  - réception d'un `modchecker:modlist` → les mods passent en UNKNOWN, `getPlayerMods` renseigné.
  - un mod marqué BANNED + réception → le joueur est kické (sauf op/bypass).
  - `/mods ban <id>` puis join → kick ; `/mods allow` → pas de kick.
  - joueur avec permission `modchecker.bypass` → jamais kické.
- [ ] **velocity** : tests de la logique de décision (décodage modlist + `BanPolicy` → décision
  kick/laisser passer), sans réseau réel (extraire la décision dans une méthode pure testable).
- [ ] **`build-all.sh`** (racine) : script qui (1) `mvn -f server/pom.xml clean verify`, (2) pour chaque
  version × loader, `./gradlew` build du mod, (3) échoue non-zero si un seul target casse, (4) résume
  les artifacts produits. Exécutable et documenté dans le README.
- [ ] **Commit** : `test: suite exhaustive (common unit, paper MockBukkit, velocity logique, build-all.sh)`

**Acceptance Phase 5 :** `mvn -f server/pom.xml clean verify` vert (tous modules) ; `./build-all.sh`
sort 0 et liste tous les artifacts.

---

## Phase 6 — Finalisation [SÉQUENTIEL]

### Task 6.1 — Compat plugin 26.1.2 + README + merge

- [ ] **Vérifier compat plugin Paper 26.1.2** : profil Maven `-Dpaper.version=26.1.2.build.67-stable`
  (ou property) qui compile `paper` contre 26.1.2 ; si succès → un seul jar OK pour les 4 versions ;
  si rupture d'API → documenter et, au besoin, ajouter un shim minimal. Reporter le résultat.
- [ ] **README** : matrice de compatibilité (versions × Fabric/NeoForge × Paper/Purpur/Velocity),
  instructions de build (`mvn -f server/pom.xml package`, `./build-all.sh`), config des 2 plugins,
  commandes, checklist e2e (clients réels par version/loader/proxy).
- [ ] **PROTOCOL.md** : inchangé (le protocole ne change pas) — vérifier qu'il mentionne que Velocity
  applique le même contrat au proxy.
- [ ] **Commit + push** ; ouvrir une PR `feat/build-modchecker` → `main` (ou merge direct selon choix
  utilisateur via finishing-a-development-branch).

---

## Self-Review (rédaction)

- **Couverture exigences :** Velocity natif (Phase 2) ✓ · 4 versions mod (Phases 3-4, Stonecutter) ✓ ·
  4 versions plugin (compat runtime + compile 26.1.2 Phase 6) ✓ · tests exhaustifs (Phase 5) ✓ ·
  parallélisation par version (Phase 4) + agent tests (Phase 5) ✓.
- **Cohérence :** protocole single-sourcé dans `common` → Paper et Velocity partagent codec + BanPolicy,
  garantissant un comportement identique. `ModStatus` enum élimine les chaînes magiques (cleanup revue v1).
- **Zones déléguées (research-driven, non figées) :** API Velocity 3.4.0 exacte, template Stonecutter
  0.9.4, mappings Fabric par version, artifact MockBukkit. Chaque tâche concernée définit un **contrat
  d'acceptance** clair plutôt qu'un code ligne-à-ligne, car ces intégrations doivent être vérifiées
  contre les sources réelles au moment de l'implémentation. Les agents reportent DONE/BLOCKED.
- **Risque principal :** deltas d'API réseau Fabric/NeoForge entre 1.21.11 et 26.x. Mitigé par
  Stonecutter (`//? if`) et la validation parallèle par version (Phase 4) qui isole chaque delta.
