package faygolover.rplights.gametest;

import faygolover.rplights.RpLights;
import faygolover.rplights.block.ChargerBlock;
import faygolover.rplights.block.ChargerBlockEntity;
import faygolover.rplights.light.Cells;
import faygolover.rplights.light.Glow;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import faygolover.rplights.recipe.LightRecipe;
import faygolover.rplights.registry.LightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Логика без игрока: батарейки, сила питания, вид света, рецепт обслуживания, зарядка. */
@GameTestHolder(RpLights.MODID)
@PrefixGameTestTemplate(false)
public final class LightsGameTests {
    private LightsGameTests() {}

    static final String T = "platform";

    private static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) h.fail(what);
    }

    @GameTest(template = T)
    public static void cellsSeriesLevel(GameTestHelper h) {
        List<Cells.Cell> cells = List.of(new Cells.Cell(false, 0.9f, 1f), new Cells.Cell(false, 0.3f, 1f), new Cells.Cell(true, 0.7f, 0.7f));
        check(h, Math.abs(Cells.level(cells, 3) - 0.3f) < 1e-5, "уровень = самая слабая");
        check(h, Cells.level(cells, 4) == 0f, "неполный комплект — 0");
        List<Cells.Cell> d = Cells.drain(cells, 0.5f);
        check(h, d.get(1).charge() == 0f && Math.abs(d.get(0).charge() - 0.4f) < 1e-5, "разряд всех поровну, не ниже 0");
        check(h, Math.abs(d.get(2).charge() - 0.2f) < 1e-5 && d.get(2).cap() == 0.7f, "аккумулятор держит ёмкость");
        h.succeed();
    }

    @GameTest(template = T)
    public static void batteryItemRoundTrip(GameTestHelper h) {
        ItemStack acc = Cells.toItem(new Cells.Cell(true, 0.33f, 0.6f));
        Cells.Cell back = Cells.fromItem(acc);
        check(h, back.acc() && Math.abs(back.charge() - 0.33f) < 1e-5 && Math.abs(back.cap() - 0.6f) < 1e-5, "аккумулятор туда-обратно");
        check(h, Cells.needsInit(new ItemStack(LightItems.ACCUMULATOR.get())), "новый без заряда — ждёт заряда");
        h.succeed();
    }

    @GameTest(template = T)
    public static void lightRefreshAndGlow(GameTestHelper h) {
        ItemStack lantern = new ItemStack(LightItems.HAND_LANTERN.get());
        lantern.getOrCreateTag().putBoolean(Lights.INIT, true);
        lantern.getOrCreateTag().putByte(Lights.BULB, (byte) 1);
        Cells.write(lantern, List.of(new Cells.Cell(false, 0.2f, 1f), new Cells.Cell(false, 0.2f, 1f),
                new Cells.Cell(false, 0.2f, 1f), new Cells.Cell(false, 0.2f, 1f)));
        Lights.setOn(lantern, true);
        Lights.refresh(lantern);
        check(h, Lights.isOn(lantern), "ручной с батарейками горит");
        Glow g = Glow.of(lantern, LightType.HAND_LANTERN, 100, 1);
        check(h, g != null && g.strength() < 0.8f && g.b() < LightType.HAND_LANTERN.b, "накаливание при 20 % тусклее и рыжее");
        lantern.getOrCreateTag().putByte(Lights.BULB, (byte) 0);
        Lights.refresh(lantern);
        check(h, !Lights.isOn(lantern), "перегорела лампочка — гаснет");

        ItemStack old = new ItemStack(LightItems.OLD_FLASHLIGHT.get());
        old.getOrCreateTag().putBoolean(Lights.INIT, true);
        Cells.write(old, List.of(new Cells.Cell(false, 0.5f, 1f)));
        Lights.setOn(old, true);
        Lights.refresh(old);
        check(h, !Lights.isOn(old), "одной батарейки старому мало");
        h.succeed();
    }

    @GameTest(template = T)
    public static void saBatterySync(GameTestHelper h) {
        ItemStack sa = new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(Lights.SA_ID));
        if (sa.isEmpty() || !Lights.isSa(sa)) {
            h.succeed();
            return;
        }
        sa.getOrCreateTag().putBoolean(Lights.INIT, true);
        Cells.write(sa, List.of(new Cells.Cell(false, 0.5f, 1f)));
        Lights.refresh(sa);
        check(h, sa.getTag().getInt(Lights.SA_BATTERY) == Lights.SA_MAX / 2, "заряд SA = заряд батарейки");
        Cells.write(sa, List.of());
        sa.getOrCreateTag().putBoolean(Lights.SA_ON, true);
        Lights.refresh(sa);
        check(h, !Lights.isOn(sa) && sa.getTag().getInt(Lights.SA_BATTERY) == 0, "SA без батарейки не светит");
        h.succeed();
    }

    @GameTest(template = T)
    public static void serviceRecipe(GameTestHelper h) {
        ItemStack uv = new ItemStack(LightItems.UV_FLASHLIGHT.get());
        uv.getOrCreateTag().putBoolean(Lights.INIT, true);
        Cells.write(uv, List.of(new Cells.Cell(false, 0.1f, 1f), new Cells.Cell(false, 0.1f, 1f)));
        SimpleContainer grid = new SimpleContainer(4);
        grid.setItem(0, uv);
        grid.setItem(1, Cells.toItem(new Cells.Cell(true, 0.7f, 0.7f)));
        LightRecipe.Parts one = LightRecipe.parse(grid);
        check(h, one == null, "одной батарейки на два гнезда мало");
        grid.setItem(2, Cells.toItem(new Cells.Cell(true, 0.6f, 0.7f)));
        LightRecipe.Parts swap = LightRecipe.parse(grid);
        check(h, swap != null && swap.kind() == LightRecipe.Kind.SWAP, "замена батареек");
        check(h, swap != null && LightRecipe.returned(swap).size() == 2, "старые батарейки возвращаются");
        grid.setItem(1, ItemStack.EMPTY);
        grid.setItem(2, ItemStack.EMPTY);
        LightRecipe.Parts eject = LightRecipe.parse(grid);
        check(h, eject != null && eject.kind() == LightRecipe.Kind.EJECT, "один фонарь — вынуть батарейки");
        h.succeed();
    }

    @GameTest(template = T, timeoutTicks = 60)
    public static void chargerCharges(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 2, 1);
        h.setBlock(pos, LightItems.CHARGER.get().defaultBlockState());
        ChargerBlockEntity be = (ChargerBlockEntity) h.getBlockEntity(pos);
        check(h, be != null && be.insert(Cells.toItem(new Cells.Cell(true, 0.0f, 0.7f))), "вставить аккумулятор");
        h.runAfterDelay(2, () -> {
            check(h, h.getBlockState(pos).getValue(ChargerBlock.CELLS) == 1, "блок показывает аккумулятор");
            h.succeed();
        });
    }
}
