package dev.gensauto.qa;

import dev.gensauto.AutoController;
import dev.gensauto.GensAutoClient;
import dev.gensauto.Inputs;
import dev.gensauto.route.Playlist;
import dev.gensauto.route.Route;
import dev.gensauto.route.RouteStore;
import dev.gensauto.ui.RoutesScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * In-game check: action bar → panel; J refuses with an empty list; recording a square that saves itself on
 * returning to the start; routes survive reloading the file; looping the Farm list (also with Speed II);
 * stopping at a wall; the pick uses the (empty) Minería list instead.
 */
public final class QaClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("GensAuto QA");
    private static final int LEG = 20, REST = 6;
    private boolean requested, done;
    private int ticks, stage, lastPlayed;
    private double rx, ry, rz;
    private final long deadline = System.currentTimeMillis() + 300_000;
    private final StringBuilder notes = new StringBuilder();

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (done) return;
            try { tick(mc); }
            catch (Throwable error) {
                LOG.error("QA FAILED", error);
                result("FAIL: " + error + "\n" + notes); done = true; mc.stop();
            }
        });
    }

    private void tick(Minecraft mc) {
        if (System.currentTimeMillis() > deadline) throw new AssertionError("Client QA timed out at stage " + stage);
        if (!requested && mc.screen != null && mc.getOverlay() == null) {
            requested = true;
            mc.options.onboardAccessibility = false;
            mc.getWindow().setWindowed(1280, 720);
            mc.options.renderDistance().set(3);
            mc.options.guiScale().set(2);
            mc.options.pauseOnLostFocus = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.createWorldOpenFlows().createFreshLevel("GensAuto-QA-" + System.currentTimeMillis(),
                    new LevelSettings("GensAuto QA", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                            new GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(271828L, false, false), WorldPresets::createFlatWorldDimensions, new TitleScreen());
            return;
        }
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        var auto = GensAutoClient.auto();
        var tracker = GensAutoClient.tracker();
        var store = auto.routes();
        String server = GensAutoClient.server(mc);
        ticks++;
        switch (stage) {
            case 0 -> { // hoe with the server's tooltip + the server's action bar packet
                if (ticks == 40) {
                    mc.player.setYRot(0); mc.player.setXRot(40);
                    giveTool(mc, Items.DIAMOND_HOE, "AZADA COSECHADORA", 71, 22.78, 4, 1);
                }
                if (ticks == 50) onServer(mc, sp -> sp.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("AZADA COSECHADORA | ★ Nivel 73 (3.61%) | ❂ Esencias Farm. (+1.6B/m)"))));
                if (ticks == 60) {
                    var tool = tracker.shown();
                    require(tool != null && tool.level == 73 && Math.abs(tool.percent - 3.61) < 1e-6, "Action bar not read: " + (tool == null ? null : tool.level + " " + tool.percent));
                    require("+1.6B/m".equals(tool.rate) && tool.prestige == 4 && tool.rebirth == 1, "Rate/prestige wrong: " + tool.rate + " P" + tool.prestige);
                    require(tool.sessionLevels() < 0.01, "Tooltip→action bar gap counted as gained levels: " + tool.sessionLevels());
                    note("barra de acción → nivel 73 (3.61%), sesión +0");
                    auto.toggle(mc);
                    require(!auto.running(), "J must not start with an empty list (no basic mode)");
                    note("J con la lista vacía no arranca (sin modo básico)");
                    stage = 1; ticks = 0;
                }
            }
            case 1 -> { // record a square; it must save itself when coming back to the start
                if (ticks == 2) {
                    rx = mc.player.getX(); ry = mc.player.getY(); rz = mc.player.getZ();
                    auto.toggleRecording(mc);
                    require(auto.recorder().recording(), "Recording did not start");
                }
                int t = ticks - 3;
                if (t >= 0 && t < 4 * (LEG + REST)) {
                    int leg = t / (LEG + REST), within = t % (LEG + REST);
                    if (within == 0) mc.player.setYRot(new float[]{0, -90, 180, 90}[leg]);
                    if (auto.recorder().recording()) Inputs.apply(mc, within < LEG ? Route.FWD : 0);
                    else Inputs.release(mc);
                }
                if (ticks == 3 + 2 * (LEG + REST) + 10) {
                    require(auto.recordArmed(), "Recording should be armed after going away from the start");
                    screenshot(mc, "qa-2-recording.png");
                }
                if (ticks == 3 + 4 * (LEG + REST) + 4) {
                    Inputs.release(mc);
                    require(!auto.recorder().recording(), "Recording did not save itself on returning to the start");
                    var farm = store.active(server, Playlist.FARM);
                    var routes = store.routes(farm);
                    require(routes.size() == 1, "Route not saved in the Farm list: " + routes.size());
                    Route r = routes.get(0);
                    note(String.format(Locale.ROOT, "se guardó sola al volver: %d frames, a %.2f bloques del inicio, en «%s»", r.size(), r.endGap(), farm.name));
                    require(r.closed() && r.inPlaylist, "Route should be closed and enabled");
                    // Same file read again, as when the game starts.
                    var reopened = new RouteStore(mc.gameDirectory.toPath().resolve("config/gensauto-routes.json"));
                    require(reopened.routes(reopened.active(server, Playlist.FARM)).size() == 1, "Route lost after reloading the file");
                    note("la ruta sigue ahí al volver a leer el archivo (como al reabrir el juego)");
                    stage = 2; ticks = 0;
                }
            }
            case 2 -> { // start 0.8 blocks off the start point and loop the list
                if (ticks == 5) mc.player.setPos(rx + 0.8, ry, rz);
                if (ticks == 10) {
                    auto.toggle(mc);
                    require(auto.running(), "List did not start");
                    lastPlayed = 0;
                }
                if (ticks > 10) {
                    require(auto.running(), "List stopped unexpectedly (loop " + auto.routesPlayed() + ")");
                    trackLoops(mc, auto);
                    if (ticks == 60) screenshot(mc, "qa-3-replaying.png");
                    if (auto.routesPlayed() >= 2) {
                        onServer(mc, sp -> sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 120, 1)));
                        note("Speed II activado (no estaba al grabar)");
                        stage = 3; ticks = 0;
                    }
                    if (ticks > 700) throw new AssertionError("Two loops took too long");
                }
            }
            case 3 -> { // keeps following the path even when faster than the recording
                require(auto.running(), "List stopped with Speed II (loop " + auto.routesPlayed() + ")");
                trackLoops(mc, auto);
                if (auto.routesPlayed() >= 4) {
                    auto.toggle(mc);
                    require(!auto.running(), "Toggle did not stop");
                    onServer(mc, sp -> {
                        var level = sp.level();
                        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 1; dy++)
                            level.setBlockAndUpdate(BlockPos.containing(rx + dx, ry + dy, rz + 2.5), Blocks.STONE.defaultBlockState());
                    });
                    stage = 4; ticks = 0;
                }
                if (ticks > 700) throw new AssertionError("Loops with Speed II took too long");
            }
            case 4 -> { // the wall makes it leave the path: it must stop by itself
                if (ticks == 10) { auto.toggle(mc); require(auto.running(), "List did not restart"); }
                if (ticks > 10 && !auto.running()) {
                    note("con un muro en el camino se detuvo solo tras " + (ticks - 10) + " ticks");
                    stage = 5; ticks = 0;
                }
                if (ticks > 400) throw new AssertionError("Did not stop at the wall");
            }
            case 5 -> { // the pick uses the Minería list, which is empty
                if (ticks == 2) giveTool(mc, Items.DIAMOND_PICKAXE, "PICO DE MINERÍA", 31, 74.22, 5, 2);
                if (ticks == 12) {
                    auto.toggle(mc);
                    require(!auto.running(), "The pick must not play Farm routes");
                    require(store.routes(store.active(server, Playlist.MINE)).isEmpty(), "Minería list should be empty");
                    note("con el pico usa la lista Minería (vacía) y no la de Farm");
                    giveTool(mc, Items.DIAMOND_HOE, "AZADA COSECHADORA", 73, 3.61, 4, 1);
                    store.rename(store.create(server, Playlist.FARM), "Farm norte");
                }
                // Far from the start, J marks it with particles; look at it for the screenshot.
                if (ticks == 20) mc.player.setPos(rx + 8, ry, rz - 6);
                if (ticks == 26) {
                    auto.toggle(mc);
                    require(!auto.running(), "Must not start far from the routes");
                    double dx = rx - mc.player.getX(), dz = rz - mc.player.getZ();
                    mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
                    mc.player.setXRot(15);
                }
                if (ticks == 50) {
                    screenshot(mc, "qa-4-marker.png");
                    note("lejos del inicio: J no arranca y marca el inicio con partículas (ver qa-4-marker.png)");
                }
                if (ticks == 56) mc.setScreen(new RoutesScreen(auto));
                if (ticks == 66) screenshot(mc, "qa-5-routes-menu.png");
                if (ticks == 70) {
                    mc.setScreen(null);
                    result("PASS\n" + notes);
                    done = true; mc.stop();
                }
            }
            default -> {}
        }
    }

    private void trackLoops(Minecraft mc, AutoController auto) {
        if (auto.routesPlayed() != lastPlayed) {
            lastPlayed = auto.routesPlayed();
            double dx = mc.player.getX() - rx, dz = mc.player.getZ() - rz;
            note(String.format(Locale.ROOT, "vuelta %d terminada a %.2f bloques del inicio", lastPlayed, Math.sqrt(dx * dx + dz * dz)));
        }
    }

    private static void giveTool(Minecraft mc, Item item, String name, int level, double percent, int prestige, int rebirth) {
        onServer(mc, sp -> {
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Herramienta Especial"),
                    Component.literal("| ★ Dueño: Tester"), Component.literal(String.format(Locale.ROOT, "| ⊠ Nivel: %d (||||||) (%.2f%%)", level, percent)),
                    Component.literal("| ♨ Prestigio: " + prestige + " ✦"), Component.literal("| ⬢ Renacimiento: " + rebirth + " ✦"))));
            sp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
    }

    private void note(String text) { LOG.info(text); notes.append("- ").append(text).append('\n'); }

    private static void onServer(Minecraft mc, Consumer<net.minecraft.server.level.ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void screenshot(Minecraft mc, String filename) {
        Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), 1, c -> {});
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void result(String text) {
        try { Files.writeString(Path.of("gensauto-qa-result.txt"), text); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
