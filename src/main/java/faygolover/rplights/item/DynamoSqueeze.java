package faygolover.rplights.item;

import net.minecraft.Util;

/** Время последнего жима динамо у своего игрока — для подёргивания руки (клиент). */
public final class DynamoSqueeze {
    private DynamoSqueeze() {}

    private static volatile long lastMillis;

    public static void squeezed() {
        lastMillis = Util.getMillis();
    }

    /** 0..1: насколько сжата рукоятка сейчас (быстро сжимается и отпускает за ~0,25 с). */
    public static float amount() {
        long dt = Util.getMillis() - lastMillis;
        if (dt < 0 || dt > 260) return 0.0f;
        float t = dt / 260.0f;
        return t < 0.3f ? t / 0.3f : 1.0f - (t - 0.3f) / 0.7f;
    }
}
