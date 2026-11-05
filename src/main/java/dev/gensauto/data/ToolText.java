package dev.gensauto.data;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Gens tool texts the server already shows: the tool's tooltip, the action bar
 * ("PICO DE MINERÍA | ★ Nivel 27 (33.01%) | Tokens Farm. (+0/m)") and the prestige menu item.
 * Pure Java so it can be tested with the exact strings from the server.
 */
public final class ToolText {
    private ToolText() {}

    public record Lore(int level, double percent, int prestige, int rebirth) {}
    public record ActionBar(String tool, int level, double percent, String rateLabel, String rate) {}
    public record PrestigeRequirement(String tool, int prestige, int level) {}

    private static final Pattern LEVEL = Pattern.compile("Nivel:\\s*([\\d.,]+)");
    private static final Pattern PERCENT = Pattern.compile("\\(\\s*([\\d.,]+)\\s*%\\s*\\)");
    private static final Pattern PRESTIGE = Pattern.compile("Prestigio:\\s*(\\d+)");
    private static final Pattern REBIRTH = Pattern.compile("Renacimiento:\\s*(\\d+)");
    private static final Pattern BAR_LEVEL = Pattern.compile("Nivel\\s*:?\\s*([\\d.,]+)\\s*\\(\\s*([\\d.,]+)\\s*%\\s*\\)");
    private static final Pattern BAR_RATE = Pattern.compile("(\\p{L}[\\p{L}.\\s]*?)\\s*\\(\\s*([^()]*?/\\s*\\p{L}+)\\s*\\)");
    private static final Pattern EDGE_SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");
    private static final Pattern PRESTIGE_TITLE = Pattern.compile("Prestigiar\\s*\\|\\s*(.+?)\\s*\\(\\s*Prestigio\\s+(\\d+)\\s*\\)");
    private static final Pattern REQUIRED = Pattern.compile("Nivel de Herramienta:\\s*([\\d.,]+)");

    /** "Pico de Minería", "PICO DE MINERIA" and "PICO DE MINERÍA ⛏" all become "PICO DE MINERIA". */
    public static String key(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    /** The tool's tooltip lines, or null when the item is not a Gens tool (no level and prestige lines). */
    public static Lore parseLore(List<String> lines) {
        Integer level = null, prestige = null, rebirth = 0;
        double percent = 0;
        for (String line : lines) {
            Matcher m;
            if (level == null && (m = LEVEL.matcher(line)).find()) {
                level = parseInt(m.group(1));
                Matcher p = PERCENT.matcher(line);
                while (p.find()) percent = parseDouble(p.group(1));
            } else if (prestige == null && (m = PRESTIGE.matcher(line)).find()) prestige = parseInt(m.group(1));
            else if ((m = REBIRTH.matcher(line)).find()) rebirth = parseInt(m.group(1));
        }
        if (level == null || prestige == null) return null;
        return new Lore(level, percent, prestige, rebirth == null ? 0 : rebirth);
    }

    /**
     * Does not depend on the separator glyph ("|", "┃", custom font icons): the tool name is whatever is
     * before "Nivel", and the rate is the first "Label (+value/m)" after it.
     */
    public static ActionBar parseActionBar(String text) {
        String t = text.replace('\n', ' ');
        Matcher m = BAR_LEVEL.matcher(t);
        if (!m.find()) return null;
        String tool = EDGE_SYMBOLS.matcher(t.substring(0, m.start())).replaceAll("").strip();
        Integer level = parseInt(m.group(1));
        if (level == null || key(tool).isEmpty()) return null;
        String label = null, rate = null;
        Matcher r = BAR_RATE.matcher(t.substring(m.end()));
        if (r.find()) { label = r.group(1).strip(); rate = r.group(2).replaceAll("\\s+", ""); }
        return new ActionBar(tool, level, parseDouble(m.group(2)), label, rate);
    }

    /** The bell in "Menú | Prestigio": its name says the tool and current prestige, its lore the level needed. */
    public static PrestigeRequirement parsePrestigeItem(String name, List<String> lore) {
        Matcher title = PRESTIGE_TITLE.matcher(name);
        if (!title.find()) return null;
        for (String line : lore) {
            Matcher m = REQUIRED.matcher(line);
            if (m.find()) {
                Integer level = parseInt(m.group(1)), prestige = parseInt(title.group(2));
                if (level != null && prestige != null) return new PrestigeRequirement(title.group(1).strip(), prestige, level);
            }
        }
        return null;
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("[.,]", "")); } catch (NumberFormatException e) { return null; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.replace(',', '.')); } catch (NumberFormatException e) { return 0; }
    }

    /** 3h 12m, 45m, 30s. */
    public static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long m = seconds / 60, h = m / 60;
        if (h >= 24) return (h / 24) + "d " + (h % 24) + "h";
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
    }
}
