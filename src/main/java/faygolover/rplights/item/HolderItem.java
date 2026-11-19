package faygolover.rplights.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Пенал для маркеров и кольцо для ХИС: хранит только подходящие предметы (по правилу {@code accepts}), всего не больше
 * {@code capacity} штук. Работает как мешок-bundle: взять пенал курсором и нажать ПКМ по предмету в инвентаре — предмет
 * уйдёт внутрь; ПКМ пеналом по пустой клетке — вынуть последний; ПКМ по пеналу предметом на курсоре — убрать внутрь,
 * пустым курсором — вынуть. ПКМ пеналом в руке — достать последний предмет во вторую руку (или в инвентарь).
 * Содержимое — NBT {@code Held} (список стопок), у каждой стопки сохраняются её данные (расход чернил маркера).
 */
public class HolderItem extends Item {
    public static final String KEY = "Held";

    private final int capacity;
    private final Predicate<ItemStack> accepts;
    private final String id;

    public HolderItem(Properties props, String id, int capacity, Predicate<ItemStack> accepts) {
        super(props);
        this.id = id;
        this.capacity = capacity;
        this.accepts = accepts;
    }

    public int capacity() {
        return capacity;
    }

    public boolean accepts(ItemStack s) {
        return !s.isEmpty() && !(s.getItem() instanceof HolderItem) && accepts.test(s);
    }

    // ---------------------------------------------------------------------------------------------- содержимое

    public static List<ItemStack> contents(ItemStack holder) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag tag = holder.getTag();
        if (tag == null || !tag.contains(KEY, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.of(list.getCompound(i));
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void write(ItemStack holder, List<ItemStack> list) {
        if (list.isEmpty()) {
            holder.removeTagKey(KEY);
            return;
        }
        ListTag tags = new ListTag();
        for (ItemStack s : list) tags.add(s.save(new CompoundTag()));
        holder.getOrCreateTag().put(KEY, tags);
    }

    public static int total(ItemStack holder) {
        int n = 0;
        for (ItemStack s : contents(holder)) n += s.getCount();
        return n;
    }

    /** Кладёт в хранилище сколько влезет; возвращает, сколько штук забрано (стопка {@code in} при этом не меняется). */
    public int insert(ItemStack holder, ItemStack in) {
        if (!accepts(in)) return 0;
        int n = Math.min(capacity - total(holder), in.getCount());
        if (n <= 0) return 0;
        List<ItemStack> list = contents(holder);
        int left = n;
        for (ItemStack s : list) {
            if (left > 0 && ItemStack.isSameItemSameTags(s, in)) {
                int m = Math.min(left, s.getMaxStackSize() - s.getCount());
                if (m > 0) {
                    s.grow(m);
                    left -= m;
                }
            }
        }
        while (left > 0) {
            int m = Math.min(left, in.getMaxStackSize());
            list.add(in.copyWithCount(m));
            left -= m;
        }
        write(holder, list);
        return n;
    }

    /** Достаёт одну штуку последней стопки. */
    public static Optional<ItemStack> removeOne(ItemStack holder) {
        List<ItemStack> list = contents(holder);
        if (list.isEmpty()) return Optional.empty();
        ItemStack last = list.get(list.size() - 1);
        ItemStack one = last.split(1);
        if (last.isEmpty()) list.remove(list.size() - 1);
        write(holder, list);
        return Optional.of(one);
    }

    // ---------------------------------------------------------------------------------------------- клики в инвентаре

    @Override
    public boolean overrideStackedOnOther(ItemStack holder, Slot slot, ClickAction action, Player player) {
        if (holder.getCount() != 1 || action != ClickAction.SECONDARY) return false;
        ItemStack in = slot.getItem();
        if (in.isEmpty()) {
            removeOne(holder).ifPresent(out -> {
                ItemStack rest = slot.safeInsert(out);
                if (!rest.isEmpty()) insert(holder, rest);
            });
            return true;
        }
        if (!accepts(in)) return false;
        int room = capacity - total(holder);
        if (room > 0) {
            ItemStack taken = slot.safeTake(in.getCount(), room, player);
            insert(holder, taken);
        }
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack holder, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (holder.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (other.isEmpty()) {
            removeOne(holder).ifPresent(access::set);
            return true;
        }
        if (!accepts(other)) return false;
        int n = insert(holder, other);
        if (n > 0) other.shrink(n);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack holder = player.getItemInHand(hand);
        if (holder.getCount() != 1) return InteractionResultHolder.pass(holder);
        Optional<ItemStack> one = removeOne(holder);
        if (one.isEmpty()) return InteractionResultHolder.fail(holder);
        if (!level.isClientSide) {
            ItemStack out = one.get();
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (player.getItemInHand(other).isEmpty()) {
                player.setItemInHand(other, out);
            } else if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
        return InteractionResultHolder.sidedSuccess(holder, level.isClientSide);
    }

    // ---------------------------------------------------------------------------------------------- вид

    @Override
    public boolean isBarVisible(ItemStack s) {
        return total(s) > 0;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.min(13, 1 + 12 * total(s) / capacity);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return total(s) >= capacity ? 0xFF7A5A : 0x6EA8FF;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights." + id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("rplights.holder.fill", total(s), capacity).withStyle(ChatFormatting.AQUA));
        for (ItemStack c : contents(s)) {
            tip.add(Component.literal(" ").append(c.getHoverName()).append(c.getCount() > 1 ? " ×" + c.getCount() : "")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.translatable("rplights.holder.how").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        ItemUtils.onContainerDestroyed(entity, contents(entity.getItem()).stream());
    }
}
