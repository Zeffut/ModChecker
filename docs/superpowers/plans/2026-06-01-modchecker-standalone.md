# ModChecker Standalone — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sortir le mod-checker de Zeffut-SMP en un projet standalone générique : un mod client multi-loader (Fabric + NeoForge) + un plugin serveur Purpur configurable.

**Architecture:** Monorepo `ModChecker/` avec `mod/` (Gradle multi-projet, sous-modules `fabric` et `neoforge` sans framework) et `plugin/` (Maven, Purpur). Mod et plugin partagent un contrat réseau figé en **handshake** : au join, le serveur envoie un paquet S2C `modchecker:hello` (preuve de présence du plugin) ; le client n'envoie sa liste de mods (C2S `modchecker:modlist`, String MC = tableau JSON `[{id,name,version}]`) **qu'en réponse à ce hello**. Le plugin découple toute logique Zeffut-SMP (combat-tag retiré, ProtocolLib retiré, branding dans `config.yml`).

**Tech Stack:** Java 21 · Plugin : Maven, Purpur API 1.21.11, Gson (shaded), JUnit 5 · Mod : Gradle, Fabric Loom (Fabric), ModDevGradle (NeoForge), MC 1.21.11.

**Sources de référence (à copier/adapter, restent intactes) :**
- Plugin base : `/Users/zeffut/Desktop/Projets/Zeffut-SMP/saison-3/Plugin/src/main/java/fr/zeffut/zeffsmp/ModChecker.java` et `ModCheckerGUI.java`
- Mod base : `/Users/zeffut/Desktop/Projets/Zeffut-SMP/saison-2/FabricMod/src/main/java/fr/zeffut/modchecker/`

**Prérequis machine :** JDK 21, Maven 3.9+, Gradle 8.10+ (ou wrapper généré). Les premiers builds Gradle téléchargent Minecraft/Loom/NeoForge (connexion requise).

---

## File Structure

```
ModChecker/
├── README.md                         ← présentation + build (Task 1/7)
├── PROTOCOL.md                       ← contrat réseau figé (Task 1)
├── .gitignore                        ← déjà créé
├── plugin/
│   ├── pom.xml                                                    (Task 2)
│   └── src/
│       ├── main/
│       │   ├── java/fr/zeffut/modchecker/
│       │   │   ├── ModInfo.java          record id/name/version   (Task 2)
│       │   │   ├── ModListCodec.java     encode/décode réseau pur (Task 2)
│       │   │   ├── ModChecker.java       cœur (listener+cmd+tab)  (Task 3)
│       │   │   ├── ModCheckerGUI.java    interface inventaire     (Task 3)
│       │   │   ├── PlayerListener.java   join/quit → ModChecker   (Task 3)
│       │   │   └── ModCheckerPlugin.java JavaPlugin principal     (Task 3)
│       │   └── resources/
│       │       ├── plugin.yml                                     (Task 2)
│       │       └── config.yml                                     (Task 2)
│       └── test/java/fr/zeffut/modchecker/
│           ├── ModListCodecTest.java                              (Task 2)
│           └── SanityTest.java                                    (Task 3)
└── mod/
    ├── settings.gradle / gradle.properties                       (Task 4)
    ├── fabric/
    │   ├── build.gradle                                          (Task 5)
    │   └── src/main/{java/fr/zeffut/modchecker/fabric, resources}(Task 5)
    └── neoforge/
        ├── build.gradle                                          (Task 6)
        └── src/main/{java/fr/zeffut/modchecker/neoforge, resources/META-INF} (Task 6)
```

---

## Task 1 : Documentation du contrat réseau

**Files:**
- Create: `PROTOCOL.md`
- Create: `README.md` (version initiale)

- [ ] **Step 1 : Écrire `PROTOCOL.md`**

```markdown
# Protocole ModChecker

Contrat **figé** partagé par le mod client et le plugin serveur. Les channels ne sont PAS
configurables côté serveur : le mod est compilé avec, les deux côtés doivent rester synchrones.

## Handshake

Le client ne révèle ses mods qu'à un serveur qui a prouvé avoir le plugin :

```
joueur rejoint
      │
serveur ──[ modchecker:hello (S2C) ]──▶ client          (preuve : le plugin est là)
      │
client ──[ modchecker:modlist (C2S) ]──▶ serveur         (réponse : voici mes mods)
```

- Pas de hello reçu (plugin absent / serveur vanilla) → le client n'envoie **rien**.
- Client vanilla (sans le mod) → reçoit le hello sur un channel inconnu, l'**ignore** silencieusement.
- Pas de modlist reçue dans le délai serveur → le joueur est considéré sans mod.

## Channels

| Channel | Sens | Quand | Payload |
|---------|------|-------|---------|
| `modchecker:hello` | Serveur → Client (S2C) | au join | version du plugin (String MC) |
| `modchecker:modlist` | Client → Serveur (C2S) | en réponse au hello | tableau JSON des mods (String MC) |

## Payload `modchecker:modlist`
Un **String Minecraft unique** : `VarInt(longueur UTF-8)` suivi des octets UTF-8.
Le contenu du String est un tableau JSON :

```json
[
  {"id": "fabric-api", "name": "Fabric API", "version": "0.141.3"},
  {"id": "sodium", "name": "Sodium", "version": "0.6.0"}
]
```

- `id` (obligatoire) : identifiant du mod
- `name` (obligatoire) : nom lisible
- `version` (optionnel) : version ; absent → le serveur stocke `"?"`

## Payload `modchecker:hello`
Un **String Minecraft unique** = la version du plugin (ex. `"1.0.0"`). Le client peut l'ignorer
aujourd'hui ; il sert de point d'extension pour une vérification de compatibilité future.

## Encodage (même format binaire des deux côtés)
- **Fabric** : `PacketCodecs.STRING` (= VarInt + UTF-8).
- **NeoForge** : `ByteBufCodecs.STRING_UTF8` (= VarInt + UTF-8).
- **Plugin** : `ModListCodec.decode(byte[])` (réception modlist) et `ModListCodec.encode(String)`
  (envoi hello), tous deux en VarInt + UTF-8.
```

