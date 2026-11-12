package faygolover.rplights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import faygolover.flrender.api.LightBeams;
import faygolover.flrender.render.LatePass;
import faygolover.rplights.ink.InkData;
import faygolover.rplights.light.LightType;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Чернила у клиента: точки чанков с сервера.
 * <ul>
 *   <li>Обычные (маркеры цветов) — как краска на блоке: светом мира (ночью темнеют), с тенью грани, до 64 блоков.</li>
 *   <li>УФ — только там, куда падает луч УФ-фонаря (свой или чужой): ярче у оси и близко, на гранях к лампе, не за
 *   стеной; голубовато-белое свечение складывается со светом.</li>
 * </ul>
 * Сдвиг глубины слоя ({@link InkRenderTypes}) плюс отступ от грани, растущий с расстоянием, — без мерцания.
 */
public final class InkClient {
    private InkClient() {}

    private static final Long2ObjectOpenHashMap<Int2ByteOpenHashMap> CHUNKS = new Long2ObjectOpenHashMap<>();
    /** Цвета обычных чернил (вид 1..8): чёрный, красный, синий, зелёный, жёлтый, оранжевый, фиолетовый, белый. */
    private static final float[][] PAINT = {
            {0.12f, 0.12f, 0.14f}, {0.78f, 0.17f, 0.15f}, {0.16f, 0.30f, 0.78f}, {0.17f, 0.52f, 0.22f},
            {0.90f, 0.78f, 0.17f}, {0.92f, 0.50f, 0.15f}, {0.47f, 0.24f, 0.72f}, {0.93f, 0.93f, 0.93f}};
    private static final float[] UV_GLOW = {0.55f, 0.82f, 1.0f};
    private static final float GAIN = 2.6f;
    private static final double PAINT_RANGE = 64.0;
    /** Видимость граней для лампы в этом кадре (pos+грань → 1 видна, 2 нет). */
    private static final Long2ByteOpenHashMap SEEN = new Long2ByteOpenHashMap();

    /** С сервера: {@code remove == null} — весь чанк заново. */
    public static void apply(long chunk, int[] add, byte[] kinds, int[] remove) {
        Int2ByteOpenHashMap set = remove == null ? new Int2ByteOpenHashMap() : CHUNKS.computeIfAbsent(chunk, k -> new Int2ByteOpenHashMap());
        for (int i = 0; i < add.length; i++) set.put(add[i], i < kinds.length ? kinds[i] : InkData.UV);
        if (remove != null) for (int k : remove) set.remove(k);
        if (set.isEmpty()) CHUNKS.remove(chunk);
        else CHUNKS.put(chunk, set);
    }

    public static void onChunkUnload(ChunkEvent.Unload e) {
        if (e.getLevel() instanceof ClientLevel) CHUNKS.remove(e.getChunk().getPos().toLong());
    }

