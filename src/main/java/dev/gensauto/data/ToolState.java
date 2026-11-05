package dev.gensauto.data;

import java.util.ArrayDeque;

/** What is known about one tool (pick or hoe) in this session. */
public final class ToolState {
    private static final long SAMPLE_EVERY = 5_000, WINDOW = 15 * 60_000, MIN_SPAN = 60_000;

    public final String key;
    public String displayName;
    public int level = -1, prestige = -1, rebirth = 0;
    public double percent;
    public String rateLabel, rate;
    /** When the server's action bar last reported this tool (it is fresher than the item tooltip). */
    public long lastBar = -1;

    private long sessionStart = -1;
    private double sessionStartProgress;
    private final ArrayDeque<double[]> samples = new ArrayDeque<>(); // {millis, progress}

    public ToolState(String key, String displayName) { this.key = key; this.displayName = displayName; }

    public boolean known() { return level >= 0; }

    /** Level plus the fraction towards the next one: level 31 at 74.22% is 31.7422. */
    public double progress() { return level + percent / 100.0; }

    public void update(int newLevel, double newPercent, long now) {
        // A prestige sends the tool back to level 0; any other drop is just an older reading.
        boolean prestiged = known() && newLevel < level && newLevel <= 2;
        if (known() && newLevel < level && !prestiged) return;
        level = newLevel; percent = newPercent;
        if (prestiged || sessionStart < 0) {
            // A prestige sends the level back to 0: start measuring again from here.
            samples.clear();
            if (sessionStart < 0) sessionStart = now;
            sessionStartProgress = progress();
        }
        if (samples.isEmpty() || now - samples.peekLast()[0] >= SAMPLE_EVERY) samples.addLast(new double[]{now, progress()});
        while (samples.size() > 2 && now - samples.peekFirst()[0] > WINDOW) samples.removeFirst();
    }

    /**
     * The first action bar replaces the tooltip reading, which is usually behind: measuring starts here,
     * so that difference does not count as levels gained.
     */
    public void rebase(long now) {
        samples.clear();
        samples.addLast(new double[]{now, progress()});
        sessionStartProgress = progress();
    }

    /** Levels per minute over the last minutes, or 0 while there is not enough data. */
    public double levelsPerMinute() {
        if (samples.size() < 2) return 0;
        double[] first = samples.peekFirst(), last = samples.peekLast();
        double span = last[0] - first[0];
        if (span < MIN_SPAN) return 0;
        return Math.max(0, (last[1] - first[1]) / (span / 60_000.0));
    }

    /** Seconds until the given level at the current pace, or -1 when unknown. */
    public long secondsTo(int targetLevel) {
        double rate = levelsPerMinute(), missing = targetLevel - progress();
        if (missing <= 0) return 0;
        return rate <= 0 ? -1 : Math.round(missing / rate * 60);
    }

    public long sessionSeconds(long now) { return sessionStart < 0 ? 0 : (now - sessionStart) / 1000; }
    public double sessionLevels() { return Math.max(0, progress() - sessionStartProgress); }
}