- [ ] **Step 2 : Écrire `README.md` (version initiale)**

```markdown
# ModChecker

Mod-checker Minecraft générique : un **mod client** (Fabric + NeoForge) envoie au serveur la
liste des mods installés, un **plugin serveur** (Purpur/Paper) les enregistre, permet de les
autoriser/bannir et kicke les joueurs porteurs d'un mod banni.

Voir [PROTOCOL.md](PROTOCOL.md) pour le contrat réseau.

## Structure
- `mod/` — mod client multi-loader (`mod/fabric`, `mod/neoforge`)
- `plugin/` — plugin serveur Maven

## Build (rempli au fil de l'implémentation)
```

- [ ] **Step 3 : Commit**

```bash
git add PROTOCOL.md README.md
git commit -m "docs: contrat réseau (PROTOCOL.md) et README initial"
```

---

## Task 2 : Plugin — squelette Maven + protocole pur (TDD)

**Files:**
- Create: `plugin/pom.xml`
- Create: `plugin/src/main/resources/plugin.yml`
- Create: `plugin/src/main/resources/config.yml`
- Create: `plugin/src/main/java/fr/zeffut/modchecker/ModInfo.java`
- Create: `plugin/src/main/java/fr/zeffut/modchecker/ModListCodec.java`
- Test: `plugin/src/test/java/fr/zeffut/modchecker/ModListCodecTest.java`

- [ ] **Step 1 : Créer `plugin/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>fr.zeffut</groupId>
    <artifactId>modchecker</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>purpur</id>
            <url>https://repo.purpurmc.org/snapshots</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.purpurmc.purpur</groupId>
            <artifactId>purpur-api</artifactId>
            <version>1.21.11-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>ModChecker-${project.version}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <createDependencyReducedPom>false</createDependencyReducedPom>
                    <relocations>
                        <relocation>
                            <pattern>com.google.gson</pattern>
                            <shadedPattern>fr.zeffut.modchecker.libs.gson</shadedPattern>
                        </relocation>
                    </relocations>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

> Note : Gson est shadé+relocalisé pour garantir sa présence au runtime quelle que soit la
> distribution serveur.

- [ ] **Step 2 : Créer `plugin/src/main/resources/plugin.yml`**

```yaml
name: ModChecker
version: 1.0.0
main: fr.zeffut.modchecker.ModCheckerPlugin
api-version: "1.21"
description: Vérifie les mods des joueurs et bannit ceux interdits
authors: [Zeffut]

commands:
  mods:
    description: Gestion des mods autorisés/bannis
    usage: /mods <list|player|allow|ban|reset>
    permission: modchecker.admin

permissions:
  modchecker.admin:
    description: Accès aux commandes de gestion des mods
    default: op
  modchecker.bypass:
    description: Exempte le joueur du mod-checker
    default: false
```

- [ ] **Step 3 : Créer `plugin/src/main/resources/config.yml`**

```yaml
# Nom affiché en en-tête des messages de kick
server-name: "Mon Serveur"
# Kicker les joueurs qui n'envoient pas leur liste de mods (mod-checker absent)
kick-without-mod: false
# Délai d'attente (secondes) avant de considérer le mod-checker absent
grace-period-seconds: 5
# Permission qui exempte du mod-checker (les OP sont toujours exemptés)
bypass-permission: "modchecker.bypass"
messages:
  banned-mod: "Mod(s) interdit(s) détecté(s) :"
  missing-mod: "Le mod ModChecker est requis. Installe-le et reconnecte-toi."
```

- [ ] **Step 4 : Créer `plugin/src/main/java/fr/zeffut/modchecker/ModInfo.java`**

```java
package fr.zeffut.modchecker;

