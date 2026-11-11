package faygolover.rplights.item;

import faygolover.rplights.light.Cells;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Тестер батареек: ПКМ, во второй руке батарейка, аккумулятор или фонарь — стрелка показывает заряд с точностью до
 * 10 % (у фонаря — каждую батарейку), у аккумулятора ещё и ёмкость, у ручного фонаря — цела ли лампочка.
 */
public class TesterItem extends Item {
    public TesterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(s);
        ItemStack other = p.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        Component msg = read(p, other);
        if (msg == null) {
            LightActions.tell(p, "rplights.tester.nothing");
            return InteractionResultHolder.consume(s);
        }
        p.displayClientMessage(msg, true);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.4f, 1.6f);
        return InteractionResultHolder.consume(s);
    }

    private static Component read(Player p, ItemStack other) {
        if (Cells.isBattery(other)) {
            if (Cells.needsInit(other)) Cells.initBattery(other, p.getRandom(), p.isCreative());
            Cells.Cell c = Cells.fromItem(other);
            if (c.acc()) return Component.translatable("rplights.tester.accumulator", pct(c.charge()), pct(c.cap()));
            return Component.translatable("rplights.tester.battery", pct(c.charge()));
        }
        LightType type = Lights.type(other);
        if (type == null || !type.usesCells()) return null;
        if (Lights.needsInit(other)) Lights.init(other, p.getRandom(), p.isCreative());
        List<Cells.Cell> cells = Cells.read(other);
        MutableComponent out = Component.translatable("rplights.tester.light");
        if (cells.isEmpty()) out.append(" ").append(Component.translatable("rplights.tester.empty"));
        for (int i = 0; i < cells.size(); i++) out.append(" " + (i + 1) + ": " + pct(cells.get(i).charge()) + "%");
        if (type == LightType.HAND_LANTERN) {
            out.append(" · ").append(Component.translatable(Lights.bulbOk(other) ? "rplights.tester.bulb_ok" : "rplights.tester.bulb_open"));
        }
        if (type == LightType.OLD && Lights.wet(other)) out.append(" · ").append(Component.translatable("rplights.tester.wet"));
        return out;
    }

    /** До 10 %, как у стрелочного прибора. */
    private static int pct(float v) {
        return Math.round(Math.max(0.0f, Math.min(1.0f, v)) * 10.0f) * 10;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights.battery_tester.desc").withStyle(ChatFormatting.GRAY));
    }
}
