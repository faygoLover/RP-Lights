package faygolover.rplights.light;

import faygolover.rplights.LightsConfig;
import faygolover.rplights.item.LightItem;
import faygolover.rplights.registry.LightItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Состояние фонаря в NBT — общее для своих предметов и фонаря Survivor's Arsenal. Сервер меняет его раз в секунду или
 * по действию игрока; клиент только читает (свет, модель).
 * <ul>
 *   <li>{@code LightOn} — светит (у SA — его {@code FlashlightOn}); сервер держит его честным: села батарейка,
 *   перегорела лампочка, намок — выключено. По нему же светят Psyche и Zone Artifacts ({@code id{LightOn}}).</li>
 *   <li>{@code RplLvl} — сила питания 0..1 для вида света (как садится); {@code RplInit} — найденный фонарь уже получил
 *   свои батарейки.</li>
 *   <li>Ручной фонарь — {@code RplBulb} (1 цела, 0 перегорела); старый — {@code RplWet} (сколько RP-часов ещё сохнуть);
 *   SA — {@code RplLow} (ближний свет); RGB — {@code RplMode}; динамо — {@code RplDyn} (игровой тик, до которого
 *   горит); лампа — {@code RplFuel} (мБ керосина), {@code RplWick} (1 — фитиль прикручен).</li>
 * </ul>
 */
public final class Lights {
    private Lights() {}

    public static final String ON = "LightOn", INIT = "RplInit", LVL = "RplLvl", BULB = "RplBulb", WET = "RplWet",
            LOW = "RplLow", MODE = "RplMode", DYN = "RplDyn", FUEL = "RplFuel", WICK = "RplWick";
    public static final String SA_ON = "FlashlightOn", SA_BATTERY = "FlashlightBattery";
    /** Полный заряд SA в его единицах. */
    public static final int SA_MAX = 24000;
    public static final ResourceLocation SA_ID = new ResourceLocation("sa_combat", "flashlight");
    /** Полный бак лампы, мБ. */
    public static final float FUEL_MAX = 1000.0f;
    /** Режимов RGB: радуга, перелив, 8 цветов. */
    public static final int RGB_MODES = 10;

    /** Вид света предмета или null. */
    public static LightType type(ItemStack s) {
        if (s.isEmpty()) return null;
        if (s.getItem() instanceof LightItem li) return li.type();
        if (s.is(LightItems.DYNAMO.get())) return LightType.DYNAMO;
        if (s.is(LightItems.LAMP.get())) return LightType.LAMP;
        if (isSa(s)) return LightType.SA;
        return null;
    }

    public static boolean isSa(ItemStack s) {
        return SA_ID.equals(ForgeRegistries.ITEMS.getKey(s.getItem()));
    }

    public static boolean isOn(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return false;
        if (isSa(s)) return t.getBoolean(SA_ON) && (!t.contains(SA_BATTERY) || t.getInt(SA_BATTERY) > 0);
        return t.getBoolean(ON);
    }

    public static void setOn(ItemStack s, boolean on) {
        s.getOrCreateTag().putBoolean(isSa(s) ? SA_ON : ON, on);
    }

    /** Сила питания 0..1 (как её видит свет). */
    public static float level(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains(LVL) ? t.getFloat(LVL) : 1.0f;
    }

    public static boolean needsInit(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null || !t.getBoolean(INIT);
    }

    /**
     * Найденный фонарь: батарейки с каким-то зарядом или пустой, лампочка цела или нет, у лампы — сколько керосина.
     * {@code full} — новый (креатив).
     */
    public static void init(ItemStack s, RandomSource rnd, boolean full) {
        LightType type = type(s);
        if (type == null) return;
        CompoundTag t = s.getOrCreateTag();
        t.putBoolean(INIT, true);
        if (type.usesCells()) {
            boolean has = full || rnd.nextDouble() < LightsConfig.get(LightsConfig.LOOT_LIGHT_HAS_CELLS);
            Cells.write(s, has ? Cells.randomSet(type.cells, rnd, full) : List.of());
        }
        if (type == LightType.HAND_LANTERN) {
            t.putByte(BULB, (byte) (full || rnd.nextDouble() < LightsConfig.get(LightsConfig.LOOT_BULB_OK) ? 1 : 0));
        }
        if (type == LightType.LAMP) {
            t.putFloat(FUEL, full ? FUEL_MAX : rnd.nextFloat() < 0.3f ? 0.0f : rnd.nextFloat() * FUEL_MAX);
        }
        refresh(s);
    }

