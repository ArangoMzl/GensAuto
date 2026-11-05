package dev.gensauto;

import dev.gensauto.route.Route;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Holds and reads the same movement keys you press (W A S D, jump, sneak, sprint). */
public final class Inputs {
    private Inputs() {}

    public static void apply(Minecraft mc, int keys) {
        var o = mc.options;
        set(o.keyUp, (keys & Route.FWD) != 0);
        set(o.keyDown, (keys & Route.BACK) != 0);
        set(o.keyLeft, (keys & Route.LEFT) != 0);
        set(o.keyRight, (keys & Route.RIGHT) != 0);
        set(o.keyJump, (keys & Route.JUMP) != 0);
        set(o.keyShift, (keys & Route.SNEAK) != 0);
        set(o.keySprint, (keys & Route.SPRINT) != 0);
    }

    public static void release(Minecraft mc) { apply(mc, 0); }

    /** The keys you are holding right now, for recording. */
    public static int read(Minecraft mc) {
        var o = mc.options;
        int keys = 0;
        if (o.keyUp.isDown()) keys |= Route.FWD;
        if (o.keyDown.isDown()) keys |= Route.BACK;
        if (o.keyLeft.isDown()) keys |= Route.LEFT;
        if (o.keyRight.isDown()) keys |= Route.RIGHT;
        if (o.keyJump.isDown()) keys |= Route.JUMP;
        if (o.keyShift.isDown()) keys |= Route.SNEAK;
        if (o.keySprint.isDown() || (mc.player != null && mc.player.isSprinting())) keys |= Route.SPRINT;
        if (mc.screen == null && o.keyAttack.isDown()) keys |= Route.ATTACK;
        return keys;
    }

    /**
     * Sprint and sneak may be in "toggle" mode, where pressing flips the state instead of holding it.
     * Pressing again when the result is wrong works in both modes.
     */
    private static void set(KeyMapping key, boolean down) {
        if (key.isDown() == down) return;
        key.setDown(down);
        if (key.isDown() != down) key.setDown(!down);
    }

    /** Turns the camera at most `speed` degrees per tick towards the target; true once it is there. */
    public static boolean turn(LocalPlayer p, float yaw, float pitch, float speed) {
        float dy = Mth.wrapDegrees(yaw - p.getYRot()), dp = pitch - p.getXRot();
        p.setYRot(p.getYRot() + Mth.clamp(dy, -speed, speed));
        p.setXRot(Mth.clamp(p.getXRot() + Mth.clamp(dp, -speed, speed), -90f, 90f));
        return Math.abs(dy) <= speed && Math.abs(dp) <= speed;
    }
}
