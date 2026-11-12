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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * Невидимые чернила у клиента: точки чанков с сервера; рисуются только там, куда падает луч УФ-фонаря (свой или
 * чужой): чем ближе к оси и к лампе — тем ярче, на гранях, повёрнутых к лампе, не за стеной. Свечение — цветом маркера,
 * складывается со светом; отступ от грани растёт с расстоянием (без мерцания).
 */
public final class InkClient {
    private InkClient() {}

    private static final Long2ObjectOpenHashMap<Int2ByteOpenHashMap> CHUNKS = new Long2ObjectOpenHashMap<>();
    /** Свечение по цвету чернил: синий, зелёный, жёлтый, оранжевый, розовый, фиолетовый, красный, белый. */
    private static final float[][] GLOW = {
            {0.55f, 0.82f, 1.0f}, {0.45f, 1.0f, 0.45f}, {1.0f, 0.95f, 0.35f}, {1.0f, 0.6f, 0.22f},
            {1.0f, 0.42f, 0.72f}, {0.72f, 0.48f, 1.0f}, {1.0f, 0.28f, 0.25f}, {0.95f, 0.95f, 1.0f}};
    private static final float GAIN = 2.6f;
    /** Видимость граней для лампы в этом кадре (pos+грань → 1 видна, 2 нет). */
    private static final Long2ByteOpenHashMap SEEN = new Long2ByteOpenHashMap();

    /** С сервера: {@code remove == null} — весь чанк заново; добавленные — с цветом, стёртые — только место. */
    public static void apply(long chunk, int[] add, int[] remove) {
        Int2ByteOpenHashMap set = remove == null ? new Int2ByteOpenHashMap() : CHUNKS.computeIfAbsent(chunk, k -> new Int2ByteOpenHashMap());
        for (int p : add) set.put(InkData.place(p), (byte) InkData.color(p));
        if (remove != null) for (int k : remove) set.remove(InkData.place(k));
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
        List<LightBeams.Spec> uv = new ArrayList<>();
        for (ClientBeams.Lamp l : ClientBeams.frame(mc, e.getPartialTick())) {
            if (l.type() == LightType.UV) uv.add(l.spec());
        }
        if (uv.isEmpty()) return;

        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
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
                        quad(vc, m, pos, face, u, v, a, GLOW[en.getByteValue() & 7], cam);
                    }
                }
            }
        }
        ps.popPose();
        buffers.endBatch(RenderType.lightning());
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

    private static void quad(VertexConsumer vc, Matrix4f m, BlockPos pos, Direction face, int u, int v, float a, float[] col, Vec3 cam) {
        float a0 = u / 16.0f, a1 = (u + 1) / 16.0f, b0 = v / 16.0f, b1 = (v + 1) / 16.0f;
        // отступ от грани растёт с расстоянием — иначе мерцает с гранью (точность глубины)
        double dist = Math.sqrt(cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        float eps = (float) (0.006 + dist * 0.0012);
        float x = pos.getX(), y = pos.getY(), z = pos.getZ();
        boolean pos_ = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float off = pos_ ? 1.0f + eps : -eps;
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
        // обе стороны — без забот о порядке обхода
        for (int i = 0; i < 4; i++) vc.vertex(m, p[i][0], p[i][1], p[i][2]).color(col[0], col[1], col[2], a).endVertex();
        for (int i = 3; i >= 0; i--) vc.vertex(m, p[i][0], p[i][1], p[i][2]).color(col[0], col[1], col[2], a).endVertex();
    }
}
