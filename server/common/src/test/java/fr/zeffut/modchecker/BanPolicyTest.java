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
}
