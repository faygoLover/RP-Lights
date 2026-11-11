package faygolover.rplights.mixin.client;

import faygolover.rplights.light.Lights;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Заряд фонаря на глаз не виден: полоску у фонаря Survivor's Arsenal в слоте не рисуем. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsBarMixin {
    @Redirect(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isBarVisible()Z"))
    private boolean rplights$hideCharge(ItemStack stack) {
        return !Lights.isSa(stack) && stack.isBarVisible();
    }
}
