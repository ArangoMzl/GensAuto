package dev.gensauto.mixin;

import dev.gensauto.AutoController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Once per tick the game calls continueAttack(true) while you hold the left click. With the automation
 * running it receives true as well, so blocks break exactly as with a held mouse button — even when the
 * Minecraft window does not have focus.
 */
@Mixin(Minecraft.class)
public class MinecraftAttackMixin {
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean gensauto$holdAttack(boolean leftClick) {
        return leftClick || AutoController.wantsAttack();
    }
}
