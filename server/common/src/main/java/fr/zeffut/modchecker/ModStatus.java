package fr.zeffut.modchecker;

/** Statut d'un mod connu du serveur. */
public enum ModStatus {
    ALLOWED, BANNED, UNKNOWN;

    public static ModStatus fromString(String s) {
        try { return valueOf(s); } catch (Exception e) { return UNKNOWN; }
    }
}
