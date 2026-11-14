package de.niklasmetzger.crownscore.perms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gespeicherter Stand der Rechteverwaltung, liegt als JSON unter world/crownscore/permissions.json. */
public class PermData {
    public Map<String, Group> groups = new LinkedHashMap<>();
    public Map<String, User> users = new LinkedHashMap<>();
    public Map<String, List<String>> tracks = new LinkedHashMap<>();

    /** Ein Recht: Knoten, Wert, optional Ablaufzeit (0 = dauerhaft) und Dimension (leer = ueberall). */
    public static class Node {
        public String node;
        public boolean value = true;
        public long expiry;
        public String dim = "";

        public Node() {}

        public Node(String node, boolean value, long expiry, String dim) {
            this.node = node;
            this.value = value;
            this.expiry = expiry;
            this.dim = dim == null ? "" : dim;
        }

        public boolean active(long now) {
            return expiry == 0 || expiry > now;
        }
    }

    /** Mitgliedschaft in einer Gruppe, optional befristet. */
    public static class Parent {
        public String group;
        public long expiry;

        public Parent() {}

        public Parent(String group, long expiry) {
            this.group = group;
            this.expiry = expiry;
        }

        public boolean active(long now) {
            return expiry == 0 || expiry > now;
        }
    }

    /** Gemeinsame Teile von Gruppen und Spielern. */
    public static class Holder {
        public List<Node> permissions = new ArrayList<>();
        public List<Parent> parents = new ArrayList<>();
        public String prefix = "";
        public String suffix = "";

        public void prune(long now) {
            permissions.removeIf(n -> !n.active(now));
            parents.removeIf(p -> !p.active(now));
        }
    }

    public static class Group extends Holder {
        public int weight;
        public String displayName = "";
    }

    public static class User extends Holder {
        public String name = "";
    }
}
