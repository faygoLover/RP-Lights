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
 * Керосиновая лампа «Летучая мышь»: светит вокруг, носится в руке или на поясе (слот Curios «пояс»). ПКМ — зажечь /
 * погасить, Shift+ПКМ — фитиль, с ведром керосина во второй руке — заправить. В воде гаснет, дождь ей не страшен.
 * Убрали в инвентарь (не на пояс) — гаснет.
 */
public class LampItem extends Item {
    public LampItem(Properties props) {
        super(props);
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
        if (Lights.isOn(s) && !selected && p.getOffhandItem() != s) Lights.setOn(s, false);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights." + LightType.LAMP.id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("rplights.tip.use." + LightType.LAMP.id).withStyle(ChatFormatting.DARK_GRAY));
    }
}
