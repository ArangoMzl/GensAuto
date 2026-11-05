package dev.gensauto;

import dev.gensauto.data.GensConfig;
import dev.gensauto.data.ToolText;
import dev.gensauto.route.Playlist;
import dev.gensauto.route.Route;
import dev.gensauto.route.RouteMath;
import dev.gensauto.route.RoutePlayer;
import dev.gensauto.route.RouteRecorder;
import dev.gensauto.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Records your routes and plays them back with the same inputs you use: it holds the left click (through
 * the game's own "continue attacking" step, once per tick like a held mouse button), holds movement keys
 * and turns the camera.
 *
 * <p>J plays the active list of the tool in your hand (Farm for the hoe, Minería for the pick): a random
 * route that starts where you stand, then another, never the same one twice in a row. It pauses while any
 * menu or chat is open and stops if you change tool, take damage, get teleported, leave the route or the server.
 */
public final class AutoController {
    /** Routes must start this close to you to be played. */
    public static final double START_RADIUS = 3.0;
    /** A recording saves itself when you come back this close to its start, after having gone this far. */
    private static final double AUTO_SAVE_DISTANCE = 0.8, ARM_DISTANCE = 3.0;
    private static final long MARKER_MILLIS = 15_000;

    private static AutoController instance;

    private final GensConfig config;
    private final RouteStore routes;
    private final RouteRecorder recorder = new RouteRecorder();
    private final RoutePlayer player = new RoutePlayer();
    private final Random random = new Random();
    private boolean running, paused, attacking, savedPauseOnLostFocus, loop, recordArmed;
    private String toolKey, lastRouteId;
    private Playlist list;
    private int routesPlayed, ticks;
    private float health;
    private Vec3 lastPos, recordPos;
    // Start point shown with particles: while recording, or for a while after J could not start.
    private Vec3 marker;
    private long markerUntil;
    // Panel line while idle with a tool in hand.
    private String idleInfo;
    private boolean idleReady;

    public AutoController(GensConfig config, RouteStore routes) { this.config = config; this.routes = routes; instance = this; }

    /** Read by the mixin: true while the automation holds the left click. */
    public static boolean wantsAttack() { return instance != null && instance.attacking; }

    public boolean running() { return running; }
    public boolean paused() { return paused; }
    public RouteRecorder recorder() { return recorder; }
    public RouteStore routes() { return routes; }
    public int routesPlayed() { return routesPlayed; }
    public boolean recordArmed() { return recordArmed; }
    public String idleInfo() { return idleInfo; }
    public boolean idleReady() { return idleReady; }

    public String status() {
        if (!running) return null;
        if (paused) return "PAUSA (menú abierto)";
        Route r = player.route();
        if (r == null) return "RUTAS";
        if (player.phase() == RoutePlayer.Phase.APPROACH) return r.name + " · yendo al inicio";
        return r.name + " " + RouteMath.clock(player.playedSeconds()) + "/" + RouteMath.clock(r.seconds())
                + (loop ? " · " + routesPlayed + " hechas" : "");
    }

    // ---------------------------------------------------------------- start / stop

    /** J: plays the active list of the tool in hand, or stops. */
    public void toggle(Minecraft mc) {
        if (running) { stop(mc, "Automático detenido", ChatFormatting.YELLOW); return; }
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (recorder.recording()) { say(mc, "Termina la grabación primero", ChatFormatting.RED); return; }
        String held = heldKey(p);
        if (held == null) { say(mc, "Toma tu pico o tu azada en la mano para iniciar", ChatFormatting.RED); return; }
        Playlist active = routes.active(GensAutoClient.server(mc), Playlist.kindOf(held));
        List<Route> here = playable(mc, active);
        if (here.isEmpty()) { explainWhyNot(mc, p, active); return; }
        begin(mc, p, held);
        list = active;
        loop = true;
        nextRoute(mc);
        say(mc, "Lista «" + active.name + "» iniciada · " + here.size() + (here.size() == 1 ? " ruta" : " rutas")
                + " desde aquí · " + GensAutoClient.keyName(GensAutoClient.autoKey) + " para detener", ChatFormatting.GREEN);
    }

    private void explainWhyNot(Minecraft mc, LocalPlayer p, Playlist active) {
        String rec = GensAutoClient.keyName(GensAutoClient.recordKey);
        if (routes.routes(active).isEmpty()) {
            say(mc, "La lista «" + active.name + "» está vacía. Párate donde quieras empezar y presiona " + rec + " para grabar una ruta", ChatFormatting.YELLOW);
            return;
        }
        Route nearest = routes.nearestStart(active, GensAutoClient.dimension(mc), p.getX(), p.getY(), p.getZ());
        if (nearest == null) {
            say(mc, "La lista «" + active.name + "» no tiene rutas activas en este mundo (revísala con "
                    + GensAutoClient.keyName(GensAutoClient.routesKey) + ")", ChatFormatting.YELLOW);
            return;
        }
        showMarker(new Vec3(nearest.startX, nearest.startY, nearest.startZ), MARKER_MILLIS);
        say(mc, String.format(Locale.ROOT, "Estás a %.0f bloques del inicio de la lista «%s». Te lo marco con partículas",
                nearest.distanceToStart(p.getX(), p.getY(), p.getZ()), active.name), ChatFormatting.YELLOW);
    }

    /** "Probar" in the routes menu: plays one route once and stops. */
    public void playOnce(Minecraft mc, Route route) {
        LocalPlayer p = mc.player;
        if (p == null || running || recorder.recording()) return;
        String held = heldKey(p);
        if (held == null || !Playlist.kindOf(held).equals(Playlist.kindOf(route.tool))) {
            say(mc, "Toma en la mano la herramienta de esa ruta", ChatFormatting.RED); return;
        }
        double d = route.distanceToStart(p.getX(), p.getY(), p.getZ());
        if (d > START_RADIUS) {
            showMarker(new Vec3(route.startX, route.startY, route.startZ), MARKER_MILLIS);
            say(mc, String.format(Locale.ROOT, "Ve al inicio de «%s» (a %.0f bloques); te lo marco con partículas", route.name, d), ChatFormatting.YELLOW);
            return;
        }
        begin(mc, p, held);
        loop = false;
        player.begin(route);
        say(mc, "Probando «" + route.name + "»", ChatFormatting.GREEN);
    }

    private void begin(Minecraft mc, LocalPlayer p, String held) {
        running = true;
        toolKey = held;
        health = p.getHealth();
        lastPos = p.position();
        routesPlayed = 0;
        lastRouteId = null;
        marker = null;
        savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false; // keep playing if you switch windows
    }

    public void stop(Minecraft mc, String reason, ChatFormatting color) {
        if (!running) return;
        running = false; attacking = false; paused = false;
        player.clear();
        Inputs.release(mc);
        mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
        if (reason != null) {
            say(mc, reason, color);
            ding(mc, color == ChatFormatting.RED ? 0.6f : 1.2f);
        }
    }

    // ---------------------------------------------------------------- recording

    /** U: start recording where you stand, or save now. */
    public void toggleRecording(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (recorder.recording()) { finishRecording(mc); return; }
        if (running) { say(mc, "Detén el automático antes de grabar", ChatFormatting.RED); return; }
        String held = heldKey(p);
        if (held == null) { say(mc, "Toma tu pico o tu azada en la mano para grabar", ChatFormatting.RED); return; }
        recorder.start(p, GensAutoClient.server(mc), GensAutoClient.dimension(mc), held);
        recordPos = p.position();
        recordArmed = false;
        Playlist target = routes.active(GensAutoClient.server(mc), Playlist.kindOf(held));
        say(mc, "Grabando para la lista «" + target.name + "». El inicio queda marcado con partículas: "
                + "al volver a pasar por ahí se guarda sola (o " + GensAutoClient.keyName(GensAutoClient.recordKey) + ")", ChatFormatting.AQUA);
        ding(mc, 1.6f);
    }

    private void finishRecording(Minecraft mc) {
        Route route = recorder.finish();
        recordArmed = false;
        if (route == null) return;
        if (route.size() < 60) { say(mc, "Grabación descartada: dura menos de 3 segundos", ChatFormatting.YELLOW); return; }
        Playlist target = routes.active(route.server, Playlist.kindOf(route.tool));
        route.playlist = target.id;
        route.name = routes.nextName(route.server, Playlist.FARM.equals(target.kind) ? "Farm" : "Mina");
        route.inPlaylist = route.closed();
        routes.add(route);
        if (route.closed()) {
            say(mc, "Ruta «" + route.name + "» guardada en la lista «" + target.name + "» (" + RouteMath.clock(route.seconds()) + ")", ChatFormatting.GREEN);
        } else {
            say(mc, String.format(Locale.ROOT, "Ruta «%s» guardada en «%s», pero terminaste a %.1f bloques del inicio: queda desactivada. Puedes probarla sola en el menú",
                    route.name, target.name, route.endGap()), ChatFormatting.YELLOW);
        }
        ding(mc, 1.2f);
    }

    public void cancelRecording(Minecraft mc, String reason) {
        if (!recorder.recording()) return;
        recorder.cancel();
        recordArmed = false;
        if (reason != null) say(mc, reason, ChatFormatting.RED);
    }

    // ---------------------------------------------------------------- ticks

    /** Runs at the start of every tick, before the game reads the keys and moves the player. */
    public void tick(Minecraft mc) {
        if (!running) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { stop(mc, null, null); return; }
        if (!toolKey.equals(heldKey(p))) { stop(mc, "Automático detenido: cambiaste de herramienta", ChatFormatting.YELLOW); return; }
        if (p.isDeadOrDying() || (config.stopOnDamage && p.getHealth() < health - 0.01f)) {
            stop(mc, "Automático detenido: recibiste daño", ChatFormatting.RED); return;
        }
        health = p.getHealth();
        Vec3 pos = p.position();
        boolean teleported = lastPos != null && pos.distanceToSqr(lastPos) > 64;
        lastPos = pos;
        if (teleported) { stop(mc, "Automático detenido: te teletransportaron", ChatFormatting.YELLOW); return; }

        paused = mc.screen != null;
        if (paused) { attacking = false; Inputs.release(mc); return; }
        var result = player.tick(mc, p, config.turnSpeed);
        attacking = player.attack();
        if (result == RoutePlayer.Result.LOST) {
            Route r = player.route();
            stop(mc, "Automático detenido: me salí de la ruta «" + (r == null ? "?" : r.name) + "» (algo bloqueó el camino)", ChatFormatting.RED);
        } else if (result == RoutePlayer.Result.DONE) {
            routesPlayed++;
            if (!loop) stop(mc, "Ruta terminada", ChatFormatting.GREEN);
            else nextRoute(mc);
        }
    }

    /** Runs at the end of every tick: records where you ended up, auto-saves and draws the start marker. */
    public void endTick(Minecraft mc) {
        ticks++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { if (recorder.recording()) recorder.cancel(); return; }
        if (recorder.recording()) {
            Vec3 pos = p.position();
            if (recordPos != null && pos.distanceToSqr(recordPos) > 64) { cancelRecording(mc, "Grabación cancelada: te teletransportaron"); return; }
            recordPos = pos;
            recorder.tick(mc);
            double d = recorder.distanceToStart(p);
            if (d > ARM_DISTANCE) recordArmed = true;
            if (recorder.full() || (recordArmed && d <= AUTO_SAVE_DISTANCE && recorder.seconds() >= 3)) { finishRecording(mc); return; }
            Route r = recorder.route();
            if (ticks % 4 == 0) drawMarker(mc, r.startX, r.startY, r.startZ);
        } else if (marker != null && System.currentTimeMillis() < markerUntil) {
            if (ticks % 4 == 0) drawMarker(mc, marker.x, marker.y, marker.z);
        }
        if (ticks % 10 == 0) updateIdleInfo(mc, p);
    }

    private void updateIdleInfo(Minecraft mc, LocalPlayer p) {
        idleInfo = null; idleReady = false;
        if (running || recorder.recording()) return;
        String held = heldKey(p);
        if (held == null) return;
        Playlist active = routes.active(GensAutoClient.server(mc), Playlist.kindOf(held));
        int here = playable(mc, active).size();
        if (here > 0) { idleInfo = "Lista «" + active.name + "»: " + here + (here == 1 ? " ruta" : " rutas") + " aquí"; idleReady = true; return; }
        if (routes.routes(active).isEmpty()) { idleInfo = "Lista «" + active.name + "» vacía · " + GensAutoClient.keyName(GensAutoClient.recordKey) + " grabar"; return; }
        Route nearest = routes.nearestStart(active, GensAutoClient.dimension(mc), p.getX(), p.getY(), p.getZ());
        idleInfo = nearest == null ? "Lista «" + active.name + "»: sin rutas activas aquí"
                : String.format(Locale.ROOT, "Lista «%s»: inicio a %.0f bl", active.name, nearest.distanceToStart(p.getX(), p.getY(), p.getZ()));
    }

    private void nextRoute(Minecraft mc) {
        Route next = list == null ? null : RouteMath.pickNext(playable(mc, list), lastRouteId, random);
        if (next == null) { stop(mc, "Automático detenido: no hay rutas de «" + (list == null ? "?" : list.name) + "» que empiecen aquí", ChatFormatting.YELLOW); return; }
        lastRouteId = next.id;
        player.begin(next);
    }

    private List<Route> playable(Minecraft mc, Playlist p) {
        LocalPlayer pl = mc.player;
        return routes.playable(p, GensAutoClient.dimension(mc), pl.getX(), pl.getY(), pl.getZ(), START_RADIUS);
    }

    private void showMarker(Vec3 at, long millis) { marker = at; markerUntil = System.currentTimeMillis() + millis; }

    /** A column of light and a turning ring of green sparks on the start point. */
    private void drawMarker(Minecraft mc, double x, double y, double z) {
        var level = mc.level;
        for (int i = 0; i < 6; i++) level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, x, y + 0.15 + i * 0.45, z, 0, 0.005, 0);
        double turn = (ticks % 40) / 40.0 * Math.PI * 2;
        for (int k = 0; k < 4; k++) {
            double a = turn + k * Math.PI / 2;
            level.addAlwaysVisibleParticle(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(a) * 0.7, y + 0.1, z + Math.sin(a) * 0.7, 0, 0, 0);
        }
    }

    /** Key of the Gens tool in the main hand ("PICO DE MINERIA"), or null for any other item. */
    public static String heldKey(LocalPlayer p) {
        var stack = p.getMainHandItem();
        var lore = stack.get(DataComponents.LORE);
        if (lore == null || ToolText.parseLore(lore.lines().stream().map(Component::getString).toList()) == null) return null;
        return ToolText.key(stack.getHoverName().getString());
    }

    static void say(Minecraft mc, String text, ChatFormatting color) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[GensAuto] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(color)), false);
    }

    private static void ding(Minecraft mc, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, 0.8f));
    }
}
