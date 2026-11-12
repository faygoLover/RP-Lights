package faygolover.rplights.item;

import faygolover.rplights.ink.InkData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Маркеры и губка: держишь ПКМ и ведёшь по блоку. Маркер рисует точками 1/16 блока (поверх другого цвета — перекрашивает),
 * чернил на {@link #INK} точек: УФ-маркер ({@code kind} 0) — невидимыми, светятся только в луче УФ-фонаря; обычные
 * ({@code kind} 1..8, {@link #COLOR_IDS}) — видимой краской. Губка ({@code kind} < 0) стирает любые полосой 5×5 клеток.
 */
public class InkToolItem extends Item {
    public static final int INK = 800;
    /** Цвета обычных маркеров, вид 1..8 (порядок — как цвета в {@code InkClient}). */
    public static final String[] COLOR_IDS = {"black", "red", "blue", "green", "yellow", "orange", "violet", "white"};
    private static final double REACH = 4.5;
    /** Последняя клетка, чтобы вести линию без разрывов: игрок → [pos, грань, u, v]. */
    private static final Map<UUID, long[]> LAST = new HashMap<>();

    private final int kind;

    public InkToolItem(Properties props, int kind) {
        super(props);
        this.kind = kind;
    }

    public boolean sponge() {
        return kind < 0;
    }

    public int kind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        LAST.remove(player.getUUID());
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack stack, int remaining) {
        if (level.isClientSide || !(e instanceof Player p) || !(level instanceof ServerLevel sl)) return;
        HitResult hit = p.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK || level.getBlockState(bh.getBlockPos()).isAir()) {
            LAST.remove(p.getUUID());
            return;
        }
        BlockPos pos = bh.getBlockPos();
        Direction face = bh.getDirection();
        Vec3 l = bh.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        int[] uv = InkData.cell(face, l.x, l.y, l.z);
        InkData data = InkData.get(sl);
        long[] last = LAST.get(p.getUUID());
        List<int[]> cells = new ArrayList<>();
        if (last != null && last[0] == pos.asLong() && last[1] == face.get3DDataValue()) {
            line((int) last[2], (int) last[3], uv[0], uv[1], cells);
        } else {
            cells.add(uv);
        }
        LAST.put(p.getUUID(), new long[]{pos.asLong(), face.get3DDataValue(), uv[0], uv[1]});
        if (sponge()) {
            int wiped = 0;
            for (int[] c : cells) wiped += data.erase(sl, pos, face, c[0], c[1], 2);
            if (remaining % 7 == 0) level.playSound(null, pos, SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.2f, 1.5f);
            if (wiped > 0 && remaining % 5 == 0) level.playSound(null, pos, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.1f, 1.8f);
            return;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue();
        if (left <= 0) return;
        if (cells.size() > left) cells = cells.subList(0, left);
        int added = data.add(sl, pos, face, cells.toArray(new int[0][]), (byte) kind);
        if (added > 0 && !p.getAbilities().instabuild) {
            stack.hurtAndBreak(added, p, x -> x.broadcastBreakEvent(p.getUsedItemHand()));
        }
        if (added > 0 && remaining % 6 == 0) level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.08f, 1.9f);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity e, int remaining) {
        LAST.remove(e.getUUID());
    }

    /** Клетки отрезка (без первой — она уже стоит). */
    private static void line(int u0, int v0, int u1, int v1, List<int[]> out) {
        int du = Math.abs(u1 - u0), dv = -Math.abs(v1 - v0);
        int su = u0 < u1 ? 1 : -1, sv = v0 < v1 ? 1 : -1;
        int err = du + dv;
        int u = u0, v = v0;
        while (u != u1 || v != v1) {
            int e2 = 2 * err;
            if (e2 >= dv) {
                err += dv;
                u += su;
            }
            if (e2 <= du) {
                err += du;
                v += sv;
            }
            out.add(new int[]{u, v});
        }
        if (out.isEmpty()) out.add(new int[]{u1, v1});
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String id = sponge() ? "ink_sponge" : kind == InkData.UV ? "uv_marker" : "marker";
        tip.add(Component.translatable("item.rplights." + id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("rplights.tip.use." + id).withStyle(ChatFormatting.DARK_GRAY));
    }
}
