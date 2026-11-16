package faygolover.rplights.client;

import faygolover.flrender.api.LightBeams;
import faygolover.rplights.compat.CuriosCompat;
import faygolover.rplights.light.Glow;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Лучи фонарей RP Lights в этом кадре: у игроков ближе 72 блоков — в руках, на голове, на груди, на поясе. Фонарь SA
 * сюда не входит (его луч рисуют сам SA и FL Render). Считается раз за кадр, берут FL Render (настоящий свет) и
 * {@link ScreenFlash} (экранный проход). Направление догоняет взгляд с задержкой, как у SA.
 */
public final class ClientBeams {
    private ClientBeams() {}

    private static final double MAX_SOURCE_DISTANCE = 72.0;
    private static final float AIM_LAG = 0.055f;

    /** Луч и где лампа: у своего фонаря от первого лица экранный проход не прячет его за стенами. */
    public record Lamp(LightBeams.Spec spec, boolean firstPersonOwn, LightType type) {}

    private enum Mount { MAIN, OFF, HEAD, CHEST, BELT }

    private static final List<Lamp> LAMPS = new ArrayList<>();
    private static final Map<String, double[]> AIM = new HashMap<>();
    private static long lastGameTime = Long.MIN_VALUE;
    private static float lastPartial = -1.0f;
    private static long lastNanos;

    /** Лучи этого кадра (посчитаны при первом вызове в кадре). */
    public static List<Lamp> frame(Minecraft mc, float partial) {
        if (mc.level == null || mc.player == null) {
            LAMPS.clear();
            AIM.clear();
            return LAMPS;
        }
        long gt = mc.level.getGameTime();
        if (gt == lastGameTime && partial == lastPartial) return LAMPS;
        lastGameTime = gt;
        lastPartial = partial;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0f : Mth.clamp((now - lastNanos) / 1.0e9f, 0.0f, 0.1f);
        lastNanos = now;
        LAMPS.clear();
        Set<String> seen = new HashSet<>();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean fpView = mc.options.getCameraType().isFirstPerson();
        for (Player p : mc.level.players()) {
            if (p == null || p.isSpectator()) continue;
            boolean local = p == mc.player;
            if (!local && p.position().distanceToSqr(cam) > MAX_SOURCE_DISTANCE * MAX_SOURCE_DISTANCE) continue;
            boolean fp = local && fpView;
            add(p, p.getMainHandItem(), Mount.MAIN, local, fp, partial, dt, gt, seen);
            add(p, p.getOffhandItem(), Mount.OFF, local, fp, partial, dt, gt, seen);
            add(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.HEAD), Mount.HEAD, local, fp, partial, dt, gt, seen);
            add(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.BODY), Mount.CHEST, local, fp, partial, dt, gt, seen);
            add(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.BELT), Mount.BELT, local, fp, partial, dt, gt, seen);
        }
        AIM.keySet().retainAll(seen);
        return LAMPS;
    }

    private static void add(Player p, ItemStack s, Mount mount, boolean local, boolean fp, float partial, float dt, long tick,
                            Set<String> seen) {
        if (s.isEmpty()) return;
        LightType type = Lights.type(s);
        if (type == null || type == LightType.SA) return;
        boolean worn = mount == Mount.HEAD || mount == Mount.CHEST || mount == Mount.BELT;
        if (worn && (mount == Mount.BELT) != (type == LightType.LAMP)) return;
        if (worn && mount != Mount.BELT && type != LightType.HEADLAMP) return;
        Glow g = Glow.of(s, type, tick, p.getId() * 31 + mount.ordinal());
        if (g == null) return;

        float range = type.range, intensity = type.intensity * g.strength();
        if (type == LightType.LAMP && Lights.wickLow(s)) range = 6.0f;
        if (mount == Mount.CHEST) intensity *= 1.06f;
        float coreOut = type.coreOut, spillOut = type.spillOut, washOut = type.washOut;
        if (p.isUnderWater() && !type.point) {
            range *= 0.55f;
            coreOut *= 0.88f;
            spillOut *= 0.8f;
            washOut *= 0.7f;
            intensity *= 0.85f;
        }

        Vec3 eye = p.getEyePosition(partial);
        String key = p.getUUID() + "/" + mount.ordinal();
        seen.add(key);
        Vec3 look = p.getViewVector(partial).normalize();
        Vec3 o, dir;
        switch (mount) {
            case MAIN, OFF -> {
                dir = smooth(key, look, dt);
                Vec3 right = horizontalRight(dir);
                boolean offhand = mount == Mount.OFF;
                double side = (p.getMainArm() == HumanoidArm.RIGHT) != offhand ? 1.0 : -1.0;
                o = fp ? eye.add(right.scale(0.1 * side)).add(0.0, -0.09, 0.0).add(dir.scale(0.24))
                        : eye.add(right.scale(0.34 * side)).add(0.0, -0.3, 0.0).add(dir.scale(0.2));
                if (type.point) o = o.add(0.0, fp ? -0.25 : -0.35, 0.0);
            }
            case HEAD -> {
                dir = smooth(key, look, dt);
                o = eye.add(0.0, 0.16, 0.0).add(dir.scale(0.14));
            }
            case CHEST -> {
                float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
                float pitch = Mth.clamp(p.getViewXRot(partial), -30.0f, 30.0f) * Mth.DEG_TO_RAD;
                Vec3 body = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
                dir = smooth(key, body.normalize(), dt);
                Vec3 flat = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
                o = p.getPosition(partial).add(0.0, p.getEyeHeight() - 0.42, 0.0).add(flat.scale(0.3));
            }
            default -> {
                float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
                Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
                dir = new Vec3(0.0, 1.0, 0.0);
                o = p.getPosition(partial).add(0.0, p.getEyeHeight() * 0.5, 0.0).add(right.scale(0.3));
            }
        }

        LightBeams.Spec spec = type.point
                ? LightBeams.Spec.point(o.x, o.y, o.z, range, intensity, g.r(), g.g(), g.b(), p.getId(), local)
                : new LightBeams.Spec(o.x, o.y, o.z, dir.x, dir.y, dir.z, range, intensity,
                LightBeams.cos(type.coreIn), LightBeams.cos(coreOut), LightBeams.cos(type.spillIn), LightBeams.cos(spillOut),
                LightBeams.cos(type.washIn), LightBeams.cos(washOut), type.spillWeight, type.washWeight,
                g.r(), g.g(), g.b(), p.getId(), local);
        LAMPS.add(new Lamp(spec, fp && (mount == Mount.MAIN || mount == Mount.OFF || mount == Mount.HEAD), type));
    }

    private static Vec3 horizontalRight(Vec3 aim) {
        Vec3 r = aim.cross(new Vec3(0.0, 1.0, 0.0));
        return r.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : r.normalize();
    }

    private static Vec3 smooth(String key, Vec3 look, float dt) {
        double[] s = AIM.get(key);
        if (s == null || dt <= 0.0f) {
            AIM.put(key, new double[]{look.x, look.y, look.z});
            return look;
        }
        double follow = 1.0 - Math.exp(-dt / AIM_LAG);
        s[0] += (look.x - s[0]) * follow;
        s[1] += (look.y - s[1]) * follow;
        s[2] += (look.z - s[2]) * follow;
        Vec3 v = new Vec3(s[0], s[1], s[2]);
        return v.lengthSqr() < 1.0e-8 ? look : v.normalize();
    }

    /** Источник для FL Render: все лучи кадра. */
    public static void register() {
        LightBeams.register((mc, partial, cam, dt, out) -> {
            for (Lamp l : frame(mc, partial)) out.add(l.spec());
        });
    }
}
