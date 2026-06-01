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
