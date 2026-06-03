package fr.zeffut.modchecker.telemetry;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonWriterTest {

    @Test
    void writesFlatObjectWithTypes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "hello");
        m.put("n", 42);
        m.put("b", true);
        assertEquals("{\"s\":\"hello\",\"n\":42,\"b\":true}", JsonWriter.write(m));
    }

    @Test
    void escapesSpecialCharacters() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", "a\"b\\c\nd");
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\"}", JsonWriter.write(m));
    }

    @Test
    void writesListAsJsonArray() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mods", List.of("xray", "sodium"));
        assertEquals("{\"mods\":[\"xray\",\"sodium\"]}", JsonWriter.write(m));
    }

    @Test
    void nullValueBecomesJsonNull() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", null);
        assertEquals("{\"k\":null}", JsonWriter.write(m));
    }
}
