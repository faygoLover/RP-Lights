package faygolover.rplights.light;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Как светит фонарь в этот тик: сила (доля от полной) и цвет. Общее для луча и модели «включён». Мигания считаются по
 * игровому тику и номеру владельца — у всех игроков одинаково, без пакетов.
 */
public record Glow(float strength, float r, float g, float b) {

    private static final float WARM_R = 1.0f, WARM_G = 0.55f, WARM_B = 0.28f;

    /** Свет фонаря сейчас или null — темно. */
    public static Glow of(ItemStack s, LightType type, long tick, int seed) {
        if (type == LightType.DYNAMO) {
            long rem = Lights.dynamoUntil(s) - tick;
            if (rem <= 0) return null;
            float f = 0.2f + 0.8f * Math.min(1.0f, rem / 200.0f);
            // К концу запаса лампочка ещё и желтеет — генератор крутится медленнее.
            return warm(type, f, 1.0f - Math.min(1.0f, rem / 120.0f));
        }
        if (!Lights.isOn(s)) return null;
        float level = Lights.level(s);
        switch (type.source) {
            case LED -> {
                if (level <= 0.0f) return null;
                if (!ledVisible(level, tick)) return null;
                if (type == LightType.OLD && dropout(level, tick, seed)) return null;
                if (type == LightType.RGB) return rgb(Lights.mode(s), tick, seed);
                return new Glow(1.0f, type.r, type.g, type.b);
            }
            case INCANDESCENT -> {
                if (level <= 0.0f) return null;
                float k = Math.min(1.0f, level / 0.4f);
                float f = level >= 0.4f ? 1.0f : 0.12f + 0.88f * (float) Math.pow(k, 1.3);
                return warm(type, f, 1.0f - k);
            }
            case FLAME -> {
                float t = tick + seed * 13.0f;
                float f = 1.0f + 0.05f * (Mth.sin(t * 0.9f) + 0.5f * Mth.sin(t * 2.3f + 1.7f)) / 1.5f;
                if (Lights.wickLow(s)) f *= 0.5f;
                return new Glow(f, type.r, type.g, type.b);
            }
            default -> {
                return null;
            }
        }
    }

    private static Glow warm(LightType type, float f, float warmth) {
        float w = Mth.clamp(warmth, 0.0f, 1.0f);
        return new Glow(f, Mth.lerp(w, type.r, WARM_R), Mth.lerp(w, type.g, WARM_G), Mth.lerp(w, type.b, WARM_B));
    }

    /** Садящийся светодиод мигает — как у фонаря SA. */
    private static boolean ledVisible(float level, long t) {
        if (level <= 0.04f) return t % 14 < 5 || t % 41 < 3;
        if (level <= 0.09f) return t % 28 < 19 && t % 11 != 0;
        if (level <= 0.16f) return t % 70 < 62;
        return true;
    }

    /** Старый фонарик: плохой контакт — раз в полминуты-минуту гаснет на 3–10 тиков, у слабых батареек чаще. */
    private static boolean dropout(float level, long t, int seed) {
        long window = t / 30;
        float r = hash(window, seed);
        float chance = level < 0.3f ? 0.12f : 0.04f;
        if (r >= chance) return false;
        long len = 3 + (long) (hash(window, seed + 7) * 8);
        return t - window * 30 < len;
    }

    /** RGB: 0 — радуга, 1 — перелив с миганием, 2..9 — цвет. Мягкие, не кислотные. */
    private static Glow rgb(int mode, long t, int seed) {
        float hue;
        if (mode == 0) {
            hue = (t % 200) / 200.0f;
        } else if (mode == 1) {
            if (t % 10 >= 5) return null;
            hue = ((t / 10) % 8) / 8.0f;
        } else {
            hue = (mode - 2) / 8.0f;
        }
        int rgb = Mth.hsvToRgb(hue, 0.6f, 1.0f);
        return new Glow(1.0f, ((rgb >> 16) & 255) / 255.0f, ((rgb >> 8) & 255) / 255.0f, (rgb & 255) / 255.0f);
    }

    private static float hash(long a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }
}
