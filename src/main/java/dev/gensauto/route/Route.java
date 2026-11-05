package dev.gensauto.route;

import java.util.UUID;

/**
 * A recorded walk: one frame per tick (20 per second) with the position reached, where you were looking
 * and which keys you were holding. Positions are stored relative to the start point.
 */
public final class Route {
    public static final int STRIDE = 6; // dx, dy, dz, yaw, pitch, keys
    public static final int FWD = 1, BACK = 2, LEFT = 4, RIGHT = 8, JUMP = 16, SNEAK = 32, SPRINT = 64, ATTACK = 128;
    public static final int MOVE = FWD | BACK | LEFT | RIGHT;
    /** A route ends "at the start" when its last frame is this close to where it began. */
    public static final double CLOSE_DISTANCE = 1.5;

    public String id = UUID.randomUUID().toString();
    public String name, server, dimension, tool;
    /** Id of the Playlist this route belongs to. */
    public String playlist;
    public long created;
    public boolean inPlaylist = true;
    public double startX, startY, startZ;
    public float[] frames = new float[0];

    public int size() { return frames == null ? 0 : frames.length / STRIDE; }
    public int seconds() { return size() / 20; }

    public double x(int i) { return startX + frames[i * STRIDE]; }
    public double y(int i) { return startY + frames[i * STRIDE + 1]; }
    public double z(int i) { return startZ + frames[i * STRIDE + 2]; }
    public float yaw(int i) { return frames[i * STRIDE + 3]; }
    public float pitch(int i) { return frames[i * STRIDE + 4]; }
    public int keys(int i) { return (int) frames[i * STRIDE + 5]; }

    /** Distance from frame i to a point; height counts half, so jumps and steps do not throw it off. */
    public double distance(int i, double px, double py, double pz) {
        double dx = x(i) - px, dy = (y(i) - py) * 0.5, dz = z(i) - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceToStart(double px, double py, double pz) {
        double dx = startX - px, dy = (startY - py) * 0.5, dz = startZ - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** How far the route ends from its start. */
    public double endGap() {
        int n = size();
        return n == 0 ? Double.MAX_VALUE : distanceToStart(x(n - 1), y(n - 1), z(n - 1));
    }

    /** Ends where it began, so it can be chained with other routes from the same point. */
    public boolean closed() { return endGap() <= CLOSE_DISTANCE; }
}
