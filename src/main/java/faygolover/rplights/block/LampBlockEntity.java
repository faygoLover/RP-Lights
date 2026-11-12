package faygolover.rplights.block;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rplights.light.Lights;
import faygolover.rplights.registry.LightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Поставленная лампа: сама лампа (предмет со всем NBT), раз в секунду горит керосин, блок светит по ней. */
public class LampBlockEntity extends BlockEntity {
    private ItemStack lamp = ItemStack.EMPTY;

    public LampBlockEntity(BlockPos pos, BlockState state) {
        super(LightItems.LAMP_BE.get(), pos, state);
    }

    /** Лампа (живой предмет: менять можно, потом {@link #changed}). Пусто — только что взяли. */
    public ItemStack lamp() {
        return lamp;
    }

    public void put(ItemStack s) {
        lamp = s.copyWithCount(1);
        changed();
    }

    public void clear() {
        lamp = ItemStack.EMPTY;
        setChanged();
    }

    /** Блок по лампе: горит ли, фитиль. */
    public void changed() {
        setChanged();
        if (level == null || level.isClientSide || lamp.isEmpty()) return;
        BlockState s = getBlockState();
        if (!s.hasProperty(LampBlock.LIT)) return;
        boolean lit = Lights.isOn(lamp) && level.getFluidState(worldPosition).isEmpty();
        if (!lit && Lights.isOn(lamp)) Lights.setOn(lamp, false);
        BlockState n = s.setValue(LampBlock.LIT, lit).setValue(LampBlock.LOW, Lights.wickLow(lamp));
        if (n != s) level.setBlock(worldPosition, n, 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState s, LampBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20L) != 0L || be.lamp.isEmpty()) return;
        if (Lights.needsInit(be.lamp)) Lights.init(be.lamp, level.random, false);
        if (!Lights.isOn(be.lamp)) {
            if (s.getValue(LampBlock.LIT)) be.changed();
            return;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            Lights.setOn(be.lamp, false);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.4f);
        } else if (Lights.burnLamp(be.lamp, RpCoreAPI.bodyScale())) {
            level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 0.8f);
        }
        be.changed();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lamp = tag.contains("Lamp") ? ItemStack.of(tag.getCompound("Lamp")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!lamp.isEmpty()) tag.put("Lamp", lamp.save(new CompoundTag()));
    }
}
