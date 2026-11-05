package dev.gensauto.qa.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The automated development client does not require the user's foreground focus. */
@Mixin(Minecraft.class)
public class QaFocusMixin {
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void gensauto$focus(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(true); }
}
