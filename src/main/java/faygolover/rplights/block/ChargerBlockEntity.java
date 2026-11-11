package faygolover.rplights.block;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rplights.LightsConfig;
import faygolover.rplights.light.Cells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import faygolover.rplights.registry.LightItems;

/**
 * Аккумуляторы в зарядном устройстве. Раз в секунду каждый получает заряд по RP-времени (с нуля до полного — за
 * {@code runtime.charge} RP-часов) и чуть изнашивается: ёмкость падает на {@code accumulator_wear} от залитого.
 */
public class ChargerBlockEntity extends BlockEntity {
    public static final int SLOTS = 4;
    private final NonNullList<ItemStack> cells = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(LightItems.CHARGER_BE.get(), pos, state);
    }

    public boolean insert(ItemStack acc) {
        for (int i = 0; i < SLOTS; i++) {
            if (cells.get(i).isEmpty()) {
                cells.set(i, acc);
                changed();
                return true;
            }
        }
        return false;
    }

    public ItemStack takeLast() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (!cells.get(i).isEmpty()) {
                ItemStack s = cells.get(i);
                cells.set(i, ItemStack.EMPTY);
                changed();
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public void dropAll() {
        if (level == null) return;
        for (ItemStack s : cells) if (!s.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5, s);
        cells.clear();
    }

    void serverTick() {
        if (level == null || level.getGameTime() % 20 != Math.floorMod(worldPosition.hashCode(), 20)) return;
        double rpSeconds = RpCoreAPI.bodyScale();
        double perSecond = 1.0 / (LightsConfig.get(LightsConfig.CHARGE_HOURS) * 3600.0);
        double wear = LightsConfig.get(LightsConfig.ACC_WEAR);
        double dead = LightsConfig.get(LightsConfig.ACC_DEAD_CAPACITY);
        boolean charging = false, any = false;
        for (ItemStack s : cells) {
            if (s.isEmpty()) continue;
            any = true;
            Cells.Cell c = Cells.fromItem(s);
            if (c.cap() < dead || c.charge() >= c.cap() - 1.0e-4f) continue;
            charging = true;
            float add = (float) Math.min(c.cap() - c.charge(), rpSeconds * perSecond * c.cap());
            if (add <= 0.0f) continue;
            float cap = (float) Math.max(0.0, c.cap() - wear * add);
            CompoundTag t = s.getOrCreateTag();
            t.putFloat(Cells.CAP, cap);
            t.putFloat(Cells.CHARGE, Math.min(cap, c.charge() + add));
        }
        setChanged();
        int count = 0;
        for (ItemStack s : cells) if (!s.isEmpty()) count++;
        BlockState st = getBlockState();
        boolean lit = any && charging;
        if (st.getValue(ChargerBlock.CELLS) != count || st.getValue(ChargerBlock.CHARGING) != lit) {
            level.setBlock(worldPosition, st.setValue(ChargerBlock.CELLS, count).setValue(ChargerBlock.CHARGING, lit), 3);
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            int count = 0;
            for (ItemStack s : cells) if (!s.isEmpty()) count++;
            BlockState st = getBlockState();
            level.setBlock(worldPosition, st.setValue(ChargerBlock.CELLS, count).setValue(ChargerBlock.CHARGING, count > 0), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, cells);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        cells.clear();
        ContainerHelper.loadAllItems(tag, cells);
    }
}
