package fr.zeffut.modchecker;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModListCodecTest {

    @Test
    void encodeWritesVarIntLengthThenUtf8() {
        // "ABC" → longueur 3 (VarInt 0x03) puis 0x41 0x42 0x43
        assertArrayEquals(new byte[]{0x03, 0x41, 0x42, 0x43}, ModListCodec.encode("ABC"));
    }

    @Test
    void encodeDecodeRoundTrip() {
        String json = "[{\"id\":\"fabric-api\",\"name\":\"Fabric API\",\"version\":\"0.1\"}]";
        assertEquals(json, ModListCodec.decode(ModListCodec.encode(json)));
    }

    @Test
    void decodeReturnsNullOnTruncatedData() {
        // VarInt annonce une longueur que les données ne contiennent pas
        assertNull(ModListCodec.decode(new byte[]{(byte) 0x05, (byte) 0x41}));
    }

    @Test
    void parsesModListWithAndWithoutVersion() {
        List<ModInfo> mods = ModListCodec.parse(
                "[{\"id\":\"a\",\"name\":\"Alpha\",\"version\":\"1.0\"},{\"id\":\"b\",\"name\":\"Beta\"}]");
        assertNotNull(mods);
        assertEquals(2, mods.size());
        assertEquals("a", mods.get(0).id());
        assertEquals("Alpha", mods.get(0).name());
        assertEquals("1.0", mods.get(0).version());
        assertEquals("?", mods.get(1).version());
    }

    @Test
    void parseReturnsNullOnInvalidJson() {
        assertNull(ModListCodec.parse("pas du json"));
    }
}
