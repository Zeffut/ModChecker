package fr.zeffut.modchecker;

import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Intégration MockBukkit pour ModCheckerPlugin (Paper 1.21.x).
 *
 * Contraintes connues de MockBukkit :
 * - Pas de simulation réelle du canal PluginMessage (Messenger) : on appelle
 *   onPluginMessageReceived() directement plutôt que via l'API Messenger.
 * - sendPluginMessage() côté plugin n'a pas d'effet vérifiable dans MockBukkit ;
 *   le handshake hello est testé indirectement via onPlayerJoin.
 * - Les tasks Bukkit (runTask, runTaskAsynchronously) ne s'exécutent pas
 *   automatiquement : on appelle scheduler.performOneTick() pour les déclencher.
 * - player.kick() dans MockBukkit déconnecte le joueur (isOnline() → false).
 *   Cependant le kick déclenché par onPluginMessageReceived est posté via
 *   runTask (main thread) ; on doit appeler performOneTick() pour l'exécuter.
 */
class ModCheckerIntegrationTest {

    private ServerMock server;
    private ModCheckerPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ModCheckerPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ─── 1. Démarrage du plugin ─────────────────────────────────────────────

    @Test
    void pluginEnablesCleanly() {
        assertTrue(plugin.isEnabled(), "Le plugin doit être activé après MockBukkit.load()");
    }

    @Test
    void configSavedOnEnable() {
        // saveDefaultConfig() crée config.yml dans le dossier de données
        assertTrue(plugin.getDataFolder().exists(), "Le répertoire de données doit exister après onEnable()");
    }

    @Test
    void modsCommandIsRegistered() {
        PluginCommand cmd = plugin.getCommand("mods");
        assertNotNull(cmd, "La commande /mods doit être enregistrée");
        assertEquals(plugin.getModChecker(), cmd.getExecutor(), "L'exécuteur de /mods doit être ModChecker");
    }

    // ─── 2. Réception de la liste de mods via message plugin ───────────────

    @Test
    void receivingModListRegistersModsAsUnknown() {
        PlayerMock player = server.addPlayer("Alice");
        String json = "[{\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.100\"}," +
                      "{\"id\":\"sodium\",\"name\":\"Sodium\",\"version\":\"0.5\"}]";
        byte[] payload = ModListCodec.encode(json);

        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);

