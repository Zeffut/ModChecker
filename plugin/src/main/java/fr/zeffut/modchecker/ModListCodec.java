package fr.zeffut.modchecker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Encodage/décodage du paquet réseau ModChecker — sans dépendance Bukkit, testable unitairement. */
public final class ModListCodec {

    private ModListCodec() {}

    /** Encode un String au format MC (VarInt longueur + UTF-8) — utilisé pour le paquet hello. */
    public static byte[] encode(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int value = utf8.length;
        while (true) {
            if ((value & ~0x7F) == 0) { out.write(value); break; }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeBytes(utf8);
        return out.toByteArray();
    }

    /** Décode un String encodé au format MC (VarInt longueur + UTF-8). Null si illisible. */
    public static String decode(byte[] data) {
        try {
            int index = 0, length = 0, shift = 0;
            byte b;
            do {
                if (index >= data.length) return null;
                b = data[index++];
                length |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);

            if (index + length > data.length) return null;
            return new String(data, index, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse le tableau JSON de mods. Null si invalide. */
    public static List<ModInfo> parse(String json) {
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<ModInfo> list = new ArrayList<>();
            for (JsonElement el : array) {
                JsonObject obj = el.getAsJsonObject();
                list.add(new ModInfo(
                        obj.get("id").getAsString(),
                        obj.get("name").getAsString(),
                        obj.has("version") ? obj.get("version").getAsString() : "?"
                ));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}
