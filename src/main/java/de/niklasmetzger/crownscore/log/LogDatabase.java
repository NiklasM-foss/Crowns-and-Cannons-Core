package de.niklasmetzger.crownscore.log;

import de.niklasmetzger.crownscore.CrownsCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Blockprotokoll in einer eingebetteten H2-Datenbank unter world/crownscore/protokoll.mv.db.
 * Schreiben laeuft gesammelt in einem eigenen Thread, Abfragen in einem zweiten, der Server-Tick wartet nie.
 */
public final class LogDatabase {
    private static final String COLS = "t, u, dim, x, y, z, a, blk, old_s, new_s, old_nbt, new_nbt, amount, nm, msg";

    /** Schutz fuer die Schreibverbindung, getrennt vom Start/Stopp-Monitor. */
    private static final Object WRITE = new Object();
    private static Connection writeConn;
    private static Connection readConn;
    private static final BlockingQueue<LogEntry> QUEUE = new LinkedBlockingQueue<>();
    private static Thread writer;
    private static volatile boolean running;
    /** Nimmt schon Eintraege an, waehrend die Datenbank im Hintergrund oeffnet. */
    private static volatile boolean accepting;
    private static ExecutorService reader;

    private LogDatabase() {}

    public static boolean ready() {
        return running;
    }

    /** Oeffnet die Datenbank im Hintergrund, damit der Serverstart nie darauf wartet. */
    public static synchronized void open(MinecraftServer server) {
        if (running || accepting) return;
        accepting = true;
        Thread t = new Thread(() -> connect(server), "CrownsCore-Protokoll-Start");
        t.setDaemon(true);
        t.start();
    }