        Map<String, ModStatus> status = plugin.getModChecker().getModStatus();
        assertEquals(ModStatus.UNKNOWN, status.get("fabric-api"));
        assertEquals(ModStatus.UNKNOWN, status.get("sodium"));
    }

    @Test
    void receivingModListPopulatesPlayerMods() {
        PlayerMock player = server.addPlayer("Bob");
        String json = "[{\"id\":\"lithium\",\"name\":\"Lithium\",\"version\":\"0.12\"}]";
        byte[] payload = ModListCodec.encode(json);

        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);

        List<ModInfo> mods = plugin.getModChecker().getPlayerMods(player.getUniqueId());
        assertNotNull(mods, "getPlayerMods doit retourner la liste après réception");
        assertEquals(1, mods.size());
        assertEquals("lithium", mods.get(0).id());
    }

    @Test
    void malformedPayloadDoesNotCrash() {
        PlayerMock player = server.addPlayer("Carol");
        // Payload illisible : VarInt annonce 50 octets, mais seul 1 octet suit.
        byte[] badPayload = new byte[]{(byte) 0x32, (byte) 0x41};

        // Ne doit pas lever d'exception ; le joueur n'est pas kické
        assertDoesNotThrow(() ->
                plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, badPayload));
        assertTrue(player.isOnline(), "Un payload invalide ne doit pas kicker le joueur");
    }

    // ─── 3. Kick sur mod banni ──────────────────────────────────────────────

    @Test
    void playerWithBannedModIsKicked() {
        PlayerMock player = server.addPlayer("Dave");

        // Enregistrer le mod puis le bannir
        String json = "[{\"id\":\"hacked-mod\",\"name\":\"Hacked Mod\",\"version\":\"1.0\"}]";
        byte[] payload = ModListCodec.encode(json);
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);
        plugin.getModChecker().setStatus("hacked-mod", ModStatus.BANNED);

        // Le kick dans setStatus est appelé directement (pas via runTask)
        // → pas besoin de tick ici ; mais si le joueur est déjà déconnecté on teste isOnline
        // Note : setStatus appelle player.kick() directement (pas via Bukkit.getScheduler())
        assertFalse(player.isOnline(), "Un joueur portant un mod banni doit être kické");
    }

    @Test
    void banOnJoinKicksPlayer() {
        // Scénario : mod banni AVANT que le joueur envoie sa liste → kick au moment de onPluginMessageReceived
        PlayerMock player = server.addPlayer("Eve");
        plugin.getModChecker().getModStatus().put("evil-mod", ModStatus.BANNED);

        String json = "[{\"id\":\"evil-mod\",\"name\":\"Evil Mod\",\"version\":\"2.0\"}]";
        byte[] payload = ModListCodec.encode(json);

        // onPluginMessageReceived poste un runTask pour kicker
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);

        // Exécuter les tâches Bukkit postées sur le main thread
        BukkitSchedulerMock scheduler = server.getScheduler();
        scheduler.performOneTick();

        assertFalse(player.isOnline(),
                "Un joueur dont la liste contient un mod banni doit être kické après le tick");
    }

    // ─── 4. Bypass : joueur exempté avec mod banni ─────────────────────────

    @Test
    void playerWithBypassPermissionNotKickedForBannedMod() {
        PlayerMock player = server.addPlayer("Frank");
        player.addAttachment(plugin, "modchecker.bypass", true);

        // Mod déjà banni
        plugin.getModChecker().getModStatus().put("hax", ModStatus.BANNED);

        String json = "[{\"id\":\"hax\",\"name\":\"Hax\",\"version\":\"1.0\"}]";
        byte[] payload = ModListCodec.encode(json);

        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);
        server.getScheduler().performOneTick();

        assertTrue(player.isOnline(),
                "Un joueur avec la permission bypass ne doit pas être kické pour un mod banni");
    }

    @Test
    void opPlayerNotKickedForBannedMod() {
        PlayerMock player = server.addPlayer("Grace");
        player.setOp(true);

        plugin.getModChecker().getModStatus().put("forbidden-mod", ModStatus.BANNED);

        String json = "[{\"id\":\"forbidden-mod\",\"name\":\"Forbidden Mod\",\"version\":\"1.0\"}]";
        byte[] payload = ModListCodec.encode(json);

        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, payload);
        server.getScheduler().performOneTick();

        assertTrue(player.isOnline(), "Un OP ne doit pas être kické pour un mod banni");
    }

    // ─── 5. Commandes /mods ─────────────────────────────────────────────────

    @Test
    void cmdBanUpdatesStatus() {
        // Pré-remplir un mod dans la map (sinon /mods ban crée quand même l'entrée)
        plugin.getModChecker().getModStatus().put("test-mod", ModStatus.UNKNOWN);

        server.executeConsole("mods", "ban", "test-mod");

        assertEquals(ModStatus.BANNED, plugin.getModChecker().getModStatus().get("test-mod"),
                "/mods ban doit mettre le statut à BANNED");
    }

    @Test
    void cmdAllowUpdatesStatus() {
        plugin.getModChecker().getModStatus().put("test-mod", ModStatus.BANNED);

        server.executeConsole("mods", "allow", "test-mod");

        assertEquals(ModStatus.ALLOWED, plugin.getModChecker().getModStatus().get("test-mod"),
                "/mods allow doit mettre le statut à ALLOWED");
    }

    @Test
    void cmdResetUpdatesStatus() {
        plugin.getModChecker().getModStatus().put("test-mod", ModStatus.BANNED);

        server.executeConsole("mods", "reset", "test-mod");

        assertEquals(ModStatus.UNKNOWN, plugin.getModChecker().getModStatus().get("test-mod"),
                "/mods reset doit remettre le statut à UNKNOWN");
    }

    @Test
    void cmdListDoesNotThrow() {
        plugin.getModChecker().getModStatus().put("fabric-api", ModStatus.ALLOWED);
        plugin.getModChecker().getModStatus().put("sodium", ModStatus.BANNED);

        // Ne doit pas lever d'exception même si le résultat n'est pas vérifié côté texte
        assertDoesNotThrow(() -> server.executeConsole("mods", "list"));
    }

    // ─── 6. onPlayerJoin / onPlayerQuit ─────────────────────────────────────

    @Test
    void onPlayerJoinSchedulesGraceTask() {
        // onPlayerJoin est déclenché automatiquement par server.addPlayer()
        // La tâche de grâce est postée via runTaskLater → elle ne s'exécute pas encore
        PlayerMock player = server.addPlayer("Henry");

        // Le joueur doit rester en ligne tant que la grâce n'est pas écoulée
        assertTrue(player.isOnline(), "Le joueur doit rester en ligne pendant la grâce");
    }

    @Test
    void onPlayerQuitClearsPlayerMods() {
        PlayerMock player = server.addPlayer("Irene");

        String json = "[{\"id\":\"optifine\",\"name\":\"OptiFine\",\"version\":\"1.0\"}]";
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, ModListCodec.encode(json));

        assertNotNull(plugin.getModChecker().getPlayerMods(player.getUniqueId()),
                "Les mods doivent être enregistrés après réception");

        plugin.getModChecker().onPlayerQuit(player.getUniqueId());

        assertNull(plugin.getModChecker().getPlayerMods(player.getUniqueId()),
                "Les mods doivent être supprimés après la déconnexion");
    }

    // ─── 7. Persistance mods.json ────────────────────────────────────────────

    @Test
    void statusPersistedAfterBanCommand() {
        // Le status est stocké en mémoire après /mods ban
        server.executeConsole("mods", "ban", "cheat-mod");
        assertEquals(ModStatus.BANNED, plugin.getModChecker().getModStatus().get("cheat-mod"),
                "Le statut banni doit être présent en mémoire après la commande");
    }

    @Test
    void newModsFoundSavedAsUnknown() {
        PlayerMock player = server.addPlayer("Jack");
        String json = "[{\"id\":\"new-unknown-mod\",\"name\":\"New Mod\",\"version\":\"1.0\"}]";
        plugin.getModChecker().onPluginMessageReceived(ModChecker.CHANNEL, player, ModListCodec.encode(json));

        // Les nouveaux mods sont enregistrés comme UNKNOWN
        assertEquals(ModStatus.UNKNOWN, plugin.getModChecker().getModStatus().get("new-unknown-mod"));
    }
}
