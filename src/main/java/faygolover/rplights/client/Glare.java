package faygolover.rplights.client;

import faygolover.flrender.api.LightBeams;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Ослепление: чужой фонарь светит тебе в глаза (ты в его луче и смотришь на лампу, стены между вами нет) — экран
 * заливает светом его цвета; сильнее близко, у оси луча и когда смотришь прямо на лампу. Нарастает быстро, сходит
 * медленно (остаётся «зайчик»). Фонари RP Lights и SA; лампа «Летучая мышь» не слепит.
 */
public final class Glare {
    private Glare() {}

    private static final float RISE = 0.06f, FALL = 0.7f, MAX = 0.88f;
    private static float level, r = 1.0f, g = 1.0f, b = 1.0f;
    private static long lastNanos;

    public static void render(ForgeGui gui, GuiGraphics gg, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0f : Mth.clamp((now - lastNanos) / 1.0e9f, 0.0f, 0.2f);
        lastNanos = now;
        float target = 0.0f;
        float tr = 0, tg = 0, tb = 0;
        if (mc.level != null && mc.player != null && !mc.player.isSpectator()) {
            Vec3 eye = mc.player.getEyePosition(partial);
            Vec3 look = mc.player.getViewVector(partial);
            for (ClientBeams.Lamp l : ClientBeams.frame(mc, partial)) {
                LightBeams.Spec s = l.spec();
                if (s.local() || l.type().point || l.type() == LightType.UV) continue;
                float a = amount(mc, eye, look, s);
                if (a > 0.0f) {
                    target += a;
                    tr += s.r() * a;
                    tg += s.g() * a;
                    tb += s.b() * a;
                }
            }
            for (Player p : mc.level.players()) {
                if (p == mc.player || p.isSpectator() || p.distanceToSqr(mc.player) > 40.0 * 40.0) continue;
                for (boolean off : new boolean[]{false, true}) {
                    ItemStack st = off ? p.getOffhandItem() : p.getMainHandItem();
                    if (!Lights.isSa(st) || !Lights.isOn(st)) continue;
                    LightBeams.Spec s = sa(p, st, off, partial);
                    float a = amount(mc, eye, look, s);
                    if (a > 0.0f) {
                        target += a;
                        tr += a;
                        tg += a * 0.97f;
                        tb += a * 0.93f;
                    }
                }
            }
        }
        if (target > 0.0f) {
            r = tr / target;
            g = tg / target;
            b = tb / target;
        }
        target = Math.min(MAX, target);
        float tau = target > level ? RISE : FALL;
        level += (target - level) * (dt <= 0.0f ? 1.0f : 1.0f - (float) Math.exp(-dt / tau));
        if (level < 0.01f) return;
        int base = (int) (Math.min(1.0f, level) * 255.0f);
        int col = Mth.color(0.75f + 0.25f * r, 0.75f + 0.25f * g, 0.75f + 0.25f * b);
        gg.fill(0, 0, w, h, (base << 24) | col);
    }

    /** Сколько слепит луч {@code s}: в луче ли глаза, смотрит ли игрок на лампу, нет ли стены. */
    private static float amount(Minecraft mc, Vec3 eye, Vec3 look, LightBeams.Spec s) {
        double dx = eye.x - s.x(), dy = eye.y - s.y(), dz = eye.z - s.z();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.4 || d > s.range()) return 0.0f;
        double cos = (dx * s.dx() + dy * s.dy() + dz * s.dz()) / d;
        double cone = Mth.clamp((cos - s.spillOut()) / Math.max(1.0e-4, s.coreIn() - s.spillOut()), 0.0, 1.0);
        if (cone <= 0.0) return 0.0f;
        double facing = -(look.x * dx + look.y * dy + look.z * dz) / d;
        double face = Mth.clamp((facing - 0.55) / 0.4, 0.0, 1.0);
        if (face <= 0.0) return 0.0f;
        Vec3 lamp = new Vec3(s.x(), s.y(), s.z());
        if (mc.level.clip(new ClipContext(eye, lamp, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType()
                != HitResult.Type.MISS) return 0.0f;
        double fall = 1.0 - d / s.range();
        return (float) (s.intensity() * cone * cone * face * face * (0.3 + 0.7 * fall * fall) * 1.3);
    }

    /** Луч фонаря SA чужого игрока (как его считает SA: из руки, по взгляду). */
    private static LightBeams.Spec sa(Player p, ItemStack st, boolean off, float partial) {
        LightType t = LightType.SA;
        boolean low = Lights.low(st);
        Vec3 look = p.getViewVector(partial).normalize();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        double side = (p.getMainArm() == HumanoidArm.RIGHT) != off ? 1.0 : -1.0;
        Vec3 o = p.getEyePosition(partial).add(right.scale(0.34 * side)).add(0.0, -0.3, 0.0).add(look.scale(0.2));
        return new LightBeams.Spec(o.x, o.y, o.z, look.x, look.y, look.z, low ? 20.0f : t.range, low ? 0.62f : t.intensity,
                LightBeams.cos(t.coreIn), LightBeams.cos(t.coreOut), LightBeams.cos(t.spillIn), LightBeams.cos(t.spillOut),
                LightBeams.cos(t.washIn), LightBeams.cos(t.washOut), t.spillWeight, t.washWeight, 1.0f, 1.0f, 1.0f, p.getId(), false);
    }
}
