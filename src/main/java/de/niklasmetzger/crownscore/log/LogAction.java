package de.niklasmetzger.crownscore.log;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Was protokolliert wird. Die Zahl ist der Wert in der Datenbank. */
public enum LogAction {
    BREAK(0, Table.BLOCK, "-block", "§cbaut ab"),
    PLACE(1, Table.BLOCK, "+block", "§asetzt"),
    CLICK(2, Table.BLOCK, "click", "§7benutzt"),
    REMOVE(10, Table.ITEM, "-container", "§cnimmt"),
    ADD(11, Table.ITEM, "+container", "§alegt rein"),
    DROP(12, Table.ITEM, "-item", "§cwirft weg"),
    PICKUP(13, Table.ITEM, "+item", "§ahebt auf"),
    KILL(20, Table.ENTITY, "kill", "§ctoetet"),
    CHAT(30, Table.TEXT, "chat", "§fschreibt"),
    COMMAND(31, Table.TEXT, "command", "§fBefehl"),
    LOGIN(32, Table.TEXT, "+session", "§akommt online"),
    LOGOUT(33, Table.TEXT, "-session", "§cgeht offline");

    public enum Table {
        BLOCK("cc_block"), ITEM("cc_item"), ENTITY("cc_entity"), TEXT("cc_text");

        public final String sql;

        Table(String sql) {
            this.sql = sql;
        }
    }

    public final int id;
    public final Table table;
    public final String key;
    public final String verb;

    LogAction(int id, Table table, String key, String verb) {
        this.id = id;
        this.table = table;
        this.key = key;
        this.verb = verb;
    }

    public static LogAction byId(int id) {
        for (LogAction a : values()) if (a.id == id) return a;
        return null;
    }

    /** a:block, a:+block, a:container, a:item, a:session, a:kill, a:chat, a:command, a:click. */
    public static Set<LogAction> parse(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        Set<LogAction> out = EnumSet.noneOf(LogAction.class);
        for (LogAction a : values()) {
            String base = a.key.replaceFirst("^[+-]", "");
            if (a.key.equals(k) || base.equals(k)) out.add(a);
        }
        return out;
    }

    /** Laesst sich zuruecksetzen? */
    public boolean rollbackable() {
        return this == BREAK || this == PLACE || this == REMOVE || this == ADD || this == KILL;
    }
}
