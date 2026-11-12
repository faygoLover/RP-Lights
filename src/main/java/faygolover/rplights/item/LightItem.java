package faygolover.rplights.item;

import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Фонарь на батарейках: ПКМ — щелчок выключателем, Shift+ПКМ — батарейки / лампочка / режим ({@link LightActions}).
 * Из рук убрали — выключается (налобный в слоте Curios светит дальше). Заряд не показывается.
 */
public class LightItem extends Item {
    private final LightType type;

    public LightItem(LightType type, Properties props) {
        super(props);
        this.type = type;
    }

    public LightType type() {
        return type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) LightActions.sneakUse(player, hand, s);
            else LightActions.toggle(player, s);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
        if (level.isClientSide || !(e instanceof Player p)) return;
        if (Lights.needsInit(s)) Lights.init(s, p.getRandom(), p.isCreative());
        if (Lights.isOn(s) && !selected && Lights.inBackpack(p, s, slot)) {
            Lights.setOn(s, false);
            LightActions.click(p, false);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights." + type.id + ".desc").withStyle(ChatFormatting.GRAY));
        if (type.usesCells()) tip.add(Component.translatable("rplights.tip.cells", type.cells).withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("rplights.tip.use." + type.id).withStyle(ChatFormatting.DARK_GRAY));
    }
}
