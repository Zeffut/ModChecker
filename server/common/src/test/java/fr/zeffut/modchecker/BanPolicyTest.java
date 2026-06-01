package fr.zeffut.modchecker;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BanPolicyTest {
    private static final List<ModInfo> MODS = List.of(
            new ModInfo("a", "Alpha", "1"), new ModInfo("b", "Beta", "1"), new ModInfo("c", "Gamma", "1"));

    @Test
    void bannedAmongReturnsOnlyBanned() {
        Map<String, ModStatus> st = Map.of("a", ModStatus.ALLOWED, "b", ModStatus.BANNED);
        List<String> banned = BanPolicy.bannedAmong(MODS, st);
        assertEquals(List.of("Beta (b)"), banned); // c absent de la map = inconnu, pas banni
    }

    @Test
    void hasBannedTrueWhenAnyBanned() {
        assertTrue(BanPolicy.hasBanned(MODS, Map.of("c", ModStatus.BANNED)));
        assertFalse(BanPolicy.hasBanned(MODS, Map.of("a", ModStatus.ALLOWED)));
        assertFalse(BanPolicy.hasBanned(List.of(), Map.of("a", ModStatus.BANNED)));
    }

    // ─── Edge cases ────────────────────────────────────────────────────────

    @Test
    void bannedAmongWithEmptyModListReturnsEmpty() {
        // Aucun mod → rien à bannir, quelle que soit la politique.
        List<String> result = BanPolicy.bannedAmong(List.of(), Map.of("a", ModStatus.BANNED));
        assertTrue(result.isEmpty(), "Aucun résultat attendu pour une liste de mods vide");
    }

    @Test
    void bannedAmongWithAllUnknownReturnsEmpty() {
        // Tous les mods sont UNKNOWN (ni BANNED ni ALLOWED) → liste vide.
        Map<String, ModStatus> allUnknown = Map.of("a", ModStatus.UNKNOWN, "b", ModStatus.UNKNOWN, "c", ModStatus.UNKNOWN);
        List<String> result = BanPolicy.bannedAmong(MODS, allUnknown);
        assertTrue(result.isEmpty(), "Les mods UNKNOWN ne doivent pas figurer parmi les bannis");
    }

    @Test
    void bannedAmongMixedStatusesReturnsOnlyBannedLabel() {
        // a=ALLOWED, b=BANNED, c=UNKNOWN → seul b ressort, avec le libellé "nom (id)".
        Map<String, ModStatus> mixed = Map.of("a", ModStatus.ALLOWED, "b", ModStatus.BANNED, "c", ModStatus.UNKNOWN);
        List<String> result = BanPolicy.bannedAmong(MODS, mixed);
        assertEquals(1, result.size());
        assertEquals("Beta (b)", result.get(0));
    }

    @Test
    void hasBannedWithAllUnknownReturnsFalse() {
        // Aucun mod BANNED → hasBanned doit retourner false.
        Map<String, ModStatus> allUnknown = Map.of("a", ModStatus.UNKNOWN, "b", ModStatus.UNKNOWN, "c", ModStatus.UNKNOWN);
        assertFalse(BanPolicy.hasBanned(MODS, allUnknown));
    }

    @Test
    void bannedAmongModAbsentFromMapNotCounted() {
        // Si un mod n'est pas dans la map de statuts, il est traité comme UNKNOWN (absent == null != BANNED).
        // Ici aucun des 3 mods n'est dans la map → liste vide attendue.
        List<String> result = BanPolicy.bannedAmong(MODS, Map.of());
        assertTrue(result.isEmpty(), "Les mods absents de la map ne doivent pas être traités comme bannis");
    }
}
