package fun.tcsmp.susmenu;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tiny MiniMessage-ish / &-code to legacy section-sign converter (works on Bukkit, Spigot, Paper, Folia). */
final class Text {
    private static final Map<String, String> TAGS = new HashMap<>();

    static {
        String[][] c = {
            {"black", "0"}, {"dark_blue", "1"}, {"dark_green", "2"}, {"dark_aqua", "3"}, {"dark_red", "4"},
            {"dark_purple", "5"}, {"gold", "6"}, {"gray", "7"}, {"grey", "7"}, {"dark_gray", "8"}, {"dark_grey", "8"},
            {"blue", "9"}, {"green", "a"}, {"aqua", "b"}, {"red", "c"}, {"light_purple", "d"}, {"yellow", "e"},
            {"white", "f"}, {"bold", "l"}, {"italic", "o"}, {"underlined", "n"}, {"underline", "n"},
            {"strikethrough", "m"}, {"obfuscated", "k"}, {"reset", "r"}
        };
        for (String[] e : c) TAGS.put(e[0], "§" + e[1]);
        TAGS.put("orange", hex("FF8C00"));
    }

    private Text() {}

    static String color(String in) {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < in.length()) {
            char ch = in.charAt(i);
            if (ch == '<') {
                int end = in.indexOf('>', i);
                if (end > i) {
                    String tag = in.substring(i + 1, end).toLowerCase(Locale.ROOT);
                    String rep;
                    if (tag.startsWith("/")) rep = "§r";
                    else if (tag.matches("#[0-9a-f]{6}")) rep = hex(tag.substring(1));
                    else rep = TAGS.get(tag);
                    if (rep != null) {
                        out.append(rep);
                        i = end + 1;
                        continue;
                    }
                }
            } else if (ch == '&' && i + 1 < in.length()) {
                char n = in.charAt(i + 1);
                if (n == '#' && i + 8 <= in.length() && in.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
                    out.append(hex(in.substring(i + 2, i + 8)));
                    i += 8;
                    continue;
                }
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(n) >= 0) {
                    out.append('§').append(Character.toLowerCase(n));
                    i += 2;
                    continue;
                }
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String hex(String rrggbb) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : rrggbb.toLowerCase(Locale.ROOT).toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }
}
