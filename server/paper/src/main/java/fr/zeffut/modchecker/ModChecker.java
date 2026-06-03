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

    private final Map<String, ModStatus> modStatus = new ConcurrentHashMap<>();
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
    private Telemetry telemetry = null;
    public void setTelemetry(Telemetry telemetry) { this.telemetry = telemetry; }
    private void tel(java.util.function.Consumer<Telemetry> action) { if (telemetry != null) action.accept(telemetry); }

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
        int newModsCount = 0;
        for (ModInfo mod : mods) {
            if (!modStatus.containsKey(mod.id())) {
                modStatus.put(mod.id(), ModStatus.UNKNOWN);
                newModsFound = true;
                newModsCount++;
                tel(t -> t.modDiscovered(mod.id(), mod.name(), mod.version()));
            }
        }
        // onPluginMessageReceived tourne sur le thread réseau (netty) → écriture disque hors-thread
        if (newModsFound) Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);

        List<String> bannedMods = BanPolicy.bannedAmong(mods, modStatus);

        final boolean hasBanned = !bannedMods.isEmpty();
        final int discoveredCount = newModsCount;
        tel(t -> t.modlistReceived(player, mods.size(), json.length(), discoveredCount, hasBanned));

        if (!bannedMods.isEmpty() && !isExempt(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kick(header()
                        .append(Component.text(bannedMsg, NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text(String.join(", ", bannedMods), NamedTextColor.GRAY)));
                tel(t -> t.playerKicked(player, "banned_mod", bannedMods));
            });
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
        tel(t -> t.playerJoin(player));

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
                    tel(t -> t.playerKicked(player, "missing_mod", java.util.List.of()));
                    plugin.getLogger().info(player.getName() + " kické — mod checker absent.");
                } else {
                    tel(t -> t.playerNoMod(player));
                    plugin.getLogger().warning(player.getName() + " n'a pas le mod checker installé.");
                }
            }
        }, gracePeriodTicks);
    }

    public Map<String, ModStatus> getModStatus() { return modStatus; }

    public List<ModInfo> getPlayerMods(UUID uuid) { return playerMods.get(uuid); }

    /** Change le statut d'un mod et kick les joueurs concernés si banni. */
    public void setStatus(String modId, ModStatus status) {
        modStatus.put(modId, status);
        save();
        tel(t -> t.modStatusChanged(modId, status.name(), "console"));
        if (status == ModStatus.BANNED) {
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
        if (args.length > 0) tel(t -> t.commandUsed(args[0].toLowerCase(), sender.getName()));
        return switch (args[0].toLowerCase()) {
            case "list" -> cmdList(sender);
            case "player" -> cmdPlayer(sender, args);
            case "allow" -> cmdSetStatus(sender, args, ModStatus.ALLOWED);
            case "ban" -> cmdSetStatus(sender, args, ModStatus.BANNED);
            case "reset" -> cmdSetStatus(sender, args, ModStatus.UNKNOWN);
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
            ModStatus status = modStatus.get(modId);
            NamedTextColor color = statusColor(status);
            String icon = switch (status) { case ALLOWED -> "✔"; case BANNED -> "✘"; default -> "?"; };
            sender.sendMessage(Component.text(" " + icon + " ", color)
                    .append(Component.text(modId, NamedTextColor.WHITE))
                    .append(Component.text(" [" + status.name() + "]", color)));
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
            ModStatus status = modStatus.getOrDefault(mod.id(), ModStatus.UNKNOWN);
            NamedTextColor color = statusColor(status);
            sender.sendMessage(Component.text("  ", color)
                    .append(Component.text(mod.name(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + mod.id() + " v" + mod.version() + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" [" + status.name() + "]", color)));
        }
        return true;
    }

    private boolean cmdSetStatus(CommandSender sender, String[] args, ModStatus status) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage : /mods " + args[0] + " <mod-id>", NamedTextColor.RED));
            return true;
        }
        String modId = args[1].toLowerCase();
        setStatus(modId, status);
        sender.sendMessage(Component.text(modId, NamedTextColor.WHITE)
                .append(Component.text(" → " + status.name(), statusColor(status))));
        return true;
    }

    private static NamedTextColor statusColor(ModStatus status) {
        return switch (status) {
            case ALLOWED -> NamedTextColor.GREEN;
            case BANNED -> NamedTextColor.RED;
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
            Type type = new TypeToken<Map<String, ModStatus>>() {}.getType();
            Map<String, ModStatus> loaded = gson.fromJson(reader, type);
            if (loaded != null) modStatus.putAll(loaded);
        } catch (IOException e) {
            plugin.getLogger().warning("Erreur lecture mods.json : " + e.getMessage());
        }
    }

    /** synchronized : sérialise les écritures (appels possibles depuis main thread ET async). */
    private synchronized void save() {
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
