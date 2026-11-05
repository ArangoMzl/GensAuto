package dev.gensauto;

import dev.gensauto.data.GensConfig;
import dev.gensauto.data.ToolState;
import dev.gensauto.data.ToolText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Collects tool data from what the server already shows: held item, action bar and prestige menu. */
public final class GensTracker {
    private final GensConfig config;
    private final Map<String, ToolState> tools = new LinkedHashMap<>();
    private String heldKey, lastKey;
    private int tick, loggedUnread;

    public GensTracker(GensConfig config) { this.config = config; }

    public GensConfig config() { return config; }

    /** The tool in hand, or the last one used when holding something else. */
    public ToolState shown() {
        String key = heldKey != null ? heldKey : lastKey;
        return key == null ? null : tools.get(key);
    }

    /** Key of the Gens tool currently in the main hand, or null. */
    public String heldKey() { return heldKey; }

    public GensConfig.Requirement requirement(ToolState tool) { return config.requirements.get(tool.key); }

    public void tick(Minecraft mc) {
        if (mc.player == null) { heldKey = null; return; }
        if (++tick % 5 != 0) return;
        readHeld(mc.player.getMainHandItem());
        if (mc.screen instanceof AbstractContainerScreen<?> screen) readPrestigeMenu(screen);
    }

    private void readHeld(ItemStack stack) {
        heldKey = null;
        if (stack.isEmpty()) return;
        var lore = ToolText.parseLore(loreLines(stack));
        if (lore == null) return;
        String name = stack.getHoverName().getString().strip();
        ToolState tool = tool(name);
        heldKey = lastKey = tool.key;
        tool.prestige = lore.prestige();
        tool.rebirth = lore.rebirth();
        // The action bar is fresher; the tooltip only fills in when the bar has not arrived for a while.
        long now = System.currentTimeMillis();
        if (!tool.known() || now - tool.lastBar > 10_000) tool.update(lore.level(), lore.percent(), now);
    }

    /** Every action bar the server sends (called from the Gui mixin). */
    public void onActionBar(Component message) {
        String text = message.getString();
        var bar = ToolText.parseActionBar(text);
        if (bar == null) {
            if (text.contains("Nivel") && loggedUnread++ < 3) GensConfig.LOG.info("Barra de acción no reconocida: {}", text);
            return;
        }
        ToolState tool = tool(bar.tool());
        long now = System.currentTimeMillis();
        boolean firstBar = tool.lastBar < 0;
        tool.lastBar = now;
        if (firstBar && tool.known()) { tool.level = bar.level(); tool.percent = bar.percent(); tool.rebase(now); }
        else tool.update(bar.level(), bar.percent(), now);
        if (bar.rate() != null) { tool.rateLabel = bar.rateLabel(); tool.rate = bar.rate(); }
        lastKey = tool.key;
    }

    private void readPrestigeMenu(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            var req = ToolText.parsePrestigeItem(stack.getHoverName().getString(), loreLines(stack));
            if (req == null) continue;
            String key = ToolText.key(req.tool());
            var old = config.requirements.get(key);
            if (old == null || old.level != req.level() || old.prestige != req.prestige()) {
                config.requirements.put(key, new GensConfig.Requirement(req.prestige(), req.level()));
                config.save();
            }
        }
    }

    private ToolState tool(String name) {
        String key = ToolText.key(name);
        ToolState tool = tools.computeIfAbsent(key, k -> new ToolState(k, name.toUpperCase(java.util.Locale.ROOT)));
        return tool;
    }

    private static List<String> loreLines(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }

    public void reset() { tools.clear(); heldKey = lastKey = null; }
}
