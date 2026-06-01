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
