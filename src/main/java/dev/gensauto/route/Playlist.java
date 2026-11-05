package dev.gensauto.route;

import java.util.UUID;

/** A named group of routes for one kind of work (Farm or Minería) on one server. */
public final class Playlist {
    public static final String FARM = "FARM", MINE = "MINA";

    public String id = UUID.randomUUID().toString();
    public String name, server, kind;
    public long created;

    public Playlist() {}
    public Playlist(String name, String server, String kind) {
        this.name = name; this.server = server; this.kind = kind; this.created = System.currentTimeMillis();
    }

    /** The kind of work a Gens tool does: the hoe farms, everything else mines. */
    public static String kindOf(String toolKey) { return toolKey != null && toolKey.contains("AZADA") ? FARM : MINE; }

    public static String label(String kind) { return FARM.equals(kind) ? "Farm" : "Minería"; }
}
