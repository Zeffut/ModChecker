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

        boolean telemetryEnabled = getConfig().getBoolean("telemetry", true);
        String telemetryHost = getConfig().getString("telemetry-host",
                fr.zeffut.modchecker.telemetry.PostHogClient.DEFAULT_HOST);
        String telemetryServerName = getConfig().getString("server-name", "Serveur");
        String installId = loadOrCreateInstallId();
        fr.zeffut.modchecker.telemetry.PostHogClient phClient =
                new fr.zeffut.modchecker.telemetry.PostHogClient(
                        telemetryEnabled, telemetryHost, "paper",
                        getServer().getBukkitVersion(), getPluginMeta().getVersion());
        Telemetry telemetry = new Telemetry(phClient, telemetryServerName, installId);
        modChecker.setTelemetry(telemetry);
        telemetry.pluginEnabled(getServer().getBukkitVersion(),
                getConfig().getBoolean("kick-without-mod", false),
                getConfig().getLong("grace-period-seconds", 5),
                modChecker.getModStatus().size(),
                getServer().getOnlineMode());

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

    @Override
    public void onDisable() {
        // Annule les tâches de grâce en attente (évite un kick après désactivation).
        getServer().getScheduler().cancelTasks(this);
    }

    public ModChecker getModChecker() { return modChecker; }

    private String loadOrCreateInstallId() {
        java.io.File f = new java.io.File(getDataFolder(), ".install-id");
        try {
            if (f.exists()) return java.nio.file.Files.readString(f.toPath()).trim();
            String id = java.util.UUID.randomUUID().toString();
            getDataFolder().mkdirs();
            java.nio.file.Files.writeString(f.toPath(), id);
            return id;
        } catch (Exception e) {
            return "unknown-" + java.util.UUID.randomUUID();
        }
    }
}