    private static synchronized void connect(MinecraftServer server) {
        if (!accepting) return;
        try {
            Path dir = server.getWorldPath(LevelResource.ROOT).resolve("crownscore");
            Files.createDirectories(dir);
            String url = "jdbc:h2:file:" + dir.resolve("protokoll").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
            org.h2.Driver driver = new org.h2.Driver();
            writeConn = driver.connect(url, new Properties());
            readConn = driver.connect(url, new Properties());
            try (Statement s = writeConn.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS cc_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, t BIGINT NOT NULL, u VARCHAR(64) NOT NULL,"
                        + " dim VARCHAR(100) NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, a TINYINT NOT NULL,"
                        + " blk VARCHAR(200), old_s VARCHAR(2000), new_s VARCHAR(2000), old_nbt CHARACTER LARGE OBJECT,"
                        + " new_nbt CHARACTER LARGE OBJECT, amount INT, nm VARCHAR(100), msg VARCHAR(4096), rb BOOLEAN DEFAULT FALSE NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS cc_log_pos ON cc_log(dim, x, z, y)");
                s.execute("CREATE INDEX IF NOT EXISTS cc_log_t ON cc_log(t)");
                s.execute("CREATE INDEX IF NOT EXISTS cc_log_u ON cc_log(u, t)");
            }
            writeConn.setAutoCommit(false);
            running = true;
            reader = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CrownsCore-Protokoll-Abfrage");
                t.setDaemon(true);
                return t;
            });
            writer = new Thread(LogDatabase::writeLoop, "CrownsCore-Protokoll-Schreiber");
            writer.setDaemon(true);
            writer.start();
            CrownsCore.LOGGER.info("Blockprotokoll bereit: {}", dir.resolve("protokoll.mv.db"));
        } catch (Exception e) {
            running = false;
            accepting = false;
            QUEUE.clear();
            CrownsCore.LOGGER.error("Blockprotokoll konnte nicht gestartet werden, es wird nichts protokolliert", e);
        }
    }

    public static synchronized void close() {
        accepting = false;
        if (!running) {
            QUEUE.clear();
            return;
        }
        running = false;
        try {
            writer.join(10_000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        flush(); // was der Schreiber nicht mehr geschafft hat
        reader.shutdown();
        try {
            reader.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            writeConn.close();
            readConn.close();
        } catch (SQLException e) {
            CrownsCore.LOGGER.warn("Blockprotokoll: Fehler beim Schliessen", e);
        }
    }

    public static void add(LogEntry e) {
        if (accepting) QUEUE.add(e);
    }

    /** Werden gerade Eintraege angenommen? (auch waehrend die Datenbank noch oeffnet) */
    public static boolean accepting() {
        return accepting;
    }

    public static int queued() {
        return QUEUE.size();
    }

    private static void writeLoop() {
        while (running) {
            try {
                LogEntry first = QUEUE.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                List<LogEntry> batch = new ArrayList<>();
                batch.add(first);
                QUEUE.drainTo(batch, 4999);
                insert(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                CrownsCore.LOGGER.error("Blockprotokoll: Schreibfehler", e);
            }
        }
    }

    private static void flush() {
        List<LogEntry> rest = new ArrayList<>();
        QUEUE.drainTo(rest);
        if (!rest.isEmpty()) insert(rest);
    }

    private static void insert(List<LogEntry> batch) {
        synchronized (WRITE) {
            insertLocked(batch);
        }
    }

    private static void insertLocked(List<LogEntry> batch) {
        try (PreparedStatement ps = writeConn.prepareStatement("INSERT INTO cc_log (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (LogEntry e : batch) {
                ps.setLong(1, e.t);
                ps.setString(2, cut(e.user, 64));
                ps.setString(3, e.dim);
                ps.setInt(4, e.x);
                ps.setInt(5, e.y);
                ps.setInt(6, e.z);
                ps.setInt(7, e.action.id);
                ps.setString(8, cut(e.blk, 200));
                ps.setString(9, e.oldS);
                ps.setString(10, e.newS);
                ps.setString(11, e.oldNbt);
                ps.setString(12, e.newNbt);
                if (e.amount == 0) ps.setNull(13, Types.INTEGER);
                else ps.setInt(13, e.amount);
                ps.setString(14, cut(e.name, 100));
                ps.setString(15, cut(e.text, 4096));
                ps.addBatch();
            }
            ps.executeBatch();
            writeConn.commit();
        } catch (SQLException ex) {
            CrownsCore.LOGGER.error("Blockprotokoll: {} Eintraege nicht gespeichert", batch.size(), ex);
            try {
                writeConn.rollback();
            } catch (SQLException ignored) {
                // nichts mehr zu retten
            }
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ---- Lesen ------------------------------------------------------------

    public interface SqlTask<T> {
        T run(Connection c) throws SQLException;
    }

    /** Fuehrt eine Abfrage im Lese-Thread aus und liefert das Ergebnis im Server-Thread. */
    public static <T> void async(MinecraftServer server, SqlTask<T> task, java.util.function.Consumer<T> done, java.util.function.Consumer<Exception> fail) {
        if (!running) {
            fail.accept(new IllegalStateException("Das Blockprotokoll ist nicht aktiv."));
            return;
        }
        reader.execute(() -> {
            try {
                // Was noch in der Warteschlange liegt, soll in der Abfrage schon auftauchen
                if (!QUEUE.isEmpty()) flush();
                T r = task.run(readConn);
                server.execute(() -> done.accept(r));
            } catch (Exception e) {
                server.execute(() -> fail.accept(e));
            }
        });
    }

    public static List<LogEntry> select(Connection c, String where, List<Object> params, int limit, boolean asc) throws SQLException {
        String sql = "SELECT id, " + COLS + ", rb FROM cc_log" + (where.isEmpty() ? "" : " WHERE " + where)
                + " ORDER BY t " + (asc ? "ASC" : "DESC") + ", id " + (asc ? "ASC" : "DESC") + (limit > 0 ? " LIMIT " + limit : "");
        List<LogEntry> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(read(rs));
            }
        }
        return out;
    }

    public static long count(Connection c, String where, List<Object> params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM cc_log" + (where.isEmpty() ? "" : " WHERE " + where))) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    public static List<LogEntry> byIds(Connection c, Collection<Long> ids) throws SQLException {
        List<LogEntry> out = new ArrayList<>();
        List<Long> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += 500) {
            List<Long> part = all.subList(i, Math.min(all.size(), i + 500));
            StringBuilder in = new StringBuilder();
            for (int j = 0; j < part.size(); j++) in.append(j == 0 ? "?" : ",?");
            out.addAll(select(c, "id IN (" + in + ")", new ArrayList<>(part), 0, true));
        }
        return out;
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object o = params.get(i);
            if (o instanceof Long l) ps.setLong(i + 1, l);
            else if (o instanceof Integer n) ps.setInt(i + 1, n);
            else if (o instanceof Boolean b) ps.setBoolean(i + 1, b);
            else ps.setString(i + 1, String.valueOf(o));
        }
    }

    private static LogEntry read(ResultSet rs) throws SQLException {
        LogEntry e = new LogEntry();
        e.id = rs.getLong("id");
        e.t = rs.getLong("t");
        e.user = rs.getString("u");
        e.dim = rs.getString("dim");
        e.x = rs.getInt("x");
        e.y = rs.getInt("y");
        e.z = rs.getInt("z");
        e.action = LogAction.byId(rs.getInt("a"));
        e.blk = nz(rs.getString("blk"));
        e.oldS = rs.getString("old_s");
        e.newS = rs.getString("new_s");
        e.oldNbt = rs.getString("old_nbt");
        e.newNbt = rs.getString("new_nbt");
        e.amount = rs.getInt("amount");
        e.name = nz(rs.getString("nm"));
        e.text = nz(rs.getString("msg"));
        e.rolledBack = rs.getBoolean("rb");
        return e;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Markiert Eintraege als zurueckgesetzt oder wiederhergestellt. */
    public static void markRolledBack(MinecraftServer server, Collection<Long> ids, boolean rb) {
        if (ids.isEmpty() || !running) return;
        List<Long> all = new ArrayList<>(ids);
        reader.execute(() -> {
            try {
                synchronized (WRITE) {
                    try (PreparedStatement ps = writeConn.prepareStatement("UPDATE cc_log SET rb = ? WHERE id = ?")) {
                        for (Long id : all) {
                            ps.setBoolean(1, rb);
                            ps.setLong(2, id);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        writeConn.commit();
                    }
                }
            } catch (SQLException e) {
                CrownsCore.LOGGER.error("Blockprotokoll: Rollback-Markierung fehlgeschlagen", e);
            }
        });
    }

    public static int purge(Connection ignored, long before) throws SQLException {
        synchronized (WRITE) {
            try (PreparedStatement ps = writeConn.prepareStatement("DELETE FROM cc_log WHERE t < ?")) {
                ps.setLong(1, before);
                int n = ps.executeUpdate();
                writeConn.commit();
                return n;
            }
        }
    }
}
