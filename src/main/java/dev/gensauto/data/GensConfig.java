package dev.gensauto.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything GensAuto remembers, in config/gensauto.json. */
public final class GensConfig {
    public static final Logger LOG = LoggerFactory.getLogger("gensauto");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Level needed to prestige, per tool, as read from the server's prestige menu. */
    public static final class Requirement {
        public int prestige, level;
        public Requirement() {}
        public Requirement(int prestige, int level) { this.prestige = prestige; this.level = level; }
    }

    public boolean hudVisible = true;
    /** Panel position as a fraction of the free space. */
    public float hudX = 0f, hudY = 0.35f, hudScale = 1f;
    public Map<String, Requirement> requirements = new LinkedHashMap<>(Map.of(
            "PICO DE MINERIA", new Requirement(5, 150),
            "AZADA COSECHADORA", new Requirement(4, 125)));

    // Automation
    /** Degrees the camera turns per tick (20 ticks = 1 second) when lining up with a route's first look. */
    public float turnSpeed = 9f;
    public boolean stopOnDamage = true;

    private transient Path file;

    public static GensConfig load(Path file) {
        GensConfig config = null;
        try {
            if (Files.exists(file)) config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GensConfig.class);
        } catch (Exception ex) {
            LOG.error("No se pudo leer {}; se usan valores por defecto", file, ex);
        }
        if (config == null) config = new GensConfig();
        config.file = file;
        return config.sanitize();
    }

    private GensConfig sanitize() {
        if (requirements == null) requirements = new LinkedHashMap<>();
        requirements.values().removeIf(r -> r == null || r.level <= 0);
        hudX = clamp(hudX, 0, 1); hudY = clamp(hudY, 0, 1); hudScale = clamp(hudScale, 0.5f, 2.5f);
        turnSpeed = clamp(turnSpeed, 2, 30);
        return this;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LOG.error("No se pudo guardar {}", file, ex);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : min;
    }
}
