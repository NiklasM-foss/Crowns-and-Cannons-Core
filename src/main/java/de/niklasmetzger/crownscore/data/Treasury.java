package de.niklasmetzger.crownscore.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** Staatskasse einer Nation samt Darlehen. */
public class Treasury {
    public double stand;
    public final List<Loan> darlehen = new ArrayList<>();
    public int nextLoanId = 1;

    public double schulden() {
        double s = 0;
        for (Loan l : darlehen) s += l.rest;
        return s;
    }

    public boolean imVerzug() {
        for (Loan l : darlehen) if (l.verzug) return true;
        return false;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putDouble("stand", stand);
        t.putInt("nextLoanId", nextLoanId);
        ListTag list = new ListTag();
        for (Loan l : darlehen) list.add(l.toNbt());
        t.put("darlehen", list);
        return t;
    }

    public static Treasury fromNbt(CompoundTag t) {
        Treasury tr = new Treasury();
        tr.stand = t.getDouble("stand");
        tr.nextLoanId = Math.max(1, t.getInt("nextLoanId"));
        ListTag list = t.getList("darlehen", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) tr.darlehen.add(Loan.fromNbt(list.getCompound(i)));
        return tr;
    }

    /** Ein Darlehen der Zentralbank. */
    public static class Loan {
        public int id;
        public double betrag;
        public double gesamt;
        public double rest;
        public double rate;
        public double zinsProzent;
        public int wochen;
        public long aufgenommen;
        public long naechsteRate;
        public boolean verzug;
        public int verzugRaten;

        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putDouble("betrag", betrag);
            t.putDouble("gesamt", gesamt);
            t.putDouble("rest", rest);
            t.putDouble("rate", rate);
            t.putDouble("zinsProzent", zinsProzent);
            t.putInt("wochen", wochen);
            t.putLong("aufgenommen", aufgenommen);
            t.putLong("naechsteRate", naechsteRate);
            t.putBoolean("verzug", verzug);
            t.putInt("verzugRaten", verzugRaten);
            return t;
        }

        public static Loan fromNbt(CompoundTag t) {
            Loan l = new Loan();
            l.id = t.getInt("id");
            l.betrag = t.getDouble("betrag");
            l.gesamt = t.getDouble("gesamt");
            l.rest = t.getDouble("rest");
            l.rate = t.getDouble("rate");
            l.zinsProzent = t.getDouble("zinsProzent");
            l.wochen = t.getInt("wochen");
            l.aufgenommen = t.getLong("aufgenommen");
            l.naechsteRate = t.getLong("naechsteRate");
            l.verzug = t.getBoolean("verzug");
            l.verzugRaten = t.getInt("verzugRaten");
            return l;
        }
    }
}
