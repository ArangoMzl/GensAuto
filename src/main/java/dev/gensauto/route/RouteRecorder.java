package dev.gensauto.route;

import dev.gensauto.Inputs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Arrays;

/** Records one frame per tick while you play: where you ended up, where you looked and which keys you held. */
public final class RouteRecorder {
    public static final int MAX_FRAMES = 20 * 60 * 30; // 30 minutes

    private Route route;
    private float[] buffer = new float[0];
    private int frames;

    public boolean recording() { return route != null; }
    public Route route() { return route; }
    public int seconds() { return frames / 20; }

    public void start(LocalPlayer p, String server, String dimension, String tool) {
        route = new Route();
        route.server = server; route.dimension = dimension; route.tool = tool;
        route.created = System.currentTimeMillis();
        route.startX = p.getX(); route.startY = p.getY(); route.startZ = p.getZ();
        buffer = new float[Route.STRIDE * 20 * 60];
        frames = 0;
    }

    /** Runs at the end of every tick, after the player moved. */
    public void tick(Minecraft mc) {
        if (route == null || mc.player == null) return;
        LocalPlayer p = mc.player;
        int at = frames * Route.STRIDE;
        if (at + Route.STRIDE > buffer.length) buffer = Arrays.copyOf(buffer, buffer.length * 2);
        buffer[at] = round(p.getX() - route.startX);
        buffer[at + 1] = round(p.getY() - route.startY);
        buffer[at + 2] = round(p.getZ() - route.startZ);
        buffer[at + 3] = round(p.getYRot());
        buffer[at + 4] = round(p.getXRot());
        buffer[at + 5] = Inputs.read(mc);
        frames++;
    }

    public boolean full() { return frames >= MAX_FRAMES; }

    /** How far you are from the point where the recording started. */
    public double distanceToStart(LocalPlayer p) { return route == null ? 0 : route.distanceToStart(p.getX(), p.getY(), p.getZ()); }

    /** Ends the recording and returns the route (not saved yet). */
    public Route finish() {
        Route done = route;
        if (done != null) done.frames = Arrays.copyOf(buffer, frames * Route.STRIDE);
        cancel();
        return done;
    }

    public void cancel() { route = null; buffer = new float[0]; frames = 0; }

    private static float round(double v) { return Math.round(v * 1000) / 1000f; }
}
