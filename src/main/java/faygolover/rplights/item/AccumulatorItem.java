package faygolover.rplights.item;

import faygolover.rplights.LightsConfig;
import faygolover.rplights.light.Cells;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Аккумулятор: 70 % обычной батарейки, заряжается в зарядном устройстве, изнашивается. Вздувшийся видно сразу. */
public class AccumulatorItem extends Item {
    public AccumulatorItem(Properties props) {
        super(props);
    }

    public static boolean swollen(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains(Cells.CAP) && t.getFloat(Cells.CAP) < LightsConfig.get(LightsConfig.ACC_DEAD_CAPACITY);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights.accumulator.desc").withStyle(ChatFormatting.GRAY));
        if (swollen(s)) tip.add(Component.translatable("rplights.tip.swollen").withStyle(ChatFormatting.RED));
    }
}
