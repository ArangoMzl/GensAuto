package dev.gensauto;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gensauto.data.GensConfig;
import dev.gensauto.route.RouteStore;
import dev.gensauto.ui.RoutesScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class GensAutoClient implements ClientModInitializer {
    private static GensTracker tracker;
    private static AutoController auto;
    public static KeyMapping autoKey, recordKey, routesKey, hudKey;

    public static GensTracker tracker() { return tracker; }
    public static AutoController auto() { return auto; }

    @Override public void onInitializeClient() {
        var configDir = FabricLoader.getInstance().getConfigDir();
        GensConfig config = GensConfig.load(configDir.resolve("gensauto.json"));
        tracker = new GensTracker(config);
        auto = new AutoController(config, new RouteStore(configDir.resolve("gensauto-routes.json")));

        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("gensauto", "controls"));
        autoKey = register("auto", GLFW.GLFW_KEY_J, category);
        recordKey = register("record", GLFW.GLFW_KEY_U, category);
        routesKey = register("routes", GLFW.GLFW_KEY_Y, category);
        hudKey = register("hud", GLFW.GLFW_KEY_UNKNOWN, category);

        // Inputs are set at the start of the tick, before the game reads the keys and moves the player.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> auto.tick(mc));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (autoKey.consumeClick()) auto.toggle(mc);
            while (recordKey.consumeClick()) auto.toggleRecording(mc);
            while (routesKey.consumeClick()) if (mc.screen == null && mc.player != null) mc.setScreen(new RoutesScreen(auto));
            while (hudKey.consumeClick()) { config.hudVisible = !config.hudVisible; config.save(); }
            auto.endTick(mc);
            tracker.tick(mc);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            auto.stop(mc, null, null);
            auto.recorder().cancel();
            tracker.reset();
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("gensauto", "tool"), GensHud::render);
    }

    private static KeyMapping register(String action, int key, KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.gensauto." + action, InputConstants.Type.KEYSYM, key, category));
    }

    public static String keyName(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }

    /** The server address, or "local" in singleplayer: routes only show up where they were recorded. */
    public static String server(Minecraft mc) {
        var data = mc.getCurrentServer();
        return data == null ? "local" : data.ip.toLowerCase(Locale.ROOT);
    }

    public static String dimension(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }
}
