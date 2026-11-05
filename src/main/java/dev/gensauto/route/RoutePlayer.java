package dev.gensauto.route;

import dev.gensauto.Inputs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Replays a route following its positions, not just its keys: the look and the actions (click, jump,
 * sneak, sprint) come from the recording, and W/A/S/D are chosen each tick to stay on the recorded
 * path. That way small differences (lag, a missing speed effect) are corrected instead of adding up.
 */
public final class RoutePlayer {
    public enum Result { RUNNING, DONE, LOST }
    public enum Phase { APPROACH, PLAY }

    private static final int LOOKAHEAD = 3, SKIP_WINDOW = 12, MAX_LAG_TICKS = 60, MAX_APPROACH_TICKS = 200;
    private static final double ON_PATH = 0.75, AT_START = 0.4;

    private Route route;
    private Phase phase;
    private int index, lagTicks, approachTicks;
    private boolean attack;

    public Route route() { return route; }
    public Phase phase() { return phase; }
    public int index() { return index; }
    public boolean attack() { return attack; }

    public void begin(Route route) {
        this.route = route;
        phase = Phase.APPROACH;
        index = lagTicks = approachTicks = 0;
        attack = false;
    }

    public void clear() { route = null; attack = false; }

    public Result tick(Minecraft mc, LocalPlayer p, float turnSpeed) {
        if (route == null || route.size() == 0) return Result.DONE;
        return phase == Phase.APPROACH ? approach(mc, p, turnSpeed) : play(mc, p);
    }

    /** Walk the last bit to the start point and turn to the recorded first look. */
    private Result approach(Minecraft mc, LocalPlayer p, float turnSpeed) {
        attack = false;
        double dx = route.startX - p.getX(), dz = route.startZ - p.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean turned = Inputs.turn(p, route.yaw(0), route.pitch(0), turnSpeed);
        Inputs.apply(mc, distance > AT_START ? RouteMath.moveKeys(dx, dz, p.getYRot()) : 0);
        if (distance <= AT_START && turned) { phase = Phase.PLAY; index = 0; return Result.RUNNING; }
        return ++approachTicks > MAX_APPROACH_TICKS ? Result.LOST : Result.RUNNING;
    }

    private Result play(Minecraft mc, LocalPlayer p) {
        int n = route.size();
        double px = p.getX(), py = p.getY(), pz = p.getZ();

        // If you got ahead (faster than when recording), jump to the nearest frame further along.
        int best = index;
        double bestDistance = route.distance(index, px, py, pz);
        for (int k = 1; k <= SKIP_WINDOW && index + k < n; k++) {
            double d = route.distance(index + k, px, py, pz);
            if (d < bestDistance) { best = index + k; bestDistance = d; }
        }
        index = best;
        boolean onPath = bestDistance <= ON_PATH;
        if (onPath) lagTicks = 0;
        else if (++lagTicks > MAX_LAG_TICKS) return Result.LOST;

        int recorded = route.keys(index);
        int target = Math.min(index + LOOKAHEAD, n - 1);
        double tx = route.x(target) - px, tz = route.z(target) - pz;
        float yaw = route.yaw(index);
        int move = Math.sqrt(tx * tx + tz * tz) > 0.15 ? RouteMath.moveKeys(tx, tz, yaw) : 0;
        int keys = move | (recorded & (Route.JUMP | Route.SNEAK));
        if ((recorded & Route.SPRINT) != 0 && (move & Route.FWD) != 0) keys |= Route.SPRINT;

        p.setYRot(yaw);
        p.setXRot(route.pitch(index));
        Inputs.apply(mc, keys);
        attack = (recorded & Route.ATTACK) != 0;

        if (index >= n - 1) return onPath ? Result.DONE : Result.RUNNING;
        if (onPath) index++;
        return Result.RUNNING;
    }

    /** Seconds played and total, for the panel. */
    public int playedSeconds() { return index / 20; }
}
