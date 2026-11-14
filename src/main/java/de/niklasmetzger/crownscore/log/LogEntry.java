package de.niklasmetzger.crownscore.log;

/**
 * Eine Zeile aus einer der vier Tabellen. Nicht jede Tabelle nutzt jedes Feld:
 * Block: blk, oldS, newS, oldNbt, newNbt. Item: blk = Item-ID, amount, newNbt = Item-NBT.
 * Entity: blk = Typ, name, oldNbt = Daten des Opfers. Text: text.
 */
public class LogEntry {
    public long id;
    public long t;
    public String user = "";
    public String dim = "";
    public int x, y, z;
    public LogAction action;
    public String blk = "";
    public String oldS;
    public String newS;
    public String oldNbt;
    public String newNbt;
    public int amount;
    public String name = "";
    public String text = "";
    public boolean rolledBack;

    public static LogEntry of(long t, String user, String dim, int x, int y, int z, LogAction a) {
        LogEntry e = new LogEntry();
        e.t = t;
        e.user = user;
        e.dim = dim;
        e.x = x;
        e.y = y;
        e.z = z;
        e.action = a;
        return e;
    }
}
