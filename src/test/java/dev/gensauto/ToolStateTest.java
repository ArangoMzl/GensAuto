package dev.gensauto;

import dev.gensauto.data.ToolState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolStateTest {
    @Test void olderReadingsDoNotLowerTheLevel() {
        var t = new ToolState("AZADA COSECHADORA", "AZADA COSECHADORA");
        t.update(73, 3.61, 0);
        t.update(71, 22.78, 5_000); // stale tooltip
        assertEquals(73, t.level);
    }

    @Test void prestigeRestartsTheCount() {
        var t = new ToolState("PICO DE MINERIA", "PICO DE MINERIA");
        t.update(150, 10, 0);
        t.update(0, 1, 5_000);
        assertEquals(0, t.level);
        assertEquals(0, t.sessionLevels(), 1e-9);
    }

    @Test void rebaseIgnoresTheTooltipGap() {
        var t = new ToolState("AZADA COSECHADORA", "AZADA COSECHADORA");
        t.update(71, 22.78, 0); // tooltip
        t.level = 73; t.percent = 3.61;
        t.rebase(1_000);         // first action bar
        assertEquals(0, t.sessionLevels(), 1e-9);
        t.update(74, 3.61, 121_000);
        assertEquals(1, t.sessionLevels(), 1e-9);
        assertEquals(0.5, t.levelsPerMinute(), 1e-9);
        // 125 - 74.0361 = 50.9639 levels at 0.5 per minute.
        assertEquals(Math.round(50.9639 / 0.5 * 60), t.secondsTo(125));
    }
}
