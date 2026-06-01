package fr.zeffut.modchecker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModStatusTest {
    @Test
    void fromStringParsesKnownAndFallsBackToUnknown() {
        assertEquals(ModStatus.BANNED, ModStatus.fromString("BANNED"));
        assertEquals(ModStatus.ALLOWED, ModStatus.fromString("ALLOWED"));
        assertEquals(ModStatus.UNKNOWN, ModStatus.fromString("garbage"));
        assertEquals(ModStatus.UNKNOWN, ModStatus.fromString(null));
    }

    @Test
    void fromStringIsCaseInsensitive() {
        assertEquals(ModStatus.BANNED,  ModStatus.fromString("banned"),  "lowercase banned");
        assertEquals(ModStatus.BANNED,  ModStatus.fromString("Banned"),  "mixed-case Banned");
        assertEquals(ModStatus.ALLOWED, ModStatus.fromString("allowed"), "lowercase allowed");
        assertEquals(ModStatus.ALLOWED, ModStatus.fromString("Allowed"), "mixed-case Allowed");
        assertEquals(ModStatus.UNKNOWN, ModStatus.fromString("unknown"), "lowercase unknown");
    }

    @Test
    void fromStringTrimsWhitespace() {
        assertEquals(ModStatus.BANNED,  ModStatus.fromString(" BANNED "),  "trailing spaces BANNED");
        assertEquals(ModStatus.ALLOWED, ModStatus.fromString(" Allowed "), "whitespace + mixed case");
    }
}
