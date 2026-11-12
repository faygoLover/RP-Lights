package faygolover.rplights.block;

import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.Lights;
import faygolover.rplights.registry.LightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Поставленная «Летучая мышь»: на пол или под потолок (ставится ПКМ лампой по блоку). Светит ванильным светом
 * (13, с прикрученным фитилём 9) — это видят и Psyche, и все остальные. Керосин горит по RP-времени, как в руке.
 * ПКМ пустой рукой — зажечь / погасить, с ведром керосина — заправить, Shift+ПКМ пустой рукой — взять (в руку,
 * как была: горит — горит). Сама лампа со всем её NBT хранится в {@link LampBlockEntity}.
 */
public class LampBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty LOW = BooleanProperty.create("low");

    private static final VoxelShape STANDING = Block.box(3.5, 0.0, 4.0, 12.5, 11.5, 12.0);
    private static final VoxelShape HANGING_SHAPE = Block.box(3.5, 4.5, 4.0, 12.5, 16.0, 12.0);

    public LampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HANGING, false)
                .setValue(LIT, false).setValue(LOW, false));
    }

    public static int light(BlockState s) {
        return s.getValue(LIT) ? (s.getValue(LOW) ? 9 : 13) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HANGING, LIT, LOW);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(HANGING) ? HANGING_SHAPE : STANDING;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        boolean wet = !ctx.getLevel().getFluidState(ctx.getClickedPos()).isEmpty();
        boolean lit = Lights.isOn(stack) && Lights.canLight(stack) && !wet;
        BlockState base = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(LIT, lit).setValue(LOW, Lights.wickLow(stack));
        boolean ceiling = ctx.getClickedFace() == Direction.DOWN;
        for (boolean hanging : ceiling ? new boolean[]{true, false} : new boolean[]{false, true}) {
            BlockState s = base.setValue(HANGING, hanging);
            if (s.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return s;
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        Direction to = s.getValue(HANGING) ? Direction.UP : Direction.DOWN;
        return Block.canSupportCenter(level, pos.relative(to), to.getOpposite());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        Direction to = s.getValue(HANGING) ? Direction.UP : Direction.DOWN;
        if (dir == to && !s.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, dir, other, level, pos, otherPos);
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean kerosene = held.is(LightActions.KEROSENE);
        if (!held.isEmpty() && !kerosene) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LampBlockEntity be)) return InteractionResult.PASS;
        ItemStack lamp = be.lamp();
        if (player.isShiftKeyDown() && held.isEmpty()) {
            be.clear();
            level.removeBlock(pos, false);
            player.setItemInHand(hand, lamp);
            level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.7f, 1.1f);
            return InteractionResult.CONSUME;
        }
        if (kerosene) {
            LightActions.refuel(player, lamp, held, hand);
        } else if (Lights.isOn(lamp)) {
            Lights.setOn(lamp, false);
            level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.9f);
        } else if (!level.getFluidState(pos).isEmpty() || !Lights.canLight(lamp)) {
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.4f, 1.2f);
        } else {
            Lights.setOn(lamp, true);
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.5f, 1.0f);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.15f, 1.6f);
        }
        be.changed();
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        if (player.isCreative() && level.getBlockEntity(pos) instanceof LampBlockEntity be) be.clear();
        super.playerWillDestroy(level, pos, s, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!s.is(now.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LampBlockEntity be) {
            ItemStack lamp = be.lamp();
            if (!lamp.isEmpty()) {
                Lights.setOn(lamp, false);
                Block.popResource(level, pos, lamp);
            }
            be.clear();
        }
        super.onRemove(s, level, pos, now, moved);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState s, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof LampBlockEntity be && !be.lamp().isEmpty()) return be.lamp().copy();
        return new ItemStack(LightItems.LAMP.get());
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource rnd) {
        if (!s.getValue(LIT)) return;
        double top = (s.getValue(HANGING) ? 4.5 : 0.0) + 9.0;
        if (rnd.nextFloat() < 0.35f) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + top / 16.0, pos.getZ() + 0.5, 0.0, 0.015, 0.0);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState s) {
        return new LampBlockEntity(pos, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState s, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, LightItems.LAMP_BE.get(), LampBlockEntity::serverTick);
    }
}
