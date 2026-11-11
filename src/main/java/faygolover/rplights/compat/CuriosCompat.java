package faygolover.rplights.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Фонари в слотах Curios: голова (налобный), тело (нагрудный), пояс (лампа). */
public final class CuriosCompat {
    private CuriosCompat() {}

    public static final String HEAD = "head", BODY = "body", BELT = "belt";

    /** Первый непустой предмет в слоте {@code id} или пусто. */
    public static ItemStack worn(LivingEntity e, String id) {
        ICuriosItemHandler h = CuriosApi.getCuriosInventory(e).resolve().orElse(null);
        if (h == null) return ItemStack.EMPTY;
        ICurioStacksHandler sh = h.getCurios().get(id);
        if (sh == null) return ItemStack.EMPTY;
        for (int i = 0; i < sh.getSlots(); i++) {
            ItemStack s = sh.getStacks().getStackInSlot(i);
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Сказать Curios, что предмет в слоте изменился (NBT), — он разошлёт его игрокам рядом. */
    public static void touched(LivingEntity e, String id) {
        ICuriosItemHandler h = CuriosApi.getCuriosInventory(e).resolve().orElse(null);
        if (h == null) return;
        ICurioStacksHandler sh = h.getCurios().get(id);
        if (sh == null) return;
        for (int i = 0; i < sh.getSlots(); i++) {
            ItemStack s = sh.getStacks().getStackInSlot(i);
            if (!s.isEmpty()) sh.getStacks().setStackInSlot(i, s);
        }
    }
}
