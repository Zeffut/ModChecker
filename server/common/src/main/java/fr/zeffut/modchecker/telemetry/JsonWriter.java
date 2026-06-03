package fr.zeffut.modchecker.telemetry;

import java.util.Collection;
import java.util.Map;

/** Encodeur JSON minimal pour des maps plates (String/Number/Boolean/Collection/null). */
final class JsonWriter {
    private JsonWriter() {}

    static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            value(sb, e.getValue());
        }
        return sb.append('}').toString();
    }

    /** Encode une valeur unique (String/Number/Boolean/Collection/null) en JSON. */
    static String value(Object v) {
        StringBuilder sb = new StringBuilder();
        value(sb, v);
        return sb.toString();
    }

    private static void value(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                first = false;
                value(sb, item);
            }
            sb.append(']');
        } else {
            sb.append('"').append(escape(v.toString())).append('"');
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
