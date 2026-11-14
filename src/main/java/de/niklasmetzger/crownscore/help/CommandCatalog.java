package de.niklasmetzger.crownscore.help;

import de.niklasmetzger.crownscore.CrownsCore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Liest die Befehlsuebersicht aus crownscore/commands.tsv. Dieselbe Datei erzeugt auch die PDF,
 * damit Spiel und Handbuch nie auseinanderlaufen.
 */
public final class CommandCatalog {
    public enum Role {
        ALLE("Alle", "§a"),
        BUERGER("Bürger", "§b"),
        BEAMTER("Beamte", "§e"),
        CHEF("Staatschef", "§6"),
        ADMIN("Admin", "§c");

        public final String label;
        public final String color;

        Role(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    public record Module(String key, String title, String intro) {}

    public record Entry(Module module, String syntax, Role role, String description) {
        /** Feste Woerter des Befehls ohne Schraegstrich, z.B. [nation, beitreten]. */
        public List<String> literals() {
            List<String> out = new ArrayList<>();
            for (String t : syntax.replaceFirst("^/", "").split(" +")) {
                if (t.isEmpty() || t.startsWith("<") || t.startsWith("[")) break;
                out.add(t.toLowerCase(Locale.ROOT));
            }
            return out;
        }

        /** Hat der Befehl nach den festen Woertern noch Argumente? */
        public boolean hasArguments() {
            return syntax.contains("<") || syntax.contains("[");
        }

        /** Rechteknoten, z.B. crownscore.nation.beitreten. */
        public String node() {
            return "crownscore." + String.join(".", literals());
        }

        /** Befehl ohne Platzhalter, zum Vorschlagen per Klick. */
        public String suggestion() {
            int cut = syntax.length();
            for (char c : new char[]{'<', '['}) {
                int i = syntax.indexOf(c);
                if (i >= 0) cut = Math.min(cut, i);
            }
            return syntax.substring(0, cut).trim() + " ";
        }
    }

    private static List<Module> modules;
    private static List<Entry> entries;

    private CommandCatalog() {}

    public static synchronized List<Entry> entries() {
        if (entries == null) load();
        return entries;
    }

    public static synchronized List<Module> modules() {
        if (modules == null) load();
        return modules;
    }

    public static Module module(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (Module m : modules()) {
            if (m.key().equals(k) || m.title().toLowerCase(Locale.ROOT).equals(k)) return m;
        }
        return null;
    }

    private static void load() {
        List<Module> mods = new ArrayList<>();
        List<Entry> list = new ArrayList<>();
        try (InputStream in = CommandCatalog.class.getResourceAsStream("/crownscore/commands.tsv")) {
            if (in == null) throw new IllegalStateException("commands.tsv fehlt im JAR");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Module current = null;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                if (line.startsWith("@")) {
                    current = new Module(f[0].substring(1), f[1], f.length > 2 ? f[2] : "");
                    mods.add(current);
                } else if (current != null && f.length >= 3) {
                    list.add(new Entry(current, f[0], Role.valueOf(f[1]), f[2]));
                }
            }
        } catch (Exception e) {
            CrownsCore.LOGGER.error("Befehlsuebersicht konnte nicht geladen werden", e);
        }
        modules = Collections.unmodifiableList(mods);
        entries = Collections.unmodifiableList(list);
    }
}
