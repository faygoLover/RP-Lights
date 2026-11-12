package faygolover.rplights.ink;

import faygolover.rplights.net.LightsNet;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
 * Чернила на блоках: точки 1/16 блока на гранях, по чанкам. Место точки — int: x 4 бита, z 4, y+512 10, грань 3,
 * u 4, v 4. Вид точки — byte: {@link #UV} — невидимые (светятся только в луче УФ-фонаря), 1..8 — обычные цвета
 * маркеров ({@code InkClient}). На клетке одна точка: другой вид поверх её заменяет. Стирает губка.
 */
public final class InkData extends SavedData {
    public static final String NAME = "rplights_ink";
    public static final int MAX_PER_CHUNK = 4096;
    public static final byte UV = 0;
    private static final int BLOCK_MASK = (1 << 18) - 1;

    private final Long2ObjectOpenHashMap<Int2ByteOpenHashMap> chunks = new Long2ObjectOpenHashMap<>();

    public static InkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(InkData::load, InkData::new, NAME);
    }

    // ------------------------------------------------------------------ точка

    public static int key(BlockPos pos, Direction face, int u, int v) {
        return (pos.getX() & 15) | (pos.getZ() & 15) << 4 | ((pos.getY() + 512) & 0x3FF) << 8
                | face.get3DDataValue() << 18 | (u & 15) << 21 | (v & 15) << 25;
    }

    public static BlockPos pos(int key, int chunkX, int chunkZ) {
        return new BlockPos((chunkX << 4) | (key & 15), ((key >>> 8) & 0x3FF) - 512, (chunkZ << 4) | ((key >>> 4) & 15));
    }

    public static Direction face(int key) {
        return Direction.from3DDataValue((key >>> 18) & 7);
    }

    public static int u(int key) {
        return (key >>> 21) & 15;
    }

    public static int v(int key) {
        return (key >>> 25) & 15;
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

    /** Поставить точки грани видом {@code kind}; возвращает, сколько новых или заменённых (на них тратятся чернила). */
    public int add(ServerLevel level, BlockPos pos, Direction face, int[][] cells, byte kind) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Int2ByteOpenHashMap set = chunks.computeIfAbsent(c, k -> new Int2ByteOpenHashMap());
        IntArrayList added = new IntArrayList();
        ByteArrayList kinds = new ByteArrayList();
        for (int[] uv : cells) {
            int k = key(pos, face, uv[0], uv[1]);
            boolean had = set.containsKey(k);
            if (had && set.get(k) == kind) continue;
            if (!had && set.size() >= MAX_PER_CHUNK) break;
            set.put(k, kind);
            added.add(k);
            kinds.add(kind);
        }
        if (set.isEmpty()) chunks.remove(c);
        if (!added.isEmpty()) {
            setDirty();
            send(level, pos, added.toIntArray(), kinds.toByteArray(), new int[0]);
        }
        return added.size();
    }

    /** Стереть точки грани в квадрате радиуса {@code r} клеток вокруг (u, v); возвращает, сколько стёрто. */
    public int erase(ServerLevel level, BlockPos pos, Direction face, int u, int v, int r) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Int2ByteOpenHashMap set = chunks.get(c);
        if (set == null) return 0;
        IntArrayList removed = new IntArrayList();
        for (int du = -r; du <= r; du++) {
            for (int dv = -r; dv <= r; dv++) {
                int uu = u + du, vv = v + dv;
                if (uu < 0 || vv < 0 || uu > 15 || vv > 15) continue;
                int k = key(pos, face, uu, vv);
                if (set.containsKey(k)) {
                    set.remove(k);
                    removed.add(k);
                }
            }
        }
        finish(level, pos, c, set, removed);
        return removed.size();
    }

    /** Блок сломан — его чернила пропали. */
    public void clearBlock(ServerLevel level, BlockPos pos) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Int2ByteOpenHashMap set = chunks.get(c);
        if (set == null) return;
        int base = key(pos, Direction.DOWN, 0, 0) & BLOCK_MASK;
        IntArrayList removed = new IntArrayList();
        for (int k : set.keySet()) if ((k & BLOCK_MASK) == base) removed.add(k);
        for (int i = 0; i < removed.size(); i++) set.remove(removed.getInt(i));
        finish(level, pos, c, set, removed);
    }

    private void finish(ServerLevel level, BlockPos pos, long c, Int2ByteOpenHashMap set, IntArrayList removed) {
        if (removed.isEmpty()) return;
        if (set.isEmpty()) chunks.remove(c);
        setDirty();
        send(level, pos, new int[0], new byte[0], removed.toIntArray());
    }

    private static void send(ServerLevel level, BlockPos pos, int[] add, byte[] kinds, int[] remove) {
        long c = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        LightsNet.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new LightsNet.InkDelta(c, add, kinds, remove));
    }

    /** Точки чанка — для игрока, который его увидел: {места, виды} или null. */
    public Object[] chunk(long c) {
        Int2ByteOpenHashMap set = chunks.get(c);
        if (set == null || set.isEmpty()) return null;
        int[] places = new int[set.size()];
        byte[] kinds = new byte[set.size()];
        int i = 0;
        for (var e : set.int2ByteEntrySet()) {
            places[i] = e.getIntKey();
            kinds[i++] = e.getByteValue();
        }
        return new Object[]{places, kinds};
    }

    // ------------------------------------------------------------------ сохранение

    public static InkData load(CompoundTag tag) {
        InkData d = new InkData();
        ListTag list = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            int[] places = ct.getIntArray("D");
            byte[] kinds = ct.getByteArray("K");
            Int2ByteOpenHashMap set = new Int2ByteOpenHashMap();
            for (int j = 0; j < places.length; j++) set.put(places[j], j < kinds.length ? kinds[j] : UV);
            if (!set.isEmpty()) d.chunks.put(ct.getLong("C"), set);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var e : chunks.long2ObjectEntrySet()) {
            Object[] pk = chunk(e.getLongKey());
            if (pk == null) continue;
            CompoundTag ct = new CompoundTag();
            ct.putLong("C", e.getLongKey());
            ct.putIntArray("D", (int[]) pk[0]);
            ct.putByteArray("K", (byte[]) pk[1]);
            list.add(ct);
        }
        tag.put("Chunks", list);
        return tag;
    }
}
