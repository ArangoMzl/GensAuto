package dev.gensauto;

import dev.gensauto.data.ToolState;
import dev.gensauto.data.ToolText;
import dev.gensauto.route.RouteMath;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Panel with the held tool: level vs. prestige requirement, time left, farm rate, session and automation. */
public final class GensHud {
    private static final int PAD = 5, LINE = 11, EDGE = 2, BAR_H = 4;
    private static final int WHITE = 0xFFF0F0F0, GRAY = 0xFFA0A0A0, GREEN = 0xFF6BE38D, YELLOW = 0xFFFFD45A,
            RED = 0xFFFF6B6B, AQUA = 0xFF6FD8E8, BG = 0xB0101418;

    private record Line(String left, int leftColor, String right, int rightColor) {
        Line(String text, int color) { this(text, color, null, 0); }
    }

    private GensHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        var tracker = GensAutoClient.tracker();
        if (tracker == null || mc.player == null || mc.options.hideGui || !tracker.config().hudVisible) return;
        ToolState tool = tracker.shown();
        var auto = GensAutoClient.auto();
        if (tool == null || !tool.known()) return;
        var recorder = auto.recorder();

        long now = System.currentTimeMillis();
        boolean pick = tool.key.contains("PICO");
        int accent = pick ? 0xFFFF7A7A : 0xFF7CF08F;
        var req = tracker.requirement(tool);
        boolean reqOutdated = req != null && tool.prestige >= 0 && req.prestige != tool.prestige;

        List<Line> lines = new ArrayList<>();
        String badge = tool.prestige < 0 ? "" : "P" + tool.prestige + " · R" + tool.rebirth;
        lines.add(new Line(tool.displayName, accent, badge, YELLOW));
        String goal = req == null ? "?" : req.level + (reqOutdated ? "?" : "");
        lines.add(new Line("Nivel " + tool.level + " / " + goal, WHITE,
                String.format(Locale.ROOT, "%.2f%%", tool.percent), GRAY));
        int barIndex = lines.size();
        lines.add(null); // progress bar towards the prestige level

        if (req != null && tool.level >= req.level && !reqOutdated) {
            lines.add(new Line("¡Listo para prestigiar!", now / 500 % 2 == 0 ? GREEN : WHITE));
        } else if (req != null) {
            int missing = Math.max(0, req.level - tool.level);
            long eta = tool.secondsTo(req.level);
            lines.add(new Line("Faltan " + missing + " niveles", WHITE,
                    eta < 0 ? "calculando…" : "~" + ToolText.duration(eta), eta < 0 ? GRAY : AQUA));
        }
        if (reqOutdated || req == null) lines.add(new Line("Abre el menú de Prestigio para leer el requisito", GRAY));
        if (tool.rate != null) lines.add(new Line(tool.rateLabel == null ? "Farm" : tool.rateLabel, GRAY, tool.rate, GREEN));
        double perMin = tool.levelsPerMinute();
        lines.add(new Line("Sesión " + ToolText.duration(tool.sessionSeconds(now)), GRAY,
                String.format(Locale.ROOT, "+%.1f niv%s", tool.sessionLevels(),
                        perMin > 0 ? String.format(Locale.ROOT, " · %.2f/min", perMin) : ""), GRAY));
        if (recorder.recording()) {
            var start = recorder.route();
            double d = recorder.distanceToStart(mc.player);
            lines.add(new Line("● GRABANDO " + RouteMath.clock(recorder.seconds()), now / 500 % 2 == 0 ? RED : WHITE,
                    GensAutoClient.keyName(GensAutoClient.recordKey) + " guardar", GRAY));
            String where = RouteMath.direction(start.startX - mc.player.getX(), start.startZ - mc.player.getZ(), mc.player.getYRot());
            lines.add(new Line(String.format(Locale.ROOT, "Inicio a %.1f bl, %s", d, where), auto.recordArmed() ? YELLOW : GRAY));
            if (auto.recordArmed()) lines.add(new Line("Vuelve al inicio para guardar", YELLOW));
        }
        if (auto.running()) {
            lines.add(new Line("● AUTO · " + auto.status(), auto.paused() ? YELLOW : GREEN,
                    GensAutoClient.keyName(GensAutoClient.autoKey), GRAY));
        } else if (!recorder.recording() && auto.idleInfo() != null) {
            lines.add(new Line(auto.idleInfo(), auto.idleReady() ? GREEN : GRAY,
                    auto.idleReady() ? GensAutoClient.keyName(GensAutoClient.autoKey) + " iniciar" : "", GRAY));
        }

        Font font = mc.font;
        int w = 150;
        for (Line l : lines) if (l != null) w = Math.max(w, font.width(l.left) + (l.right == null ? 0 : font.width(l.right) + 10));
        w += PAD * 2;
        int h = PAD * 2 + (lines.size() - 1) * LINE + BAR_H + 2;
        float scale = tracker.config().hudScale;
        int sw = Math.round(w * scale), sh = Math.round(h * scale);
        int x = EDGE + Math.round(tracker.config().hudX * Math.max(0, g.guiWidth() - sw - EDGE * 2));
        int y = EDGE + Math.round(tracker.config().hudY * Math.max(0, g.guiHeight() - sh - EDGE * 2));

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.fill(0, 0, w, h, BG);
        g.fill(0, 0, w, 1, accent);
        int cy = PAD;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (i == barIndex) {
                double fraction = req == null || req.level <= 0 ? 0 : Math.min(1, tool.progress() / req.level);
                g.fill(PAD, cy, w - PAD, cy + BAR_H, 0x50FFFFFF);
                g.fill(PAD, cy, PAD + (int) Math.round((w - PAD * 2) * fraction), cy + BAR_H, fraction >= 1 ? GREEN : accent);
                cy += BAR_H + 2;
                continue;
            }
            g.drawString(font, l.left, PAD, cy, l.leftColor, true);
            if (l.right != null) g.drawString(font, l.right, w - PAD - font.width(l.right), cy, l.rightColor, true);
            cy += LINE;
        }
        pose.popMatrix();
    }
}
