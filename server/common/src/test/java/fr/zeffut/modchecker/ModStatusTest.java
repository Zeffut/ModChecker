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
}
