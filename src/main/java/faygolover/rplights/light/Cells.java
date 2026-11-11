package faygolover.rplights.light;

import faygolover.rplights.RpLights;
import faygolover.rplights.registry.LightItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Батарейки. У каждой свой заряд в NBT ({@code RplCharge}, в «полных батарейках»: новая обычная — 1.0); у аккумулятора
 * ещё ёмкость ({@code RplCap}, новый — 0.7). Игрок заряда не видит: ни полоски, ни процента — только тестер и свет.
 * Батарейки внутри фонаря — список {@code RplCells}: {@code a} — аккумулятор, {@code c} — заряд, {@code m} — ёмкость.
 * Обычная батарейка — предметы тега {@code rplights:batteries} (батарейка Survivor's Arsenal {@code sa_core:battery}).
 */
public final class Cells {
    private Cells() {}

    public static final TagKey<Item> BATTERIES = ItemTags.create(new ResourceLocation(RpLights.MODID, "batteries"));
    public static final String CHARGE = "RplCharge", CAP = "RplCap", CELLS = "RplCells";
    /** Новый аккумулятор держит 70 % обычной батарейки. */
    public static final float ACC_CAP = 0.7f;
    public static final ResourceLocation SA_BATTERY = new ResourceLocation("sa_core", "battery");

    /** Одна батарейка: аккумулятор ли, заряд, ёмкость (у обычной — 1). */
    public record Cell(boolean acc, float charge, float cap) {
        public Cell withCharge(float c) {
            return new Cell(acc, Math.max(0.0f, Math.min(cap, c)), cap);
        }
    }

    // ------------------------------------------------------------------ предметы-батарейки

    public static boolean isAccumulator(ItemStack s) {
        return !s.isEmpty() && s.is(LightItems.ACCUMULATOR.get());
    }

    public static boolean isRegular(ItemStack s) {
        return !s.isEmpty() && !isAccumulator(s) && s.is(BATTERIES);
    }

    public static boolean isBattery(ItemStack s) {
        return isAccumulator(s) || isRegular(s);
    }

    /** Заряд и ёмкость ещё не заданы (батарейка из лута или из креатива). */
    public static boolean needsInit(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null || !t.contains(CHARGE);
    }

    /**
     * Задать заряд найденной батарейке: обычная — четверть полных, остальные 5–95 %; аккумулятор — ёмкость 0.4–0.7,
     * заряд случайный. {@code full} — новая (креатив).
     */
    public static void initBattery(ItemStack s, RandomSource rnd, boolean full) {
        CompoundTag t = s.getOrCreateTag();
        if (isAccumulator(s)) {
            float cap = full ? ACC_CAP : 0.4f + rnd.nextFloat() * (ACC_CAP - 0.4f);
            t.putFloat(CAP, cap);
            t.putFloat(CHARGE, full ? cap : rnd.nextFloat() * cap);
        } else {
            t.putFloat(CHARGE, full || rnd.nextFloat() < 0.25f ? 1.0f : 0.05f + rnd.nextFloat() * 0.9f);
        }
    }

    /** Батарейка из предмета (без заданного заряда — полная). */
    public static Cell fromItem(ItemStack s) {
        CompoundTag t = s.getTag();
        boolean acc = isAccumulator(s);
        float cap = acc ? (t != null && t.contains(CAP) ? t.getFloat(CAP) : ACC_CAP) : 1.0f;
        float charge = t != null && t.contains(CHARGE) ? t.getFloat(CHARGE) : cap;
        return new Cell(acc, Math.max(0.0f, Math.min(cap, charge)), cap);
    }

    /** Предмет из батарейки (обычная — батарейка SA или первая из тега). */
    public static ItemStack toItem(Cell c) {
        ItemStack s;
        if (c.acc()) {
            s = new ItemStack(LightItems.ACCUMULATOR.get());
            s.getOrCreateTag().putFloat(CAP, c.cap());
        } else {
            s = new ItemStack(regularItem());
            if (s.isEmpty()) return ItemStack.EMPTY;
        }
        s.getOrCreateTag().putFloat(CHARGE, c.charge());
        return s;
    }

    private static Item regular;

    public static Item regularItem() {
        if (regular == null) {
            Item sa = ForgeRegistries.ITEMS.getValue(SA_BATTERY);
            if (sa != null && sa != net.minecraft.world.item.Items.AIR) regular = sa;
            else {
                var tag = ForgeRegistries.ITEMS.tags();
                regular = tag == null ? net.minecraft.world.item.Items.AIR
                        : tag.getTag(BATTERIES).stream().findFirst().orElse(net.minecraft.world.item.Items.AIR);
            }
        }
        return regular;
    }

    public static float charge(ItemStack battery) {
        return fromItem(battery).charge();
    }

    // ------------------------------------------------------------------ батарейки в фонаре

    public static List<Cell> read(ItemStack light) {
        List<Cell> out = new ArrayList<>();
        CompoundTag t = light.getTag();
        if (t == null || !t.contains(CELLS, Tag.TAG_LIST)) return out;
        ListTag list = t.getList(CELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            out.add(new Cell(c.getBoolean("a"), c.getFloat("c"), c.contains("m") ? c.getFloat("m") : 1.0f));
        }
        return out;
    }

    public static void write(ItemStack light, List<Cell> cells) {
        ListTag list = new ListTag();
        for (Cell c : cells) {
            CompoundTag t = new CompoundTag();
            t.putBoolean("a", c.acc());
            t.putFloat("c", c.charge());
            if (c.acc()) t.putFloat("m", c.cap());
            list.add(t);
        }
        light.getOrCreateTag().put(CELLS, list);
    }

    /**
     * Сила питания: батарейки стоят последовательно — фонарь живёт, пока жива самая слабая. 0 — комплект неполный или
     * одна села.
     */
    public static float level(List<Cell> cells, int needed) {
        if (needed <= 0 || cells.size() < needed) return 0.0f;
        float min = Float.MAX_VALUE;
        for (Cell c : cells) min = Math.min(min, c.charge());
        return Math.max(0.0f, min);
    }

    /** Разрядить все батарейки фонаря на {@code amount} (в полных батарейках). */
    public static List<Cell> drain(List<Cell> cells, float amount) {
        List<Cell> out = new ArrayList<>(cells.size());
        for (Cell c : cells) out.add(c.withCharge(c.charge() - amount));
        return out;
    }

    /** Комплект для найденного фонаря: батарейки примерно одного заряда (из одной пачки). */
    public static List<Cell> randomSet(int n, RandomSource rnd, boolean full) {
        List<Cell> out = new ArrayList<>(n);
        float base = full ? 1.0f : 0.1f + rnd.nextFloat() * 0.9f;
        for (int i = 0; i < n; i++) {
            float c = full ? 1.0f : Math.max(0.02f, Math.min(1.0f, base + (rnd.nextFloat() - 0.5f) * 0.12f));
            out.add(new Cell(false, c, 1.0f));
        }
        return out;
    }
}