/** Un mod détecté côté client. */
public record ModInfo(String id, String name, String version) {}
```

- [ ] **Step 5 : Écrire le test qui échoue `plugin/src/test/java/fr/zeffut/modchecker/ModListCodecTest.java`**

```java
package fr.zeffut.modchecker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModListCodecTest {

    @Test
    void encodeWritesVarIntLengthThenUtf8() {
        // "ABC" → longueur 3 (VarInt 0x03) puis 0x41 0x42 0x43
        assertArrayEquals(new byte[]{0x03, 0x41, 0x42, 0x43}, ModListCodec.encode("ABC"));
    }

    @Test
    void encodeDecodeRoundTrip() {
        String json = "[{\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.1\"}]";
        assertEquals(json, ModListCodec.decode(ModListCodec.encode(json)));
    }

    @Test
    void decodeReturnsNullOnTruncatedData() {
        // VarInt annonce une longueur que les données ne contiennent pas
        assertNull(ModListCodec.decode(new byte[]{(byte) 0x05, (byte) 0x41}));
    }

    @Test
    void parsesModListWithAndWithoutVersion() {
        List<ModInfo> mods = ModListCodec.parse(
                "[{\"id\":\"a\",\"name\":\"Alpha\",\"version\":\"1.0\"},{\"id\":\"b\",\"name\":\"Beta\"}]");
        assertNotNull(mods);
        assertEquals(2, mods.size());
        assertEquals("a", mods.get(0).id());
        assertEquals("Alpha", mods.get(0).name());
        assertEquals("1.0", mods.get(0).version());
        assertEquals("?", mods.get(1).version());
    }

    @Test
    void parseReturnsNullOnInvalidJson() {
        assertNull(ModListCodec.parse("pas du json"));
    }
}
```

- [ ] **Step 6 : Lancer le test pour le voir échouer**

Run: `mvn -q -f plugin/pom.xml test`
Expected: ÉCHEC de compilation — `cannot find symbol: ModListCodec`.

- [ ] **Step 7 : Implémenter `plugin/src/main/java/fr/zeffut/modchecker/ModListCodec.java`**

```java
package fr.zeffut.modchecker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Encodage/décodage du paquet réseau ModChecker — sans dépendance Bukkit, testable unitairement. */
public final class ModListCodec {

    private ModListCodec() {}