    /** Пересчитать силу питания (и заряд у SA) по батарейкам; нет силы — выключить. */
    public static void refresh(ItemStack s) {
        LightType type = type(s);
        if (type == null) return;
        CompoundTag t = s.getOrCreateTag();
        float lvl;
        if (type.usesCells()) {
            lvl = Cells.level(Cells.read(s), type.cells);
        } else if (type == LightType.LAMP) {
            lvl = Math.min(1.0f, t.getFloat(FUEL) / FUEL_MAX);
        } else {
            lvl = 1.0f;
        }
        t.putFloat(LVL, lvl);
        if (type == LightType.SA) {
            t.putInt(SA_BATTERY, lvl > 0.0f ? Math.max(1, Math.round(lvl * SA_MAX)) : 0);
            if (lvl <= 0.0f) t.putBoolean(SA_ON, false);
        } else if (!canLight(s)) {
            t.putBoolean(ON, false);
        }
    }

    /** Может ли светить сейчас: есть питание, цела лампочка, сухой. Динамо — всегда (свет от жима). */
    public static boolean canLight(ItemStack s) {
        LightType type = type(s);
        if (type == null) return false;
        CompoundTag t = s.getTag();
        if (type == LightType.DYNAMO) return true;
        if (type == LightType.LAMP) return t != null && t.getFloat(FUEL) > 0.0f;
        if (t == null) return true;
        if (type.usesCells() && Cells.level(Cells.read(s), type.cells) <= 0.0f) return false;
        if (type == LightType.HAND_LANTERN && t.contains(BULB) && t.getByte(BULB) == 0) return false;
        if (type == LightType.OLD && t.getFloat(WET) > 0.0f) return false;
        return true;
    }

    public static boolean bulbOk(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null || !t.contains(BULB) || t.getByte(BULB) != 0;
    }

    public static boolean wet(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.getFloat(WET) > 0.0f;
    }

    public static boolean low(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.getBoolean(LOW);
    }

    public static int mode(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null ? 0 : Math.floorMod(t.getInt(MODE), RGB_MODES);
    }

    /** RP-часы света от полного комплекта для текущего режима. */
    public static double hours(ItemStack s, LightType type) {
        if (type == LightType.SA && low(s)) return LightsConfig.get(LightsConfig.SA_LOW_HOURS);
        if (type == LightType.LAMP && wickLow(s)) return LightsConfig.get(LightsConfig.LAMP_LOW_HOURS);
        return type.hours();
    }

    public static boolean wickLow(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.getByte(WICK) == 1;
    }

    /**
     * Горение лампы {@code rpSeconds} RP-секунд: керосин убывает (прикрученный фитиль — вдвое медленнее), кончился —
     * гаснет. Общее для лампы в руке, на поясе и поставленной. Возвращает true, если погасла сейчас.
     */
    public static boolean burnLamp(ItemStack s, double rpSeconds) {
        if (!isOn(s)) return false;
        CompoundTag tag = s.getOrCreateTag();
        float burn = (float) (rpSeconds / (hours(s, LightType.LAMP) * 3600.0) * FUEL_MAX);
        tag.putFloat(FUEL, Math.max(0.0f, tag.getFloat(FUEL) - burn));
        refresh(s);
        return !isOn(s);
    }

    /** Тик, до которого горит динамо (0 — не жали). */
    public static long dynamoUntil(ItemStack s) {
        CompoundTag t = s.getTag();
        return t == null ? 0L : t.getLong(DYN);
    }
}
