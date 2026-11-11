package faygolover.rplights.recipe;

import faygolover.rplights.light.Cells;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import faygolover.rplights.registry.LightItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Обслуживание фонаря в верстаке:
 * <ul>
 *   <li>фонарь + полный комплект батареек (обычных или аккумуляторов) — поменять батарейки;</li>
 *   <li>фонарь один — вынуть батарейки;</li>
 *   <li>ручной фонарь + лампочка — вкрутить новую;</li>
 *   <li>лампа + ведро керосина — заправить (ведро остаётся).</li>
 * </ul>
 * Вынутое (батарейки со своим зарядом, старая лампочка) игрок получает при взятии результата
 * ({@link #returned}, событие крафта в {@code LightTicker}). Фонарь SA тоже обслуживается здесь.
 */
public class LightRecipe extends CustomRecipe {
    public LightRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    public enum Kind { SWAP, EJECT, BULB, FUEL }

    /** Что лежит в сетке. */
    public record Parts(ItemStack light, LightType type, List<ItemStack> batteries, int bulbs, int fuel, Kind kind) {}

    public static Parts parse(Container grid) {
        ItemStack light = ItemStack.EMPTY;
        LightType type = null;
        List<ItemStack> batteries = new ArrayList<>();
        int bulbs = 0, fuel = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            LightType t = Lights.type(s);
            if (t != null) {
                if (type != null) return null;
                light = s;
                type = t;
            } else if (Cells.isBattery(s)) {
                batteries.add(s);
            } else if (s.is(LightItems.BULB.get())) {
                bulbs++;
            } else if (s.is(LightActions.KEROSENE)) {
                fuel++;
            } else {
                return null;
            }
        }
        if (type == null) return null;
        Kind kind = null;
        if (type.usesCells() && bulbs == 0 && fuel == 0) {
            if (batteries.size() == type.cells) kind = Kind.SWAP;
            else if (batteries.isEmpty() && !Cells.read(light).isEmpty()) kind = Kind.EJECT;
        } else if (type == LightType.HAND_LANTERN && bulbs == 1 && batteries.isEmpty() && fuel == 0) {
            kind = Kind.BULB;
        }
        if (type == LightType.LAMP && fuel == 1 && bulbs == 0 && batteries.isEmpty()) kind = Kind.FUEL;
        return kind == null ? null : new Parts(light, type, batteries, bulbs, fuel, kind);
    }

    @Override
    public boolean matches(CraftingContainer grid, Level level) {
        return parse(grid) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer grid, RegistryAccess access) {
        Parts p = parse(grid);
        if (p == null) return ItemStack.EMPTY;
        ItemStack out = p.light().copyWithCount(1);
        switch (p.kind()) {
            case SWAP -> {
                List<Cells.Cell> fresh = new ArrayList<>();
                for (ItemStack b : p.batteries()) fresh.add(Cells.fromItem(b));
                Cells.write(out, fresh);
            }
            case EJECT -> Cells.write(out, List.of());
            case BULB -> out.getOrCreateTag().putByte(Lights.BULB, (byte) 1);
            case FUEL -> out.getOrCreateTag().putFloat(Lights.FUEL, Lights.FUEL_MAX);
        }
        out.getOrCreateTag().putBoolean(Lights.INIT, true);
        Lights.setOn(out, false);
        Lights.refresh(out);
        return out;
    }

    /** Что вернуть игроку из старого фонаря, когда он забрал результат. */
    public static List<ItemStack> returned(Parts p) {
        List<ItemStack> out = new ArrayList<>();
        switch (p.kind()) {
            case SWAP, EJECT -> {
                for (Cells.Cell c : Cells.read(p.light())) out.add(Cells.toItem(c));
            }
            case BULB -> out.add(new ItemStack(Lights.bulbOk(p.light()) ? LightItems.BULB.get() : LightItems.BURNT_BULB.get()));
            default -> {
            }
        }
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer grid) {
        NonNullList<ItemStack> out = NonNullList.withSize(grid.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < out.size(); i++) {
            if (grid.getItem(i).is(LightActions.KEROSENE)) out.set(i, new ItemStack(Items.BUCKET));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LightItems.LIGHT_RECIPE.get();
    }
}
