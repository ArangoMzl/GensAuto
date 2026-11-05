package dev.gensauto.ui;

import dev.gensauto.AutoController;
import dev.gensauto.GensAutoClient;
import dev.gensauto.route.Playlist;
import dev.gensauto.route.Route;
import dev.gensauto.route.RouteMath;
import dev.gensauto.route.RouteStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Y: routes of this server, in two tabs (Farm / Minería). Each tab has one or more lists; the one "en uso"
 * is what J plays with that tool and where new recordings go.
 */
public final class RoutesScreen extends Screen {
    private static final int W = 360, ROW = 24, LIST_TOP = 112;
    private static final int WHITE = 0xFFF0F0F0, GRAY = 0xFFA0A0A0, GREEN = 0xFF6BE38D, YELLOW = 0xFFFFD45A, RED = 0xFFFF6B6B;

    private final AutoController auto;
    private final RouteStore store;
    private String server, dimension, kind;
    private Playlist selected;
    private List<Route> routes = List.of();
    private int scroll;
    private boolean renaming, confirmDeleteList;
    private String confirmDelete;
    private EditBox nameBox;

    public RoutesScreen(AutoController auto) {
        super(Component.literal("Rutas"));
        this.auto = auto;
        this.store = auto.routes();
    }

    private int left() { return (width - W) / 2; }
    private int visibleRows() { return Math.max(1, (height - LIST_TOP - 48) / ROW); }