    /** Encode un String au format MC (VarInt longueur + UTF-8) — utilisé pour le paquet hello. */
    public static byte[] encode(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int value = utf8.length;
        while (true) {
            if ((value & ~0x7F) == 0) { out.write(value); break; }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeBytes(utf8);
        return out.toByteArray();
    }

    /** Décode un String encodé au format MC (VarInt longueur + UTF-8). Null si illisible. */
    public static String decode(byte[] data) {
        try {
            int index = 0, length = 0, shift = 0;
            byte b;
            do {
                if (index >= data.length) return null;
                b = data[index++];
                length |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            if (index + length > data.length) return null;
            return new String(data, index, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse le tableau JSON de mods. Null si invalide. */
    public static List<ModInfo> parse(String json) {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<ModInfo> list = new ArrayList<>();
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                list.add(new ModInfo(
                        obj.get("id").getAsString(),
                        obj.get("name").getAsString(),
                        obj.has("version") ? obj.get("version").getAsString() : "?"
                ));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 8 : Lancer les tests pour les voir passer**

Run: `mvn -q -f plugin/pom.xml test`
Expected: PASS (5 tests OK).

- [ ] **Step 9 : Commit**

```bash
git add plugin/pom.xml plugin/src/main/resources plugin/src/main/java plugin/src/test
git commit -m "feat(plugin): squelette Maven + protocole pur (ModInfo, ModListCodec) testé"
```

---

## Task 3 : Plugin — cœur découplé (ModChecker, GUI, listener, main)

**Files:**
- Create: `plugin/src/main/java/fr/zeffut/modchecker/ModChecker.java`
- Create: `plugin/src/main/java/fr/zeffut/modchecker/ModCheckerGUI.java`
- Create: `plugin/src/main/java/fr/zeffut/modchecker/PlayerListener.java`
- Create: `plugin/src/main/java/fr/zeffut/modchecker/ModCheckerPlugin.java`
- Test: `plugin/src/test/java/fr/zeffut/modchecker/SanityTest.java`

- [ ] **Step 1 : Créer `ModChecker.java` (version découplée + config)**

Différences vs l'original Zeffut-SMP : package `fr.zeffut.modchecker` ; type `ModCheckerPlugin` ;
channel `modchecker:modlist` ; `ModInfo`/décodage délégués à `ModListCodec` (accesseurs `mod.id()`…) ;
ligne `plugin.getCombatManager().removeCombat(...)` **supprimée** ; messages/branding/grace-period/bypass
lus depuis `config.yml`.

```java
package fr.zeffut.modchecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModChecker implements PluginMessageListener, CommandExecutor, TabCompleter {

    /** C2S : le client envoie sa liste de mods sur ce channel (en réponse au hello). */
    public static final String CHANNEL = "modchecker:modlist";
    /** S2C : le serveur s'annonce au client (handshake) sur ce channel. */
    public static final String HELLO_CHANNEL = "modchecker:hello";

    private final ModCheckerPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File modsFile;

    private final Map<String, String> modStatus = new ConcurrentHashMap<>();
    private final Map<UUID, List<ModInfo>> playerMods = new ConcurrentHashMap<>();
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    private final String serverName;
    private final boolean kickWithoutMod;
    private final long gracePeriodTicks;
    private final String bypassPermission;
    private final String bannedMsg;
    private final String missingMsg;
    private final String pluginVersion;

    private ModCheckerGUI gui;

    public ModChecker(ModCheckerPlugin plugin) {
        this.plugin = plugin;
        this.modsFile = new File(plugin.getDataFolder(), "mods.json");
        this.pluginVersion = plugin.getPluginMeta().getVersion();
        var cfg = plugin.getConfig();
        this.serverName = cfg.getString("server-name", "Serveur");
        this.kickWithoutMod = cfg.getBoolean("kick-without-mod", false);
        this.gracePeriodTicks = Math.max(1, cfg.getLong("grace-period-seconds", 5)) * 20L;
        this.bypassPermission = cfg.getString("bypass-permission", "modchecker.bypass");
        this.bannedMsg = cfg.getString("messages.banned-mod", "Mod(s) interdit(s) détecté(s) :");
        this.missingMsg = cfg.getString("messages.missing-mod",
                "Le mod ModChecker est requis. Installe-le et reconnecte-toi.");
        load();
    }

    public void setGui(ModCheckerGUI gui) { this.gui = gui; }

    private boolean isExempt(Player player) {
        return player.isOp() || player.hasPermission(bypassPermission);
    }

    private Component header() {
        return Component.text("\n")
                .append(Component.text("  " + serverName + "  ", NamedTextColor.RED))
                .append(Component.text("\n\n"));
    }

    // ─── Réception liste mods ───────────────────────────────────────────────

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] data) {
        if (!channel.equals(CHANNEL)) return;

        String json = ModListCodec.decode(data);
        if (json == null) {
            plugin.getLogger().warning("Données illisibles reçues de " + player.getName());
            return;
        }
        List<ModInfo> mods = ModListCodec.parse(json);
        if (mods == null) {
            plugin.getLogger().warning("Liste mods invalide reçue de " + player.getName());
            return;
        }

        pendingPlayers.remove(player.getUniqueId());
        playerMods.put(player.getUniqueId(), mods);

        boolean newModsFound = false;
        for (ModInfo mod : mods) {
            if (!modStatus.containsKey(mod.id())) {
                modStatus.put(mod.id(), "UNKNOWN");
                newModsFound = true;
            }
        }
        if (newModsFound) save();

        List<String> bannedMods = new ArrayList<>();
        for (ModInfo mod : mods) {
            if ("BANNED".equals(modStatus.get(mod.id()))) {
                bannedMods.add(mod.name() + " (" + mod.id() + ")");
            }
        }

        if (!bannedMods.isEmpty() && !isExempt(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(header()
                    .append(Component.text(bannedMsg, NamedTextColor.RED))
                    .append(Component.text("\n"))
                    .append(Component.text(String.join(", ", bannedMods), NamedTextColor.GRAY))));
            plugin.getLogger().info("Joueur " + player.getName() + " kické — mods bannis : " + bannedMods);
        } else if (!bannedMods.isEmpty()) {
            plugin.getLogger().info("Mods bannis ignorés pour " + player.getName() + " : " + bannedMods);
        } else {
            plugin.getLogger().info("Mods de " + player.getName() + " : " + mods.size() + " mod(s) détecté(s).");
        }
    }

    /** Vrai pour un faux joueur (bot NMS sans connexion réseau) : exempté du mod-checker. */
    private static boolean isFakePlayer(Player player) {
        return player.getAddress() == null;
    }

    public void onPlayerJoin(Player player) {
        if (isFakePlayer(player)) return;
        pendingPlayers.add(player.getUniqueId());

        // Handshake : annoncer la présence du plugin au client. Seul un client qui a le mod répondra
        // (sur CHANNEL). Un client vanilla ignore ce paquet ; un serveur sans plugin n'en envoie pas.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendPluginMessage(plugin, HELLO_CHANNEL, ModListCodec.encode(pluginVersion));
            }
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (pendingPlayers.contains(player.getUniqueId())) {
                pendingPlayers.remove(player.getUniqueId());
                if (kickWithoutMod && !isExempt(player)) {
                    player.kick(header().append(Component.text(missingMsg, NamedTextColor.GRAY)));
                    plugin.getLogger().info(player.getName() + " kické — mod checker absent.");
                } else {
                    plugin.getLogger().warning(player.getName() + " n'a pas le mod checker installé.");
                }
            }
        }, gracePeriodTicks);
    }

    public Map<String, String> getModStatus() { return modStatus; }

    public List<ModInfo> getPlayerMods(UUID uuid) { return playerMods.get(uuid); }

    /** Change le statut d'un mod et kick les joueurs concernés si banni. */
    public void setStatus(String modId, String status) {
        modStatus.put(modId, status);
        save();
        if ("BANNED".equals(status)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                List<ModInfo> mods = playerMods.get(p.getUniqueId());
                if (mods != null && !isExempt(p) && mods.stream().anyMatch(m -> m.id().equals(modId))) {
                    p.kick(header().append(Component.text("Le mod " + modId + " a été banni.", NamedTextColor.GRAY)));
                }
            }
        }
    }

    public void onPlayerQuit(UUID uuid) {
        pendingPlayers.remove(uuid);
        playerMods.remove(uuid);
    }

    // ─── Commande /mods ─────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && gui != null) {
                gui.openModList(player, 0);
            } else {
                sendUsage(sender);
            }
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "list" -> cmdList(sender);
            case "player" -> cmdPlayer(sender, args);
            case "allow" -> cmdSetStatus(sender, args, "ALLOWED");
            case "ban" -> cmdSetStatus(sender, args, "BANNED");
            case "reset" -> cmdSetStatus(sender, args, "UNKNOWN");
            default -> { sendUsage(sender); yield true; }
        };
    }

    private boolean cmdList(CommandSender sender) {
        if (modStatus.isEmpty()) {
            sender.sendMessage(Component.text("Aucun mod enregistré.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("=== Mods enregistrés ===", NamedTextColor.GOLD));
        List<String> sorted = new ArrayList<>(modStatus.keySet());
        Collections.sort(sorted);
        for (String modId : sorted) {
            String status = modStatus.get(modId);
            NamedTextColor color = statusColor(status);
            String icon = switch (status) { case "ALLOWED" -> "✔"; case "BANNED" -> "✘"; default -> "?"; };
            sender.sendMessage(Component.text(" " + icon + " ", color)
                    .append(Component.text(modId, NamedTextColor.WHITE))
                    .append(Component.text(" [" + status + "]", color)));
        }
        return true;
    }

    private boolean cmdPlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage : /mods player <joueur>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Joueur introuvable ou hors ligne.", NamedTextColor.RED));
            return true;
        }
        List<ModInfo> mods = playerMods.get(target.getUniqueId());
        if (mods == null) {
            sender.sendMessage(Component.text(target.getName() + " n'a pas envoyé sa liste de mods.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("=== Mods de " + target.getName() + " (" + mods.size() + ") ===", NamedTextColor.GOLD));
        for (ModInfo mod : mods) {
            String status = modStatus.getOrDefault(mod.id(), "UNKNOWN");
            NamedTextColor color = statusColor(status);
            sender.sendMessage(Component.text("  ", color)
                    .append(Component.text(mod.name(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + mod.id() + " v" + mod.version() + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" [" + status + "]", color)));
        }
        return true;
    }

    private boolean cmdSetStatus(CommandSender sender, String[] args, String status) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage : /mods " + args[0] + " <mod-id>", NamedTextColor.RED));
            return true;
        }
        String modId = args[1].toLowerCase();
        setStatus(modId, status);
        sender.sendMessage(Component.text(modId, NamedTextColor.WHITE)
                .append(Component.text(" → " + status, statusColor(status))));
        return true;
    }

    private static NamedTextColor statusColor(String status) {
        return switch (status) {
            case "ALLOWED" -> NamedTextColor.GREEN;
            case "BANNED" -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
    }

    // ─── Tab completion ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("list", "player", "allow", "ban", "reset");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "player" -> {
                    String partial = args[1].toLowerCase();
                    yield Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(partial)).toList();
                }
                case "allow", "ban", "reset" -> {
                    String partial = args[1].toLowerCase();
                    yield modStatus.keySet().stream().filter(id -> id.startsWith(partial)).sorted().toList();
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    // ─── Persistance ────────────────────────────────────────────────────────

    private void load() {
        if (!modsFile.exists()) return;
        try (FileReader reader = new FileReader(modsFile)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) modStatus.putAll(loaded);
        } catch (IOException e) {
            plugin.getLogger().warning("Erreur lecture mods.json : " + e.getMessage());
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(modsFile)) {
            gson.toJson(modStatus, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Erreur écriture mods.json : " + e.getMessage());
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== Mod Checker ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/mods list — Liste des mods enregistrés", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mods player <joueur> — Mods d'un joueur", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mods allow <mod-id> — Autoriser un mod", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mods ban <mod-id> — Bannir un mod", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/mods reset <mod-id> — Remettre en inconnu", NamedTextColor.YELLOW));
    }
}
```

- [ ] **Step 2 : Créer `ModCheckerGUI.java`**

Copier le fichier original puis appliquer 3 modifs (package + références `ModInfo`) :

```bash
cp "/Users/zeffut/Desktop/Projets/Zeffut-SMP/saison-3/Plugin/src/main/java/fr/zeffut/zeffsmp/ModCheckerGUI.java" \
   plugin/src/main/java/fr/zeffut/modchecker/ModCheckerGUI.java
```

Puis éditer le fichier copié :
- Ligne 1 : `package fr.zeffut.zeffsmp;` → `package fr.zeffut.modchecker;`
- Dans `openPlayerMods` : `List<ModChecker.ModInfo> mods` → `List<ModInfo> mods`
- Dans `openPlayerMods` boucle : `ModChecker.ModInfo mod = mods.get(i);` → `ModInfo mod = mods.get(i);`
- Dans `handlePlayerModsClick` : `List<ModChecker.ModInfo> mods` → `List<ModInfo> mods`

(Les appels `mod.id()`, `mod.name()`, `mod.version()` utilisent déjà les accesseurs : rien d'autre à changer.)

- [ ] **Step 3 : Créer `PlayerListener.java`**

```java
package fr.zeffut.modchecker;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ModChecker modChecker;

    public PlayerListener(ModChecker modChecker) {
        this.modChecker = modChecker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        modChecker.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        modChecker.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
```

- [ ] **Step 4 : Créer `ModCheckerPlugin.java`**

```java
package fr.zeffut.modchecker;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ModCheckerPlugin extends JavaPlugin {

    private ModChecker modChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        modChecker = new ModChecker(this);
        ModCheckerGUI gui = new ModCheckerGUI(modChecker);
        modChecker.setGui(gui);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(modChecker), this);

        // Réception de la liste de mods (C2S) + envoi du hello (S2C)
        getServer().getMessenger().registerIncomingPluginChannel(this, ModChecker.CHANNEL, modChecker);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModChecker.HELLO_CHANNEL);

        PluginCommand cmd = getCommand("mods");
        if (cmd != null) {
            cmd.setExecutor(modChecker);
            cmd.setTabCompleter(modChecker);
        }

        getLogger().info("ModChecker activé (channel " + ModChecker.CHANNEL + ").");
    }

    public ModChecker getModChecker() { return modChecker; }
}
```

- [ ] **Step 5 : Créer `SanityTest.java`**

```java
package fr.zeffut.modchecker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SanityTest {
    @Test
    void smoke() {
        assertEquals(2, 1 + 1);
    }
}
```

- [ ] **Step 6 : Compiler + packager le plugin**

Run: `mvn -q -f plugin/pom.xml clean package`
Expected: BUILD SUCCESS, tests verts, jar produit `plugin/target/ModChecker-1.0.0.jar`.

- [ ] **Step 7 : Vérifier que le jar contient Gson relocalisé et le plugin.yml**

Run: `unzip -l plugin/target/ModChecker-1.0.0.jar | grep -E "plugin.yml|fr/zeffut/modchecker/libs/gson|ModCheckerPlugin.class"`
Expected: les trois apparaissent (plugin.yml, classes gson relocalisées, ModCheckerPlugin.class).

- [ ] **Step 8 : Commit**

```bash
git add plugin/src
git commit -m "feat(plugin): cœur ModChecker découplé de Zeffut-SMP (config, GUI, listener, main)"
```

---

## Task 4 : Mod — squelette Gradle multi-projet

**Files:**
- Create: `mod/settings.gradle`
- Create: `mod/gradle.properties`

- [ ] **Step 1 : Créer `mod/settings.gradle`**

```groovy
pluginManagement {
    repositories {
        maven { url = "https://maven.fabricmc.net/" }
        maven { url = "https://maven.neoforged.net/releases" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "modchecker-mod"
include("fabric")
include("neoforge")
```

- [ ] **Step 2 : Créer `mod/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2G

mod_version=1.0.0
minecraft_version=1.21.11

# Fabric
yarn_mappings=1.21.11+build.4
loader_version=0.18.6
fabric_version=0.141.3+1.21.11

# NeoForge — vérifier la version exacte pour 1.21.11 (voir Step 3)
neoforge_version=21.11.0
```

- [ ] **Step 3 : Vérifier la version NeoForge disponible pour 1.21.11**

Run: `curl -s "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge" | tr ',' '\n' | grep '^"21.11' | tail -5`
Expected: une ou plusieurs versions `21.11.x.y`. Mettre la plus récente stable dans `neoforge_version`
(ex. `21.11.7`). Si aucune version `21.11` n'existe encore, prendre la dernière `21.x` compatible
1.21.11 et ajuster `minecraft_version` en conséquence.

- [ ] **Step 4 : Générer le wrapper Gradle**

Run: `cd mod && gradle wrapper --gradle-version 8.10 && cd ..`
Expected: création de `mod/gradlew`, `mod/gradle/wrapper/*`. (Nécessite un Gradle local : `brew install gradle` si absent.)

- [ ] **Step 5 : Commit**

```bash
git add mod/settings.gradle mod/gradle.properties mod/gradlew mod/gradlew.bat mod/gradle
git commit -m "chore(mod): squelette Gradle multi-projet (fabric + neoforge)"
```

---

## Task 5 : Mod — module Fabric

**Files:**
- Create: `mod/fabric/build.gradle`
- Create: `mod/fabric/src/main/java/fr/zeffut/modchecker/fabric/ModListPayload.java`
- Create: `mod/fabric/src/main/java/fr/zeffut/modchecker/fabric/HelloPayload.java`
- Create: `mod/fabric/src/main/java/fr/zeffut/modchecker/fabric/ModCheckerFabric.java`
- Create: `mod/fabric/src/main/resources/fabric.mod.json`

- [ ] **Step 1 : Créer `mod/fabric/build.gradle`**

```groovy
plugins {
    id "fabric-loom" version "1.13.6"
    id "java"
}

version = project.mod_version
group = "fr.zeffut"
base { archivesName = "modchecker-fabric" }

repositories { mavenCentral() }

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType(JavaCompile).configureEach { options.encoding = "UTF-8" }
```

- [ ] **Step 2 : Créer `ModListPayload.java` (Fabric)**

```java
package fr.zeffut.modchecker.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModListPayload(String json) implements CustomPayload {

    public static final Id<ModListPayload> ID = new Id<>(Identifier.of("modchecker", "modlist"));

    public static final PacketCodec<RegistryByteBuf, ModListPayload> CODEC =
            PacketCodecs.STRING.xmap(ModListPayload::new, ModListPayload::json).cast();

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

- [ ] **Step 2b : Créer `HelloPayload.java` (Fabric, S2C)**

```java
package fr.zeffut.modchecker.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record HelloPayload(String version) implements CustomPayload {

    public static final Id<HelloPayload> ID = new Id<>(Identifier.of("modchecker", "hello"));

    public static final PacketCodec<RegistryByteBuf, HelloPayload> CODEC =
            PacketCodecs.STRING.xmap(HelloPayload::new, HelloPayload::version).cast();

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
```

- [ ] **Step 3 : Créer `ModCheckerFabric.java`**

On enregistre le payload S2C `hello` et un receiver : **à réception du hello**, on construit la
liste des mods et on l'envoie. Plus d'envoi automatique au join.

```java
package fr.zeffut.modchecker.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class ModCheckerFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(HelloPayload.ID, HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ModListPayload.ID, ModListPayload.CODEC);

        // Le serveur a le plugin (hello reçu) → on lui envoie notre liste de mods
        ClientPlayNetworking.registerGlobalReceiver(HelloPayload.ID, (payload, context) -> {
            JsonArray mods = new JsonArray();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", mod.getMetadata().getId());
                entry.addProperty("name", mod.getMetadata().getName());
                entry.addProperty("version", mod.getMetadata().getVersion().getFriendlyString());
                mods.add(entry);
            }
            context.responseSender().sendPacket(new ModListPayload(mods.toString()));
        });
    }
}
```

- [ ] **Step 4 : Créer `mod/fabric/src/main/resources/fabric.mod.json`**

```json
{
  "schemaVersion": 1,
  "id": "modchecker",
  "version": "1.0.0",
  "name": "ModChecker",
  "description": "Envoie la liste des mods au serveur (mod-checker générique)",
  "authors": ["Zeffut"],
  "environment": "client",
  "entrypoints": {
    "client": ["fr.zeffut.modchecker.fabric.ModCheckerFabric"]
  },
  "depends": {
    "fabricloader": ">=0.18.0",
    "fabric-api": "*",
    "minecraft": "~1.21.11"
  }
}
```

- [ ] **Step 5 : Builder le module Fabric**

Run: `cd mod && ./gradlew :fabric:build && cd ..`
Expected: BUILD SUCCESSFUL, jar dans `mod/fabric/build/libs/modchecker-fabric-1.0.0.jar`. (Premier build = téléchargement MC/Loom, peut être long.)

- [ ] **Step 6 : Commit**

```bash
git add mod/fabric
git commit -m "feat(mod): module client Fabric (envoi liste mods sur modchecker:modlist)"
```

---

## Task 6 : Mod — module NeoForge

**Files:**
- Create: `mod/neoforge/build.gradle`
- Create: `mod/neoforge/src/main/java/fr/zeffut/modchecker/neoforge/ModListPayload.java`
- Create: `mod/neoforge/src/main/java/fr/zeffut/modchecker/neoforge/HelloPayload.java`
- Create: `mod/neoforge/src/main/java/fr/zeffut/modchecker/neoforge/ModCheckerNeoForge.java`
- Create: `mod/neoforge/src/main/resources/META-INF/neoforge.mods.toml`

> Note multi-loader : NeoForge utilise les mappings officiels Mojang (classes `ResourceLocation`,
> `FriendlyByteBuf`…), différentes des noms Yarn de Fabric. Le format binaire sur le fil reste
> identique (VarInt + UTF-8). Les signatures NeoForge ci-dessous ciblent la branche 1.21.x ; si la
> version NeoForge résolue diffère, ajuster les imports au Step 5 (le build le signalera).

- [ ] **Step 1 : Créer `mod/neoforge/build.gradle`**

```groovy
plugins {
    id "net.neoforged.moddev" version "2.0.78"
    id "java"
}

version = project.mod_version
group = "fr.zeffut"
base { archivesName = "modchecker-neoforge" }

neoForge {
    version = project.neoforge_version
    mods {
        modchecker {
            sourceSet sourceSets.main
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType(JavaCompile).configureEach { options.encoding = "UTF-8" }
```

> Si la version `2.0.78` du plugin `net.neoforged.moddev` n'est pas résolue, prendre la dernière
> sur https://projects.neoforged.net/neoforged/ModDevGradle .

- [ ] **Step 2 : Créer `ModListPayload.java` (NeoForge)**

```java
package fr.zeffut.modchecker.neoforge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModListPayload(String json) implements CustomPacketPayload {

    public static final Type<ModListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("modchecker", "modlist"));

    public static final StreamCodec<FriendlyByteBuf, ModListPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ModListPayload::json,
                    ModListPayload::new);

    @Override
    public Type<ModListPayload> type() { return TYPE; }
}
```

- [ ] **Step 2b : Créer `HelloPayload.java` (NeoForge, S2C)**

```java
package fr.zeffut.modchecker.neoforge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HelloPayload(String version) implements CustomPacketPayload {

    public static final Type<HelloPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("modchecker", "hello"));

    public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, HelloPayload::version,
                    HelloPayload::new);

    @Override
    public Type<HelloPayload> type() { return TYPE; }
}
```

- [ ] **Step 3 : Créer `ModCheckerNeoForge.java`**

Payloads enregistrés en `optional()` (dialogue avec un serveur **non-NeoForge** / Purpur). Le
handler `playToClient` du hello prouve que le plugin est présent → on répond avec la liste des mods.
Plus d'envoi automatique au login.

```java
package fr.zeffut.modchecker.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;

@Mod(value = "modchecker", dist = Dist.CLIENT)
public class ModCheckerNeoForge {

    public ModCheckerNeoForge(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0").optional();
        // hello (S2C) : le serveur a le plugin → on répond avec la liste des mods
        registrar.playToClient(HelloPayload.TYPE, HelloPayload.CODEC,
                (payload, context) -> PacketDistributor.sendToServer(buildModList()));
        // modlist (C2S) : déclaration du channel d'envoi, pas de réception côté client
        registrar.playToServer(ModListPayload.TYPE, ModListPayload.CODEC, (payload, context) -> {});
    }

    private static ModListPayload buildModList() {
        JsonArray mods = new JsonArray();
        for (IModInfo mod : ModList.get().getMods()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mod.getModId());
            entry.addProperty("name", mod.getDisplayName());
            entry.addProperty("version", mod.getVersion().toString());
            mods.add(entry);
        }
        return new ModListPayload(mods.toString());
    }
}
```

> Si `IModInfo.getDisplayName()` n'existe pas dans la version résolue, remplacer par `mod.getModId()`
> comme nom de repli. Si `@Mod(value=..., dist=Dist.CLIENT)` n'accepte pas `dist`, retirer le
> paramètre `dist` (le `side="CLIENT"` du `.mods.toml` suffit). `PacketDistributor.sendToServer(...)`
> dans le handler envoie depuis le thread réseau client — comportement attendu pour une réponse.

- [ ] **Step 4 : Créer `mod/neoforge/src/main/resources/META-INF/neoforge.mods.toml`**

```toml
modLoader = "javafml"
loaderVersion = "[1,)"
license = "All Rights Reserved"

[[mods]]
modId = "modchecker"
version = "1.0.0"
displayName = "ModChecker"
description = "Envoie la liste des mods au serveur (mod-checker générique)"
authors = "Zeffut"

[[dependencies.modchecker]]
modId = "neoforge"
type = "required"
versionRange = "[21.11,)"
ordering = "NONE"
side = "CLIENT"

[[dependencies.modchecker]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.11,1.22)"
ordering = "NONE"
side = "CLIENT"
```

- [ ] **Step 5 : Builder le module NeoForge**

Run: `cd mod && ./gradlew :neoforge:build && cd ..`
Expected: BUILD SUCCESSFUL, jar dans `mod/neoforge/build/libs/modchecker-neoforge-1.0.0.jar`.
Si erreurs de compilation : ajuster les imports/signatures réseau à la version NeoForge résolue
(voir notes Steps 2-3).

- [ ] **Step 6 : Commit**

```bash
git add mod/neoforge
git commit -m "feat(mod): module client NeoForge (payload optional → serveur Purpur)"
```

---

## Task 7 : Finalisation README + checklist de validation manuelle

**Files:**
- Modify: `README.md`

- [ ] **Step 1 : Compléter la section build de `README.md`**

Remplacer la ligne `## Build (rempli au fil de l'implémentation)` par :

```markdown
## Build

### Plugin serveur
```bash
mvn -f plugin/pom.xml clean package
# → plugin/target/ModChecker-1.0.0.jar  (à mettre dans plugins/ du serveur)
```

### Mod client
```bash
cd mod
./gradlew :fabric:build       # → mod/fabric/build/libs/modchecker-fabric-1.0.0.jar
./gradlew :neoforge:build     # → mod/neoforge/build/libs/modchecker-neoforge-1.0.0.jar
```

## Configuration serveur
À la première activation, `plugins/ModChecker/config.yml` est créé (server-name, kick-without-mod,
grace-period-seconds, bypass-permission, messages). Recharge le plugin après modification.

## Commandes
`/mods` (GUI) · `/mods list` · `/mods player <joueur>` · `/mods allow|ban|reset <mod-id>`
Permission : `modchecker.admin`. Exemption : permission `modchecker.bypass` ou OP.
```

- [ ] **Step 2 : Checklist de validation manuelle (à cocher en jeu — hors CI)**

Validation impossible en unitaire (réseau + client réel). Sur un serveur Purpur 1.21.11 de test :

- [ ] Plugin chargé sans erreur, log `ModChecker activé (channel modchecker:modlist).`
- [ ] Client **Fabric** + jar mod → au join, log serveur `Mods de <joueur> : N mod(s) détecté(s).`
      (le serveur a envoyé `hello`, le mod a répondu sa liste).
- [ ] Client **NeoForge** + jar mod → même log au join.
- [ ] **Handshake — serveur sans plugin :** connecter le client modé à un serveur **sans** le plugin
      ModChecker → le client n'envoie **aucune** liste (aucun paquet `modchecker:modlist` émis,
      vérifiable via un sniffer/log mod). C'est le cœur de la nouvelle exigence.
- [ ] **Client vanilla** (sans le mod) sur le serveur avec plugin → aucune erreur client (le `hello`
      sur channel inconnu est ignoré) ; serveur le traite comme « sans mod ».
- [ ] `/mods list` montre les mods en `UNKNOWN` ; `/mods ban <id>` kicke le joueur porteur.
- [ ] GUI `/mods` : clic gauche=ALLOWED, droit=BANNED, shift=UNKNOWN ; persiste dans `mods.json`.
- [ ] `kick-without-mod: true` → un client **sans** le mod est kické après `grace-period-seconds`.
- [ ] Un joueur avec permission `modchecker.bypass` n'est jamais kické.

- [ ] **Step 3 : Commit final**

```bash
git add README.md
git commit -m "docs: README build + configuration + checklist de validation"
```

---

## Self-Review (effectuée à la rédaction)

- **Couverture spec :** PROTOCOL.md handshake 2 channels (T1) ✓ · mod Fabric hello→liste (T5) ✓ ·
  mod NeoForge + optional() hello→liste (T6) ✓ · plugin découplé/config (T3) ✓ · ProtocolLib retiré
  (pom T2 — absent) ✓ · combat-tag retiré (T3 Step 1) ✓ · channels génériques
  `modchecker:hello`/`modchecker:modlist` (T1/T2/T3/T5/T6) ✓ · **handshake : client n'envoie que sur
  réception du hello** (T3 envoi serveur, T5/T6 réception client, T7 test serveur-sans-plugin) ✓ ·
  persistance mods.json (T3) ✓ · tests encode/decode/parse (T2) ✓ · originaux Zeffut intacts (copie
  via `cp`, jamais de suppression source) ✓.
- **Placeholders :** aucun « TBD/TODO ». Les seules incertitudes (versions NeoForge, signatures réseau)
  ont des steps de vérification concrets (curl maven, build qui signale) + valeurs de repli explicites.
- **Cohérence des types :** `ModInfo` (accesseurs `id()/name()/version()`) utilisé identiquement
  dans ModListCodec, ModChecker, ModCheckerGUI. Constantes alignées mod/plugin :
  `modchecker:modlist` (C2S) et `modchecker:hello` (S2C). `ModListCodec.encode/decode/parse`
  signatures stables entre T2 (def) et T3 (usage). Handler hello → `ModListPayload` cohérent T5/T6.
```
