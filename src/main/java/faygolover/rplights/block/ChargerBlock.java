package faygolover.rplights.block;

import faygolover.rplights.light.Cells;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.item.AccumulatorItem;
import faygolover.rplights.registry.LightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Зарядное устройство на 4 аккумулятора. ПКМ аккумулятором — вставить, пустой рукой — вынуть последний, Shift+ПКМ —
 * вынуть все. Обычные батарейки не заряжает, вздувшийся аккумулятор не берёт. Огонёк: красный — заряжается, зелёный —
 * все полные. Электричество не нужно.
 */
public class ChargerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty CELLS = IntegerProperty.create("cells", 0, 4);
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 4, 13);

    public ChargerBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELLS, 0).setValue(CHARGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, CELLS, CHARGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChargerBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = p.getItemInHand(hand);
        if (Cells.isAccumulator(held)) {
            if (AccumulatorItem.swollen(held)) {
                LightActions.tell(p, "rplights.msg.swollen");
                return InteractionResult.CONSUME;
            }
            if (Cells.needsInit(held)) Cells.initBattery(held, p.getRandom(), p.isCreative());
            if (!be.insert(held.copyWithCount(1))) {
                LightActions.tell(p, "rplights.msg.charger_full");
                return InteractionResult.CONSUME;
            }
            if (!p.isCreative()) held.shrink(1);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.5f, 1.7f);
            return InteractionResult.CONSUME;
        }
        if (Cells.isRegular(held)) {
            LightActions.tell(p, "rplights.msg.not_rechargeable");
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() || p.isShiftKeyDown()) {
            boolean any = false;
            do {
                ItemStack out = be.takeLast();
                if (out.isEmpty()) break;
                LightActions.give(p, out);
                any = true;
            } while (p.isShiftKeyDown());
            if (any) level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.BLOCKS, 0.5f, 1.4f);
            return any ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ChargerBlockEntity be) be.dropAll();
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != LightItems.CHARGER_BE.get() ? null
                : (l, p, s, be) -> ((ChargerBlockEntity) be).serverTick();
    }
}
