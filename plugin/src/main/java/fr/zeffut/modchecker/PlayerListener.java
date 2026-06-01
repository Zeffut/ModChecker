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