    @Override protected void init() {
        if (server == null) {
            server = GensAutoClient.server(minecraft);
            dimension = GensAutoClient.dimension(minecraft);
            String held = minecraft.player == null ? null : AutoController.heldKey(minecraft.player);
            kind = held == null ? Playlist.FARM : Playlist.kindOf(held);
        }
        var lists = store.playlists(server, kind);
        if (selected == null || lists.stream().noneMatch(p -> p.id.equals(selected.id))) selected = store.active(server, kind);
        lists = store.playlists(server, kind);
        routes = store.routes(selected);
        scroll = Math.max(0, Math.min(scroll, routes.size() - visibleRows()));
        int left = left(), right = left + W;

        // Tabs
        for (String k : new String[]{Playlist.FARM, Playlist.MINE}) {
            int count = store.playlists(server, k).stream().mapToInt(p -> store.routes(p).size()).sum();
            Button tab = Button.builder(Component.literal(Playlist.label(k) + " (" + count + ")"), b -> {
                kind = k; selected = null; scroll = 0; renaming = false; confirmDeleteList = false; confirmDelete = null;
                rebuildWidgets();
            }).bounds(width / 2 - 112 + (k.equals(Playlist.FARM) ? 0 : 114), 22, 110, 20).build();
            tab.active = !k.equals(kind);
            addRenderableWidget(tab);
        }

        // Which list: ◀ name ▶ and "use this one"
        final List<Playlist> all = lists;
        Button prev = Button.builder(Component.literal("◀"), b -> cycle(all, -1)).bounds(left, 50, 20, 20).build();
        Button next = Button.builder(Component.literal("▶"), b -> cycle(all, 1)).bounds(left + 222, 50, 20, 20).build();
        prev.active = next.active = all.size() > 1 && !renaming;
        addRenderableWidget(prev);
        addRenderableWidget(next);
        boolean inUse = store.isActive(selected);
        Button use = Button.builder(Component.literal(inUse ? "En uso ✓" : "Usar esta"), b -> { store.setActive(selected); rebuildWidgets(); })
                .bounds(right - 110, 50, 110, 20).build();
        use.active = !inUse;
        use.setTooltip(Tooltip.create(Component.literal("La lista en uso es la que " + GensAutoClient.keyName(GensAutoClient.autoKey)
                + " reproduce con " + (Playlist.FARM.equals(kind) ? "la azada" : "el pico") + " y donde se guardan las rutas nuevas")));
        addRenderableWidget(use);

        // New / rename / delete list
        if (renaming) {
            nameBox = new EditBox(font, left, 76, 236, 20, Component.literal("Nombre"));
            nameBox.setMaxLength(32);
            nameBox.setValue(selected.name);
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
            addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> confirmRename()).bounds(right - 110, 76, 54, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> { renaming = false; rebuildWidgets(); }).bounds(right - 54, 76, 54, 20).build());
        } else {
            nameBox = null;
            addRenderableWidget(Button.builder(Component.literal("+ Nueva lista"), b -> {
                selected = store.create(server, kind); scroll = 0; renaming = true; rebuildWidgets();
            }).bounds(left, 76, 96, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Renombrar"), b -> { renaming = true; rebuildWidgets(); })
                    .bounds(left + 100, 76, 80, 20).build());
            Button delete = Button.builder(Component.literal(confirmDeleteList ? "¿Borrar todo?" : "Borrar lista"), b -> {
                if (confirmDeleteList) { store.delete(selected); selected = null; confirmDeleteList = false; }
                else confirmDeleteList = true;
                rebuildWidgets();
            }).bounds(right - 110, 76, 110, 20).build();
            if (!routes.isEmpty()) delete.setTooltip(Tooltip.create(Component.literal("Borra la lista y sus " + routes.size() + " rutas")));
            addRenderableWidget(delete);
        }

        // Routes of the list
        int at = 0;
        for (int k = 0; k < all.size(); k++) if (all.get(k).id.equals(selected.id)) at = k;
        Playlist moveTarget = all.size() > 1 ? all.get((at + 1) % all.size()) : null;
        for (int i = 0; i < visibleRows() && scroll + i < routes.size(); i++) {
            Route r = routes.get(scroll + i);
            int y = LIST_TOP + i * ROW;
            Button on = Button.builder(Component.literal(r.inPlaylist && r.closed() ? "Sí" : "No"), b -> {
                r.inPlaylist = !r.inPlaylist; store.save(); rebuildWidgets();
            }).bounds(right - 160, y, 34, 20).build();
            on.active = r.closed();
            on.setTooltip(Tooltip.create(Component.literal(r.closed() ? "Usar esta ruta al reproducir la lista" : "No vuelve a su inicio: solo se puede probar sola")));
            addRenderableWidget(on);
            if (moveTarget != null) {
                Playlist to = moveTarget;
                Button move = Button.builder(Component.literal("Mover"), b -> { store.move(r, to); rebuildWidgets(); })
                        .bounds(right - 122, y, 50, 20).build();
                move.setTooltip(Tooltip.create(Component.literal("Mover a «" + to.name + "»")));
                addRenderableWidget(move);
            }
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { onClose(); auto.playOnce(minecraft, r); })
                    .bounds(right - 68, y, 22, 20).tooltip(Tooltip.create(Component.literal("Probar una vez"))).build());
            boolean confirming = r.id.equals(confirmDelete);
            addRenderableWidget(Button.builder(Component.literal(confirming ? "¿Sí?" : "✕"), b -> {
                if (r.id.equals(confirmDelete)) { store.remove(r); confirmDelete = null; }
                else confirmDelete = r.id;
                rebuildWidgets();
            }).bounds(right - (confirming ? 42 : 22), y, confirming ? 42 : 22, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose()).bounds(width / 2 - 40, height - 26, 80, 20).build());
    }

    private void cycle(List<Playlist> lists, int step) {
        int i = 0;
        for (int k = 0; k < lists.size(); k++) if (lists.get(k).id.equals(selected.id)) i = k;
        selected = lists.get(Math.floorMod(i + step, lists.size()));
        scroll = 0; confirmDelete = null; confirmDeleteList = false;
        rebuildWidgets();
    }

    private void confirmRename() {
        if (nameBox != null) store.rename(selected, nameBox.getValue());
        renaming = false;
        rebuildWidgets();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (renaming && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) { confirmRename(); return true; }
        return super.keyPressed(event);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        int left = left();
        g.drawCenteredString(font, "Rutas · " + server, width / 2, 8, WHITE);
        if (!renaming) {
            boolean inUse = store.isActive(selected);
            String title = "«" + selected.name + "» · " + routes.size() + (routes.size() == 1 ? " ruta" : " rutas");
            g.drawCenteredString(font, title, left + 121, 56, inUse ? GREEN : WHITE);
        }
        if (routes.isEmpty()) {
            g.drawCenteredString(font, "Esta lista no tiene rutas.", width / 2, LIST_TOP + 4, YELLOW);
            g.drawCenteredString(font, "Toma " + (Playlist.FARM.equals(kind) ? "la azada" : "el pico") + ", párate donde quieras empezar y presiona "
                    + GensAutoClient.keyName(GensAutoClient.recordKey) + ".", width / 2, LIST_TOP + 16, GRAY);
            if (!store.isActive(selected)) g.drawCenteredString(font, "Las rutas nuevas se guardan en la lista en uso: pulsa «Usar esta».", width / 2, LIST_TOP + 28, GRAY);
        } else {
            g.drawString(font, "Ruta", left, LIST_TOP - 12, GRAY, false);
            g.drawString(font, "Inicio", left + 96, LIST_TOP - 12, GRAY, false);
            g.drawString(font, "Usar", W + left - 158, LIST_TOP - 12, GRAY, false);
        }
        var p = minecraft.player;
        for (int i = 0; i < visibleRows() && scroll + i < routes.size(); i++) {
            Route r = routes.get(scroll + i);
            int y = LIST_TOP + i * ROW + 6;
            int color = !r.closed() ? GRAY : r.inPlaylist ? WHITE : GRAY;
            g.drawString(font, font.plainSubstrByWidth(r.name, 60) + " " + RouteMath.clock(r.seconds()), left, y, color, true);
            String where;
            int whereColor;
            if (!r.closed()) { where = String.format(Locale.ROOT, "no vuelve (%.1f bl)", r.endGap()); whereColor = RED; }
            else if (p == null || !java.util.Objects.equals(r.dimension, dimension)) { where = "otro mundo"; whereColor = GRAY; }
            else {
                double d = r.distanceToStart(p.getX(), p.getY(), p.getZ());
                where = d <= AutoController.START_RADIUS ? "aquí" : String.format(Locale.ROOT, "a %.0f bl", d);
                whereColor = d <= AutoController.START_RADIUS ? GREEN : GRAY;
            }
            g.drawString(font, where, left + 96, y, whereColor, true);
        }
        String hint = GensAutoClient.keyName(GensAutoClient.recordKey) + " grabar · " + GensAutoClient.keyName(GensAutoClient.autoKey)
                + " reproducir la lista en uso desde su inicio";
        if (routes.size() > visibleRows())
            hint = (scroll + 1) + "–" + Math.min(routes.size(), scroll + visibleRows()) + " de " + routes.size() + " · rueda para ver más · " + hint;
        g.drawCenteredString(font, hint, width / 2, height - 40, GRAY);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int max = Math.max(0, routes.size() - visibleRows());
        int next = Math.max(0, Math.min(max, scroll + (vertical < 0 ? 1 : -1)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
