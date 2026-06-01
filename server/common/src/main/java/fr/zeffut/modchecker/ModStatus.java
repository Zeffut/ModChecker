package fr.zeffut.modchecker;

/** Statut d'un mod connu du serveur. */
public enum ModStatus {
    ALLOWED, BANNED, UNKNOWN;

    public static ModStatus fromString(String s) {
        if (s == null) return UNKNOWN;
        try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); } catch (Exception e) { return UNKNOWN; }
    }
}
