package fr.zeffut.modchecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModCheckerGUI implements Listener {

    private final ModChecker modChecker;
    private static final int PAGE_SIZE = 45; // 5 lignes de mods, 1 ligne de navigation

    public ModCheckerGUI(ModChecker modChecker) {
        this.modChecker = modChecker;
    }

    // ─── GUI liste des mods ──────────────────────────────────────────────────

    public void openModList(Player viewer, int page) {
        List<Map.Entry<String, ModStatus>> entries = new ArrayList<>(modChecker.getModStatus().entrySet());
        entries.removeIf(e -> e.getValue() == ModStatus.ALLOWED);
        entries.sort(Comparator.comparing(Map.Entry::getKey));

        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        Inventory inv = Bukkit.createInventory(new ModListHolder(page), 54,
                Component.text("Mods (" + (page + 1) + "/" + totalPages + ")", NamedTextColor.LIGHT_PURPLE));

        // Remplir les mods
        for (int i = start; i < end; i++) {
            Map.Entry<String, ModStatus> entry = entries.get(i);
            String modId = entry.getKey();
            ModStatus status = entry.getValue();

            Material mat = switch (status) {
                case ALLOWED -> Material.LIME_STAINED_GLASS_PANE;
                case BANNED -> Material.RED_STAINED_GLASS_PANE;
                default -> Material.GRAY_STAINED_GLASS_PANE;
            };
            NamedTextColor color = switch (status) {
                case ALLOWED -> NamedTextColor.GREEN;
                case BANNED -> NamedTextColor.RED;
                default -> NamedTextColor.GRAY;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(modId, color).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text("Statut : " + status.name(), color).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Clic gauche → Autoriser", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("Clic droit → Bannir", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Shift+clic → Inconnu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(i - start, item);
        }

        // Ligne de navigation (slot 45-53)
        fillNavBar(inv, page, totalPages);

        viewer.openInventory(inv);
    }

    // ─── GUI mods d'un joueur ────────────────────────────────────────────────

    public void openPlayerMods(Player viewer, Player target, int page) {
        List<ModInfo> mods = modChecker.getPlayerMods(target.getUniqueId());
        if (mods == null || mods.isEmpty()) {
            viewer.sendMessage(Component.text(target.getName() + " n'a pas de mods.", NamedTextColor.GRAY));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) mods.size() / PAGE_SIZE));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, mods.size());

        Inventory inv = Bukkit.createInventory(new PlayerModsHolder(target.getUniqueId(), target.getName(), page), 54,
                Component.text("Mods de " + target.getName() + " (" + (page + 1) + "/" + totalPages + ")", NamedTextColor.LIGHT_PURPLE));

        for (int i = start; i < end; i++) {
            ModInfo mod = mods.get(i);
            ModStatus status = modChecker.getModStatus().getOrDefault(mod.id(), ModStatus.UNKNOWN);

            Material mat = switch (status) {
                case ALLOWED -> Material.LIME_STAINED_GLASS_PANE;
                case BANNED -> Material.RED_STAINED_GLASS_PANE;
                default -> Material.GRAY_STAINED_GLASS_PANE;
            };
            NamedTextColor color = switch (status) {
                case ALLOWED -> NamedTextColor.GREEN;
                case BANNED -> NamedTextColor.RED;
                default -> NamedTextColor.GRAY;
            };

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(mod.name(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text(mod.id() + " v" + mod.version(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                        Component.text("Statut : " + status.name(), color).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Clic gauche → Autoriser", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("Clic droit → Bannir", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text("Shift+clic → Inconnu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                ));
                item.setItemMeta(meta);
            }
            inv.setItem(i - start, item);
        }

        fillNavBar(inv, page, totalPages);

        viewer.openInventory(inv);
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private void fillNavBar(Inventory inv, int page, int totalPages) {
        // Filler
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) { fm.displayName(Component.text(" ")); filler.setItemMeta(fm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Page précédente (slot 45)
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            if (pm != null) {
                pm.displayName(Component.text("← Page précédente", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                prev.setItemMeta(pm);
            }
            inv.setItem(45, prev);
        }

        // Page suivante (slot 53)
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            if (nm != null) {
                nm.displayName(Component.text("Page suivante →", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                next.setItemMeta(nm);
            }
            inv.setItem(53, next);
        }

        // Info page (slot 49)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.displayName(Component.text("Page " + (page + 1) + "/" + totalPages, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            info.setItemMeta(im);
        }
        inv.setItem(49, info);
    }

    // ─── Gestion des clics ───────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof ModListHolder mlh) {
            event.setCancelled(true);
            handleModListClick(player, mlh, event);
        } else if (holder instanceof PlayerModsHolder pmh) {
            event.setCancelled(true);
            handlePlayerModsClick(player, pmh, event);
        }
    }

    private void handleModListClick(Player player, ModListHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Navigation
        if (slot == 45) { openModList(player, holder.page - 1); return; }
        if (slot == 53) { openModList(player, holder.page + 1); return; }
        if (slot >= 45) return;

        // Clic sur un mod
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.displayName() == null) return;

        // Extraire le modId du displayName
        String modId = getModIdFromEntries(holder.page, slot);
        if (modId == null) return;

        ModStatus newStatus;
        if (event.isShiftClick()) {
            newStatus = ModStatus.UNKNOWN;
        } else if (event.isRightClick()) {
            newStatus = ModStatus.BANNED;
        } else {
            newStatus = ModStatus.ALLOWED;
        }

        modChecker.setStatus(modId, newStatus);

        NamedTextColor color = switch (newStatus) {
            case ALLOWED -> NamedTextColor.GREEN;
            case BANNED -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
        player.sendMessage(Component.text(modId, NamedTextColor.WHITE)
                .append(Component.text(" → " + newStatus.name(), color)));

        // Rafraîchir le GUI
        openModList(player, holder.page);
    }

    private void handlePlayerModsClick(Player player, PlayerModsHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Navigation
        if (slot == 45) { reopenPlayerMods(player, holder, holder.page - 1); return; }
        if (slot == 53) { reopenPlayerMods(player, holder, holder.page + 1); return; }
        if (slot >= 45) return;

        // Clic sur un mod
        List<ModInfo> mods = modChecker.getPlayerMods(holder.targetUuid);
        if (mods == null) return;

        int index = holder.page * PAGE_SIZE + slot;
        if (index >= mods.size()) return;

        String modId = mods.get(index).id();
        ModStatus newStatus;
        if (event.isShiftClick()) {
            newStatus = ModStatus.UNKNOWN;
        } else if (event.isRightClick()) {
            newStatus = ModStatus.BANNED;
        } else {
            newStatus = ModStatus.ALLOWED;
        }

        modChecker.setStatus(modId, newStatus);

        NamedTextColor color = switch (newStatus) {
            case ALLOWED -> NamedTextColor.GREEN;
            case BANNED -> NamedTextColor.RED;
            default -> NamedTextColor.GRAY;
        };
        player.sendMessage(Component.text(modId, NamedTextColor.WHITE)
                .append(Component.text(" → " + newStatus.name(), color)));

        reopenPlayerMods(player, holder, holder.page);
    }

    private String getModIdFromEntries(int page, int slot) {
        List<String> sorted = new ArrayList<>();
        for (Map.Entry<String, ModStatus> e : modChecker.getModStatus().entrySet()) {
            if (e.getValue() != ModStatus.ALLOWED) sorted.add(e.getKey());
        }
        Collections.sort(sorted);
        int index = page * PAGE_SIZE + slot;
        if (index >= sorted.size()) return null;
        return sorted.get(index);
    }

    private void reopenPlayerMods(Player viewer, PlayerModsHolder holder, int page) {
        Player target = Bukkit.getPlayer(holder.targetUuid);
        if (target != null) {
            openPlayerMods(viewer, target, page);
        }
    }

    // ─── Holders ─────────────────────────────────────────────────────────────

    static final class ModListHolder implements InventoryHolder {
        final int page;
        ModListHolder(int page) { this.page = page; }
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }

    static final class PlayerModsHolder implements InventoryHolder {
        final UUID targetUuid;
        final String targetName;
        final int page;
        PlayerModsHolder(UUID uuid, String name, int page) {
            this.targetUuid = uuid;
            this.targetName = name;
            this.page = page;
        }
        @Override public @NotNull Inventory getInventory() { throw new UnsupportedOperationException(); }
    }
}