    public static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ClientLevel) CHUNKS.clear();
    }

    public static void clear() {
        CHUNKS.clear();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent e) {
        if (LatePass.deferred(e) || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || CHUNKS.isEmpty()) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        paint(mc, buffers.getBuffer(InkRenderTypes.PAINT), m, cam);
        buffers.endBatch(InkRenderTypes.PAINT);
        List<LightBeams.Spec> uv = new ArrayList<>();
        for (ClientBeams.Lamp l : ClientBeams.frame(mc, e.getPartialTick())) {
            if (l.type() == LightType.UV) uv.add(l.spec());
        }
        if (!uv.isEmpty()) {
            glow(mc, buffers.getBuffer(InkRenderTypes.GLOW), m, cam, uv);
            buffers.endBatch(InkRenderTypes.GLOW);
        }
        ps.popPose();
    }

    /** Обычные чернила: все видимые точки рядом, со светом мира у грани. */
    private static void paint(Minecraft mc, VertexConsumer vc, Matrix4f m, Vec3 cam) {
        int r = (int) PAINT_RANGE;
        int cx0 = Mth.floor(cam.x - r) >> 4, cx1 = Mth.floor(cam.x + r) >> 4;
        int cz0 = Mth.floor(cam.z - r) >> 4, cz1 = Mth.floor(cam.z + r) >> 4;
        double r2 = PAINT_RANGE * PAINT_RANGE;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                Int2ByteOpenHashMap dots = CHUNKS.get(ChunkPos.asLong(cx, cz));
                if (dots == null) continue;
                for (var en : dots.int2ByteEntrySet()) {
                    int kind = en.getByteValue();
                    if (kind <= InkData.UV || kind > PAINT.length) continue;
                    int k = en.getIntKey();
                    BlockPos pos = InkData.pos(k, cx, cz);
                    if (cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r2) continue;
                    Direction face = InkData.face(k);
                    int light = LevelRenderer.getLightColor(mc.level, pos.relative(face));
                    float shade = mc.level.getShade(face, true);
                    float[] c = PAINT[kind - 1];
                    quad(vc, m, pos, face, InkData.u(k), InkData.v(k), c[0] * shade, c[1] * shade, c[2] * shade, 0.93f, light, cam);
                }
            }
        }
    }

    /** УФ-чернила: только в лучах УФ-фонарей. */
    private static void glow(Minecraft mc, VertexConsumer vc, Matrix4f m, Vec3 cam, List<LightBeams.Spec> uv) {
        SEEN.clear();
        int rays = 0;
        for (LightBeams.Spec s : uv) {
            int r = (int) Math.ceil(s.range()) + 1;
            int cx0 = Mth.floor(s.x() - r) >> 4, cx1 = Mth.floor(s.x() + r) >> 4;
            int cz0 = Mth.floor(s.z() - r) >> 4, cz1 = Mth.floor(s.z() + r) >> 4;
            Vec3 lamp = new Vec3(s.x(), s.y(), s.z());
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    Int2ByteOpenHashMap dots = CHUNKS.get(ChunkPos.asLong(cx, cz));
                    if (dots == null) continue;
                    for (var en : dots.int2ByteEntrySet()) {
                        if (en.getByteValue() != InkData.UV) continue;
                        int k = en.getIntKey();
                        BlockPos pos = InkData.pos(k, cx, cz);
                        Direction face = InkData.face(k);
                        int u = InkData.u(k), v = InkData.v(k);
                        double[] c = centre(pos, face, u, v);
                        double dx = c[0] - s.x(), dy = c[1] - s.y(), dz = c[2] - s.z();
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d > s.range() || d < 1.0e-3) continue;
                        double nx = dx / d, ny = dy / d, nz = dz / d;
                        double toward = -(nx * face.getStepX() + ny * face.getStepY() + nz * face.getStepZ());
                        if (toward <= 0.05) continue;
                        double cos = nx * s.dx() + ny * s.dy() + nz * s.dz();
                        double cone = Mth.clamp((cos - s.spillOut()) / Math.max(1.0e-4, s.coreIn() - s.spillOut()), 0.0, 1.0);
                        if (cone <= 0.0) continue;
                        double fall = 1.0 - d / s.range();
                        float a = (float) Math.min(1.0, GAIN * s.intensity() * cone * cone * fall * (0.4 + 0.6 * toward));
                        if (a < 0.02f) continue;
                        long faceKey = pos.asLong() * 7L + face.ordinal();
                        byte vis = SEEN.get(faceKey);
                        if (vis == 0) {
                            if (rays >= 160) continue;
                            rays++;
                            Vec3 target = new Vec3(pos.getX() + 0.5 + face.getStepX() * 0.5, pos.getY() + 0.5 + face.getStepY() * 0.5,
                                    pos.getZ() + 0.5 + face.getStepZ() * 0.5);
                            BlockHitResult hit = mc.level.clip(new ClipContext(lamp, target, ClipContext.Block.OUTLINE,
                                    ClipContext.Fluid.NONE, mc.player));
                            boolean ok = hit.getType() == HitResult.Type.MISS
                                    || (hit.getBlockPos().equals(pos) && hit.getDirection() == face)
                                    || hit.getLocation().distanceToSqr(target) < 0.36;
                            vis = (byte) (ok ? 1 : 2);
                            SEEN.put(faceKey, vis);
                        }
                        if (vis != 1) continue;
                        quad(vc, m, pos, face, u, v, UV_GLOW[0], UV_GLOW[1], UV_GLOW[2], a, -1, cam);
                    }
                }
            }
        }
    }

    private static double[] centre(BlockPos pos, Direction face, int u, int v) {
        double a = (u + 0.5) / 16.0, b = (v + 0.5) / 16.0;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        double off = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        return switch (face.getAxis()) {
            case Y -> new double[]{x + a, y + off, z + b};
            case Z -> new double[]{x + a, y + b, z + off};
            default -> new double[]{x + off, y + b, z + a};
        };
    }

    /** Клетка 1/16 на грани; {@code light} < 0 — без карты света (формат слоя УФ). */
    private static void quad(VertexConsumer vc, Matrix4f m, BlockPos pos, Direction face, int u, int v,
                             float r, float g, float b, float a, int light, Vec3 cam) {
        float a0 = u / 16.0f, a1 = (u + 1) / 16.0f, b0 = v / 16.0f, b1 = (v + 1) / 16.0f;
        double dist = Math.sqrt(cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        float eps = (float) (0.003 + dist * 0.0006);
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float off = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f + eps : -eps;
        float[][] p = new float[4][];
        switch (face.getAxis()) {
            case Y -> {
                p[0] = new float[]{x + a0, y + off, z + b0};
                p[1] = new float[]{x + a1, y + off, z + b0};
                p[2] = new float[]{x + a1, y + off, z + b1};
                p[3] = new float[]{x + a0, y + off, z + b1};
            }
            case Z -> {
                p[0] = new float[]{x + a0, y + b0, z + off};
                p[1] = new float[]{x + a1, y + b0, z + off};
                p[2] = new float[]{x + a1, y + b1, z + off};
                p[3] = new float[]{x + a0, y + b1, z + off};
            }
            default -> {
                p[0] = new float[]{x + off, y + b0, z + a0};
                p[1] = new float[]{x + off, y + b0, z + a1};
                p[2] = new float[]{x + off, y + b1, z + a1};
                p[3] = new float[]{x + off, y + b1, z + a0};
            }
        }
        for (int i = 0; i < 4; i++) {
            VertexConsumer c = vc.vertex(m, p[i][0], p[i][1], p[i][2]).color(r, g, b, a);
            if (light >= 0) c.uv2(light);
            c.endVertex();
        }
    }
}
