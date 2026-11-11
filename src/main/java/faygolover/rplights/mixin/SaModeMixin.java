package faygolover.rplights.mixin;

import faygolover.rplights.light.Lights;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Survivor's Arsenal 1.0.0: у фонаря есть ближний свет (20 блоков, 0.62), но {@code isHighMode} всегда true. Теперь —
 * по нашему {@code RplLow} (Shift+ПКМ без батареек во второй руке). Его читают и луч SA, и FL Render.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.item.common.FlashlightItem", remap = false)
public abstract class SaModeMixin {
    @Inject(method = "isHighMode", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void rplights$mode(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!Lights.low(stack));
    }
}
