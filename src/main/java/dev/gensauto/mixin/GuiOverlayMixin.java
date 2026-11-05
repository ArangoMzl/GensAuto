package dev.gensauto.mixin;

import dev.gensauto.GensAutoClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server's action bar ("AZADA COSECHADORA | Nivel 73 (3.61%) | ...") goes straight to the HUD here. */
@Mixin(Gui.class)
public class GuiOverlayMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void gensauto$readActionBar(Component message, boolean animateColor, CallbackInfo ci) {
        var tracker = GensAutoClient.tracker();
        if (tracker != null && message != null) tracker.onActionBar(message);
    }
}
