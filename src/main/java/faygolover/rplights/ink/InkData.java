package faygolover.rplights.ink;

import faygolover.rplights.net.LightsNet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/**
 * Невидимые чернила мира: точки 1/16 блока на гранях блоков, по чанкам. Точка — одно int:
 * x 4 бита, z 4, y+2048 12, грань 3, u 4, v 4. Видны только в луче УФ-фонаря (клиент, {@code InkClient}).
 */
public final class InkData extends SavedData {
    public static final String NAME = "rplights_ink";
    public static final int MAX_PER_CHUNK = 4096;

    private final Long2ObjectOpenHashMap<IntOpenHashSet> chunks = new Long2ObjectOpenHashMap<>();

    public static InkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(InkData::load, InkData::new, NAME);
    }

    // ------------------------------------------------------------------ точка

    public static int key(BlockPos pos, Direction face, int u, int v) {
        return (pos.getX() & 15) | (pos.getZ() & 15) << 4 | ((pos.getY() + 2048) & 0xFFF) << 8
                | face.get3DDataValue() << 20 | (u & 15) << 23 | (v & 15) << 27;
    }

    public static BlockPos pos(int key, int chunkX, int chunkZ) {
        return new BlockPos((chunkX << 4) | (key & 15), ((key >>> 8) & 0xFFF) - 2048, (chunkZ << 4) | ((key >>> 4) & 15));
    }

    public static Direction face(int key) {
        return Direction.from3DDataValue((key >>> 20) & 7);
    }

    public static int u(int key) {
        return (key >>> 23) & 15;
    }

    public static int v(int key) {
        return (key >>> 27) & 15;
    }

    /** Клетка грани по точке попадания (доли блока 0..1): какая ось — u, какая — v. */
    public static int[] cell(Direction face, double lx, double ly, double lz) {
        double a, b;
        switch (face.getAxis()) {
            case Y -> { a = lx; b = lz; }
            case Z -> { a = lx; b = ly; }
            default -> { a = lz; b = ly; }
        }
        return new int[]{clamp((int) Math.floor(a * 16.0)), clamp((int) Math.floor(b * 16.0))};
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(15, v));
    }

    // ------------------------------------------------------------------ правка

    /** Поставить точки грани; возвращает, сколько новых (на них тратятся чернила). */
    public int add(ServerLevel level, BlockPos pos, Direction face, int[][] cells) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        IntOpenHashSet set = chunks.computeIfAbsent(c, k -> new IntOpenHashSet());
        IntArrayList added = new IntArrayList();
        for (int[] uv : cells) {
            if (set.size() >= MAX_PER_CHUNK) break;
            int k = key(pos, face, uv[0], uv[1]);
            if (set.add(k)) added.add(k);
        }
        if (!added.isEmpty()) {
            setDirty();
            send(level, pos, added.toIntArray(), new int[0]);
        }
        return added.size();
    }

    /** Стереть точки грани в квадрате радиуса {@code r} клеток вокруг (u, v). */
    public void erase(ServerLevel level, BlockPos pos, Direction face, int u, int v, int r) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        IntOpenHashSet set = chunks.get(c);
        if (set == null) return;
        IntArrayList removed = new IntArrayList();
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                int uu = u + du, vv = v + dv;
                if (uu < 0 || vv < 0 || uu > 15 || vv > 15) continue;
                int k = key(pos, face, uu, vv);
                if (set.remove(k)) removed.add(k);
            }
        }
        finish(level, pos, c, set, removed);
    }

    /** Блок сломан — его чернила пропали. */
    public void clearBlock(ServerLevel level, BlockPos pos) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        IntOpenHashSet set = chunks.get(c);
        if (set == null) return;
        int base = key(pos, Direction.DOWN, 0, 0) & 0xFFFFF;
        IntArrayList removed = new IntArrayList();
        for (int k : set) if ((k & 0xFFFFF) == base) removed.add(k);
        for (int i = 0; i < removed.size(); i++) set.remove(removed.getInt(i));
        finish(level, pos, c, set, removed);
    }

    private void finish(ServerLevel level, BlockPos pos, long c, IntOpenHashSet set, IntArrayList removed) {
        if (removed.isEmpty()) return;
        if (set.isEmpty()) chunks.remove(c);
        setDirty();
        send(level, pos, new int[0], removed.toIntArray());
    }

    private static void send(ServerLevel level, BlockPos pos, int[] add, int[] remove) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        LightsNet.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new LightsNet.InkDelta(c, add, remove));
    }

    /** Точки чанка (для отправки игроку, который его увидел) или null. */
    public int[] chunk(long c) {
        IntOpenHashSet set = chunks.get(c);
        return set == null || set.isEmpty() ? null : set.toIntArray();
    }

    // ------------------------------------------------------------------ сохранение

    public static InkData load(CompoundTag tag) {
        InkData d = new InkData();
        ListTag list = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            IntOpenHashSet set = new IntOpenHashSet(ct.getIntArray("D"));
            if (!set.isEmpty()) d.chunks.put(ct.getLong("C"), set);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var e : chunks.long2ObjectEntrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag ct = new CompoundTag();
            ct.putLong("C", e.getLongKey());
            ct.putIntArray("D", e.getValue().toIntArray());
            list.add(ct);
        }
        tag.put("Chunks", list);
        return tag;
    }
}
