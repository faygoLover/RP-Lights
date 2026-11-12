package faygolover.rplights.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.flrender.api.FlRender;
import faygolover.flrender.api.LightBeams;
import faygolover.rplights.RpLights;
import faygolover.rplights.mixin.client.PostChainAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Экранный проход фонарей RP Lights — как у фонаря SA (и его копии в RP Fixes): осветляет пиксели в конусе по глубине
 * кадра, но у каждого луча свой цвет и форма, лампа светит вокруг. Нужен с шейдерпаком (там нет настоящего света
 * FL Render) и без FL Render; при настоящем свете только подчёркивает пятно ({@code FlCone}). Сквозь стены: чужой фонарь,
 * которого камера не видит, не рисуется, дальность луча обрезается стеной на его оси (как {@code sa_flashlight_occlusion}).
 * По 4 луча за проход.
 */
public final class ScreenFlash {
    private ScreenFlash() {}

    private static final ResourceLocation CHAIN = new ResourceLocation(RpLights.MODID, "shaders/post/flash.json");
    private static final float FADE_START = 0.62f;

    private static PostChain chain;
    private static int width = -1, height = -1;
    private static boolean failed;
    private static boolean lastPack;

    public static void onRenderLevelStage(RenderLevelStageEvent e) {
        boolean afterWeather = e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER;
        boolean afterLevel = e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL;
        if (!afterWeather && !afterLevel) return;
        boolean pack = FlRender.shaderPack();
        if (pack != lastPack) {
            lastPack = pack;
            dispose();
        }
        if (pack ? !afterLevel : !afterWeather) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || failed) return;
        List<ClientBeams.Lamp> lamps = visible(mc, ClientBeams.frame(mc, e.getPartialTick()));
        if (lamps.isEmpty()) return;
        try {
            ensure(mc);
            if (chain == null) return;
            for (int from = 0; from < lamps.size(); from += 4) {
                upload(mc, lamps.subList(from, Math.min(lamps.size(), from + 4)), pack);
                mc.getMainRenderTarget().bindWrite(false);
                chain.process(e.getPartialTick());
            }
            mc.getMainRenderTarget().bindWrite(false);
        } catch (RuntimeException ex) {
            RpLights.LOGGER.error("RP Lights: экранный проход фонарей выключен", ex);
            failed = true;
            dispose();
        }
    }

    /** Свой первым; чужие — только если камера видит лампу; дальность — до стены на оси луча. */
    private static List<ClientBeams.Lamp> visible(Minecraft mc, List<ClientBeams.Lamp> all) {
        List<ClientBeams.Lamp> out = new ArrayList<>();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (ClientBeams.Lamp l : all) {
            LightBeams.Spec s = l.spec();
            if (l.firstPersonOwn()) {
                out.add(0, l);
                continue;
            }
            Vec3 lamp = new Vec3(s.x(), s.y(), s.z());
            BlockHitResult toLamp = mc.level.clip(new ClipContext(cam, lamp, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (toLamp.getType() == HitResult.Type.BLOCK && toLamp.getLocation().distanceTo(cam) < cam.distanceTo(lamp) - 0.25) continue;
            if (s.coreIn() > -0.99f) {
                Vec3 end = lamp.add(s.dx() * s.range(), s.dy() * s.range(), s.dz() * s.range());
                BlockHitResult hit = mc.level.clip(new ClipContext(lamp, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    float range = Math.min(s.range(), (float) hit.getLocation().distanceTo(lamp) / FADE_START + 1.0f);
                    s = new LightBeams.Spec(s.x(), s.y(), s.z(), s.dx(), s.dy(), s.dz(), range, s.intensity(), s.coreIn(), s.coreOut(),
                            s.spillIn(), s.spillOut(), s.washIn(), s.washOut(), s.spillWeight(), s.washWeight(), s.r(), s.g(), s.b(),
                            s.owner(), s.local());
                }
            }
            out.add(new ClientBeams.Lamp(s, false, l.type()));
        }
        return out;
    }

    private static void ensure(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        if (chain != null && main.width == width && main.height == height) return;
        dispose();
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            chain.resize(main.width, main.height);
            width = main.width;
            height = main.height;
        } catch (IOException ex) {
            RpLights.LOGGER.error("RP Lights: не загрузилась цепочка {}", CHAIN, ex);
            failed = true;
            chain = null;
        }
    }

    private static void upload(Minecraft mc, List<ClientBeams.Lamp> lamps, boolean pack) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cp = camera.getPosition();
        Matrix4f invProj = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
        Matrix4f invView = inverseView(camera);
        Matrix4f origin = new Matrix4f().zero(), axis = new Matrix4f().zero(), cone = new Matrix4f().zero(),
                wash = new Matrix4f().zero(), color = new Matrix4f().zero();
        Vector4f col = new Vector4f();
        for (int i = 0; i < lamps.size(); i++) {
            LightBeams.Spec s = lamps.get(i).spec();
            origin.setColumn(i, col.set((float) (s.x() - cp.x), (float) (s.y() - cp.y), (float) (s.z() - cp.z), s.range()));
            axis.setColumn(i, col.set((float) s.dx(), (float) s.dy(), (float) s.dz(), s.intensity()));
            cone.setColumn(i, col.set(s.coreIn(), s.coreOut(), s.spillIn(), s.spillOut()));
            wash.setColumn(i, col.set(s.washIn(), s.washOut(), s.spillWeight(), s.washWeight()));
            color.setColumn(i, col.set(s.r(), s.g(), s.b(), s.coreIn() <= -0.99f ? 1.0f : 0.0f));
        }
        float flCone = !pack && FlRender.flashlightLight() ? 1.0f : 0.0f;
        for (PostPass pass : ((PostChainAccessor) chain).rplights$passes()) {
            EffectInstance fx = pass.getEffect();
            fx.safeGetUniform("InvProjMat").set(invProj);
            fx.safeGetUniform("InvViewMat").set(invView);
            fx.safeGetUniform("BeamOrigin").set(origin);
            fx.safeGetUniform("BeamAxis").set(axis);
            fx.safeGetUniform("BeamCone").set(cone);
            fx.safeGetUniform("BeamWash").set(wash);
            fx.safeGetUniform("BeamColor").set(color);
            fx.safeGetUniform("BeamCount").set((float) lamps.size());
            fx.safeGetUniform("FlCone").set(flCone);
            fx.safeGetUniform("ShaderPackMode").set(pack ? 1.0f : 0.0f);
        }
    }

    private static Matrix4f inverseView(Camera camera) {
        Matrix3f vanilla = RenderSystem.getInverseViewRotationMatrix();
        return vanilla != null && Math.abs(vanilla.determinant()) > 1.0e-4f
                ? new Matrix4f().set(vanilla)
                : new Matrix4f().rotateY(-(camera.getYRot() + 180.0f) * ((float) Math.PI / 180.0f))
                .rotateX(-camera.getXRot() * ((float) Math.PI / 180.0f));
    }

    public static void dispose() {
        if (chain != null) chain.close();
        chain = null;
        width = height = -1;
    }
}
