package faygolover.rplights;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Настройки сервера ({@code serverconfig/rplights-server.toml}). Время работы — в RP-часах непрерывного света от полного
 * комплекта новых батареек (RP-сутки при 3-часовых сутках — 3 реальных часа, RP-час — 7,5 минуты).
 */
public final class LightsConfig {
    private LightsConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue SA_HIGH_HOURS, SA_LOW_HOURS, HAND_LANTERN_HOURS, OLD_HOURS, HEADLAMP_HOURS,
            UV_HOURS, RGB_HOURS, LAMP_FULL_HOURS, LAMP_LOW_HOURS, CHARGE_HOURS;
    public static final ForgeConfigSpec.DoubleValue BULB_BURN_PER_HOUR, BULB_BURN_ON_FALL, OLD_MISFIRE, OLD_RAIN_PER_MINUTE,
            OLD_DRY_HOURS, ACC_WEAR, ACC_DEAD_CAPACITY;
    public static final ForgeConfigSpec.IntValue DYNAMO_SQUEEZE_TICKS, DYNAMO_MAX_TICKS;
    public static final ForgeConfigSpec.DoubleValue DYNAMO_STAMINA;
    public static final ForgeConfigSpec.DoubleValue LOOT_LIGHT_HAS_CELLS, LOOT_BULB_OK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Время работы в RP-часах непрерывного света от полного комплекта новых батареек.").push("runtime");
        SA_HIGH_HOURS = b.comment("Отличный фонарь (Survivor's Arsenal), дальний свет, 1 батарейка.").defineInRange("sa_high", 4.0, 0.05, 1000.0);
        SA_LOW_HOURS = b.comment("Отличный фонарь, ближний свет.").defineInRange("sa_low", 16.0, 0.05, 1000.0);
        HAND_LANTERN_HOURS = b.comment("Ручной фонарь, лампа накаливания, 4 батарейки (последние 40 % тускнеет).").defineInRange("hand_lantern", 20.0, 0.05, 1000.0);
        OLD_HOURS = b.comment("Старый фонарик, светодиод, 2 батарейки.").defineInRange("old_flashlight", 24.0, 0.05, 1000.0);
        HEADLAMP_HOURS = b.comment("Налобный / нагрудный фонарь, светодиоды, 3 батарейки.").defineInRange("headlamp", 12.0, 0.05, 1000.0);
        UV_HOURS = b.comment("УФ-фонарик, 2 батарейки.").defineInRange("uv_flashlight", 10.0, 0.05, 1000.0);
        RGB_HOURS = b.comment("RGB-фонарик, 3 батарейки.").defineInRange("rgb_flashlight", 12.0, 0.05, 1000.0);
        LAMP_FULL_HOURS = b.comment("Керосиновая лампа, полный бак (1 ведро), фитиль выкручен.").defineInRange("lamp_full", 40.0, 0.05, 1000.0);
        LAMP_LOW_HOURS = b.comment("Керосиновая лампа, фитиль прикручен.").defineInRange("lamp_low", 80.0, 0.05, 1000.0);
        CHARGE_HOURS = b.comment("Зарядка аккумулятора с нуля до полного.").defineInRange("charge", 3.0, 0.05, 1000.0);
        b.pop();

        b.push("faults");
        BULB_BURN_PER_HOUR = b.comment("Шанс перегореть лампочке ручного фонаря за RP-час света.").defineInRange("bulb_burn_per_hour", 1.0 / 200.0, 0.0, 1.0);
        BULB_BURN_ON_FALL = b.comment("Шанс перегореть лампочке при падении с фонарём в руке (с высоты больше 4 блоков).").defineInRange("bulb_burn_on_fall", 0.1, 0.0, 1.0);
        OLD_MISFIRE = b.comment("Старый фонарик: шанс, что не включится с первого щелчка.").defineInRange("old_misfire", 0.3, 0.0, 1.0);
        OLD_RAIN_PER_MINUTE = b.comment("Старый фонарик: шанс замкнуть под дождём за RP-минуту (включённый, над головой небо).").defineInRange("old_rain_per_minute", 1.0 / 40.0, 0.0, 1.0);
        OLD_DRY_HOURS = b.comment("Старый фонарик: сколько RP-часов сохнет после воды (не под дождём и не в воде).").defineInRange("old_dry_hours", 6.0, 0.0, 1000.0);
        ACC_WEAR = b.comment("Износ аккумулятора: доля ёмкости, которую он теряет на каждую единицу заряда (полная батарейка = 1).").defineInRange("accumulator_wear", 0.01, 0.0, 1.0);
        ACC_DEAD_CAPACITY = b.comment("Аккумулятор вздулся и не заряжается, когда ёмкость ниже этого (новый — 0.7).").defineInRange("accumulator_dead", 0.245, 0.0, 1.0);
        b.pop();

        b.push("dynamo");
        DYNAMO_SQUEEZE_TICKS = b.comment("Сколько тиков света даёт один жим.").defineInRange("squeeze_ticks", 80, 1, 2400);
        DYNAMO_MAX_TICKS = b.comment("Самый большой запас света в тиках.").defineInRange("max_ticks", 400, 1, 24000);
        DYNAMO_STAMINA = b.comment("Выносливость RP Stamina за жим (обычный максимум — 1000).").defineInRange("stamina", 5.0, 0.0, 1000.0);
        b.pop();

        b.push("loot");
        LOOT_LIGHT_HAS_CELLS = b.comment("Найденный фонарь: шанс, что в нём есть батарейки (остальные — пустые).").defineInRange("light_has_cells", 0.7, 0.0, 1.0);
        LOOT_BULB_OK = b.comment("Найденный ручной фонарь: шанс, что лампочка цела.").defineInRange("bulb_ok", 0.85, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    public static double get(ForgeConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    public static int get(ForgeConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }
}
