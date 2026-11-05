package dev.gensauto.route;

import com.google.gson.Gson;
import dev.gensauto.data.GensConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes and their lists (Farm / Minería), saved in config/gensauto-routes.json on every change and
 * loaded when the game starts. Each server has its own lists; the "active" list of each kind is the one
 * J plays and the one new recordings go into.
 */
public final class RouteStore {
    private static final Gson GSON = new Gson();
    private static final class FileData {
        List<Playlist> playlists = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        Map<String, String> active = new HashMap<>();
    }

    private final Path file;
    private final List<Playlist> playlists = new ArrayList<>();
    private final List<Route> routes = new ArrayList<>();
    private final Map<String, String> active = new HashMap<>();

    public RouteStore(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) {
                FileData data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileData.class);
                if (data != null) {
                    if (data.playlists != null) data.playlists.stream().filter(p -> p != null && p.id != null && p.server != null && p.kind != null).forEach(playlists::add);
                    if (data.routes != null) data.routes.stream().filter(r -> r != null && r.size() > 0 && r.tool != null).forEach(routes::add);
                    if (data.active != null) active.putAll(data.active);
                }
            }
        } catch (Exception ex) {
            GensConfig.LOG.error("No se pudieron leer las rutas de {}", file, ex);
        }
        // Routes from before lists existed (or whose list is gone) go to the default list of their kind.
        boolean changed = false;
        for (Route r : routes) {
            Playlist p = byId(r.playlist);
            if (p == null || !Objects.equals(p.server, r.server)) { r.playlist = active(r.server, Playlist.kindOf(r.tool)).id; changed = true; }
        }
        if (changed) save();
    }

    // ---------------------------------------------------------------- lists

    public List<Playlist> playlists(String server, String kind) {
        return playlists.stream().filter(p -> Objects.equals(p.server, server) && p.kind.equals(kind))
                .sorted(Comparator.comparingLong(p -> p.created)).toList();
    }

    /** The list J plays for this kind of work; created as "Farm" / "Minería" the first time. */
    public Playlist active(String server, String kind) {
        Playlist p = byId(active.get(key(server, kind)));
        if (p != null && Objects.equals(p.server, server) && p.kind.equals(kind)) return p;
        var existing = playlists(server, kind);
        p = existing.isEmpty() ? null : existing.get(0);
        if (p == null) { p = new Playlist(Playlist.label(kind), server, kind); playlists.add(p); }
        active.put(key(server, kind), p.id);
        save();
        return p;
    }

    public void setActive(Playlist p) { active.put(key(p.server, p.kind), p.id); save(); }
    public boolean isActive(Playlist p) { return p.id.equals(active.get(key(p.server, p.kind))); }

    public Playlist create(String server, String kind) {
        String base = Playlist.label(kind);
        String name = base;
        for (int n = 2; nameTaken(server, kind, name); n++) name = base + " " + n;
        Playlist p = new Playlist(name, server, kind);
        playlists.add(p);
        save();
        return p;
    }

    public void rename(Playlist p, String name) {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) return;
        p.name = name.length() > 32 ? name.substring(0, 32) : name;
        save();
    }

    /** Deletes the list and its routes; the kind always keeps at least one list. */
    public void delete(Playlist p) {
        routes.removeIf(r -> p.id.equals(r.playlist));
        playlists.remove(p);
        if (isActive(p) || active.get(key(p.server, p.kind)) == null) active.remove(key(p.server, p.kind));
        save();
        active(p.server, p.kind);
    }

    // ---------------------------------------------------------------- routes

    public List<Route> routes(Playlist p) {
        return routes.stream().filter(r -> p.id.equals(r.playlist)).sorted(Comparator.comparingLong(r -> r.created)).toList();
    }

    /** Routes J can play now: enabled, ending where they start, same dimension, starting near you. */
    public List<Route> playable(Playlist p, String dimension, double x, double y, double z, double radius) {
        return routes(p).stream().filter(r -> r.inPlaylist && r.closed() && Objects.equals(r.dimension, dimension)
                && r.distanceToStart(x, y, z) <= radius).toList();
    }

    /** The enabled route of the list whose start is closest to you (to point you there), or null. */
    public Route nearestStart(Playlist p, String dimension, double x, double y, double z) {
        return routes(p).stream().filter(r -> r.inPlaylist && r.closed() && Objects.equals(r.dimension, dimension))
                .min(Comparator.comparingDouble(r -> r.distanceToStart(x, y, z))).orElse(null);
    }

    /** "Azada 3": the next free number for that tool on this server. */
    public String nextName(String server, String toolLabel) {
        for (int n = 1; ; n++) {
            String name = toolLabel + " " + n;
            if (routes.stream().noneMatch(r -> Objects.equals(r.server, server) && name.equalsIgnoreCase(r.name))) return name;
        }
    }

    public void add(Route route) { routes.add(route); save(); }
    public void remove(Route route) { routes.remove(route); save(); }
    public void move(Route route, Playlist to) { route.playlist = to.id; save(); }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            FileData data = new FileData();
            data.playlists = playlists; data.routes = routes; data.active = active;
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            GensConfig.LOG.error("No se pudieron guardar las rutas en {}", file, ex);
        }
    }

    private Playlist byId(String id) {
        if (id == null) return null;
        for (Playlist p : playlists) if (p.id.equals(id)) return p;
        return null;
    }

    private boolean nameTaken(String server, String kind, String name) {
        return playlists(server, kind).stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
    }

    private static String key(String server, String kind) { return server + "|" + kind; }
}
