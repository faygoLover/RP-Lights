package faygolover.rplights.light;

import faygolover.rplights.LightsConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Виды света: питание, как садится, луч. Углы — половинные, в градусах: ядро (in, out), спад, широкий ореол; веса спада и
 * ореола относительно ядра. Яркость — относительно фонаря Survivor's Arsenal (1.0, дальность 38). Цвета мягкие.
 */
public enum LightType {
    /** Отличный фонарь — Survivor's Arsenal ({@code sa_combat:flashlight}); луч рисует сам SA и FL Render. */
    SA("sa", Source.LED, 1, LightsConfig.SA_HIGH_HOURS,
            38, 1.0f, 23, 30, 32, 42, 42, 53, 0.34f, 0.18f, 1.0f, 0.96f, 0.88f, false),
    /** Ручной фонарь-прожектор с ручкой: лампа накаливания, тёплый, широкий тусклый ореол. */
    HAND_LANTERN("hand_lantern", Source.INCANDESCENT, 4, LightsConfig.HAND_LANTERN_HOURS,
            38, 0.65f, 14, 22, 26, 38, 40, 58, 0.45f, 0.22f, 1.0f, 0.82f, 0.58f, false),
    /** Старый хромированный фонарик со светодиодом: холодный, узкое неровное пятно, капризный. */
    OLD("old_flashlight", Source.LED, 2, LightsConfig.OLD_HOURS,
            28, 0.6f, 16, 24, 26, 34, 34, 44, 0.30f, 0.12f, 0.88f, 0.93f, 1.0f, false),
    /** Налобный / нагрудный: светодиоды, шире SA. */
    HEADLAMP("headlamp", Source.LED, 3, LightsConfig.HEADLAMP_HOURS,
            34, 0.8f, 26, 36, 38, 50, 50, 62, 0.40f, 0.22f, 1.0f, 0.95f, 0.86f, false),
    /** Динамо-фонарик: без батареек, лампочка от генератора. */
    DYNAMO("dynamo_flashlight", Source.DYNAMO, 0, null,
            18, 0.45f, 20, 32, 34, 48, 48, 60, 0.40f, 0.25f, 1.0f, 0.84f, 0.60f, false),
    /** Керосиновая лампа «Летучая мышь»: светит вокруг. */
    LAMP("hurricane_lamp", Source.FLAME, 0, LightsConfig.LAMP_FULL_HOURS,
            10, 0.7f, 0, 0, 0, 0, 0, 0, 0.0f, 0.0f, 1.0f, 0.72f, 0.42f, true),
    /** УФ-фонарик: фиолетовый, тусклый, узкий. */
    UV("uv_flashlight", Source.LED, 2, LightsConfig.UV_HOURS,
            16, 0.3f, 12, 18, 20, 28, 28, 36, 0.25f, 0.10f, 0.62f, 0.42f, 1.0f, false),
    /** RGB-фонарик: перебор цветов (прикол). */
    RGB("rgb_flashlight", Source.LED, 3, LightsConfig.RGB_HOURS,
            20, 0.5f, 23, 30, 32, 42, 42, 53, 0.34f, 0.18f, 1.0f, 1.0f, 1.0f, false);

    /** Как садится и чем светит. */
    public enum Source {
        /** Горит в полную силу почти до конца, ниже 15 % — мигает, потом гаснет. */
        LED,
        /** Ниже 40 % тускнеет и рыжеет, в конце — тлеющая спираль. */
        INCANDESCENT,
        /** Пламя: мягко колышется. */
        FLAME,
        /** Генератор: яркость по запасу. */
        DYNAMO
    }

    public final String id;
    public final Source source;
    public final int cells;
    private final ForgeConfigSpec.DoubleValue hours;
    public final float range, intensity;
    public final float coreIn, coreOut, spillIn, spillOut, washIn, washOut, spillWeight, washWeight;
    public final float r, g, b;
    public final boolean point;

    LightType(String id, Source source, int cells, ForgeConfigSpec.DoubleValue hours, float range, float intensity,
              float coreIn, float coreOut, float spillIn, float spillOut, float washIn, float washOut,
              float spillWeight, float washWeight, float r, float g, float b, boolean point) {
        this.id = id;
        this.source = source;
        this.cells = cells;
        this.hours = hours;
        this.range = range;
        this.intensity = intensity;
        this.coreIn = coreIn;
        this.coreOut = coreOut;
        this.spillIn = spillIn;
        this.spillOut = spillOut;
        this.washIn = washIn;
        this.washOut = washOut;
        this.spillWeight = spillWeight;
        this.washWeight = washWeight;
        this.r = r;
        this.g = g;
        this.b = b;
        this.point = point;
    }

    /** RP-часы света от полного комплекта (0 — не от батареек). */
    public double hours() {
        return hours == null ? 0.0 : LightsConfig.get(hours);
    }

    public boolean usesCells() {
        return cells > 0;
    }
}
