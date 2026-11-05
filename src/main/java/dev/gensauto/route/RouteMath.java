package dev.gensauto.route;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure helpers for replaying routes (no Minecraft classes, so they are unit-tested). */
public final class RouteMath {
    private RouteMath() {}

    /** -180..180 */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /**
     * Which of W/A/S/D to hold to move towards (dx, dz) while looking at yaw. Minecraft yaw 0 looks to +Z
     * and "left" (A) moves to +X at that yaw. Diagonals hold two keys, like a player would.
     */
    public static int moveKeys(double dx, double dz, float yaw) {
        if (dx * dx + dz * dz < 1e-6) return 0;
        double desired = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = wrapDegrees(desired - yaw);
        int keys = 0;
        if (Math.abs(rel) < 67.5) keys |= Route.FWD;
        else if (Math.abs(rel) > 112.5) keys |= Route.BACK;
        if (rel < -22.5 && rel > -157.5) keys |= Route.LEFT;
        else if (rel > 22.5 && rel < 157.5) keys |= Route.RIGHT;
        return keys;
    }

    private static final String[] DIRECTIONS = {"adelante", "adelante a la derecha", "a la derecha", "atrás a la derecha",
            "atrás", "atrás a la izquierda", "a la izquierda", "adelante a la izquierda"};

    /** Where (dx, dz) is for someone looking at yaw, in words: "adelante", "atrás a la izquierda"... */
    public static String direction(double dx, double dz, float yaw) {
        double rel = wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - yaw);
        int i = (int) Math.round(rel / 45.0);
        return DIRECTIONS[Math.floorMod(i, 8)];
    }

    /** A random route from the list, never the same one twice in a row when there is another choice. */
    public static Route pickNext(List<Route> candidates, String lastId, Random random) {
        if (candidates.isEmpty()) return null;
        List<Route> pool = new ArrayList<>(candidates);
        if (pool.size() > 1) pool.removeIf(r -> r.id.equals(lastId));
        return pool.get(random.nextInt(pool.size()));
    }

    public static String clock(int seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
