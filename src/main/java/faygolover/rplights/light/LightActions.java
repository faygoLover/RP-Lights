package faygolover.rplights.light;

import faygolover.rplights.LightsConfig;
import faygolover.rplights.RpLights;
import faygolover.rplights.registry.LightItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Действия игрока с фонарём (сервер): включить, поменять батарейки, лампочку, керосин, режим. */
public final class LightActions {
    private LightActions() {}

    /** Ведро керосина (PneumaticCraft) и подобное — тег {@code rplights:kerosene}. */
    public static final TagKey<Item> KEROSENE = ItemTags.create(new ResourceLocation(RpLights.MODID, "kerosene"));

    private static final ResourceLocation SA_ON_SOUND = new ResourceLocation("sa_combat", "flashlight_on");
    private static final ResourceLocation SA_OFF_SOUND = new ResourceLocation("sa_combat", "flashlight_off");

    // ------------------------------------------------------------------ включение

    /** Щелчок выключателем. Включается не всегда: села батарейка, перегорела лампочка, мокрый, осечка у старого. */
    public static void toggle(Player p, ItemStack s) {
        LightType type = Lights.type(s);
        if (type == null || p.level().isClientSide) return;
        if (Lights.needsInit(s)) Lights.init(s, p.getRandom(), p.isCreative());
        if (type == LightType.LAMP) {
            toggleLamp(p, s);
            return;
        }
        if (Lights.isOn(s)) {
            Lights.setOn(s, false);
            click(p, false);
            return;
        }
        Lights.refresh(s);
        if (!Lights.canLight(s)) {
            click(p, true);
            return;
        }
        if (type == LightType.OLD && p.getRandom().nextDouble() < LightsConfig.get(LightsConfig.OLD_MISFIRE)) {
            click(p, true);
            return;
        }
        Lights.setOn(s, true);
        click(p, true);
    }

    private static void toggleLamp(Player p, ItemStack s) {
        if (Lights.isOn(s)) {
            Lights.setOn(s, false);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 0.9f);
            return;
        }
        if (p.isUnderWater() || !Lights.canLight(s)) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.4f, 1.2f);
            return;
        }
        Lights.setOn(s, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.5f, 1.0f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.15f, 1.6f);
    }

    public static void click(Player p, boolean on) {
        SoundEvent sa = ForgeRegistries.SOUND_EVENTS.getValue(on ? SA_ON_SOUND : SA_OFF_SOUND);
        SoundEvent sound = sa != null ? sa : on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 0.6f, on ? 1.0f : 0.95f);
    }

    // ------------------------------------------------------------------ Shift+ПКМ

    /**
     * Shift+ПКМ фонарём: во второй руке батарейки — поменять, лампочка — вкрутить, керосин — залить; вторая рука пуста —
     * режим (SA: дальний/ближний, RGB: следующий, лампа: фитиль), у фонарей без режимов — вынуть батарейки.
     */
    public static void sneakUse(Player p, InteractionHand hand, ItemStack s) {
        LightType type = Lights.type(s);
        if (type == null || p.level().isClientSide) return;
        if (Lights.needsInit(s)) Lights.init(s, p.getRandom(), p.isCreative());
        ItemStack other = p.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (type.usesCells() && Cells.isBattery(other)) {
            swapCells(p, s, type, other);
        } else if (type == LightType.HAND_LANTERN && other.is(LightItems.BULB.get())) {
            swapBulb(p, s, other);
        } else if (type == LightType.LAMP && other.is(KEROSENE)) {
            refuel(p, s, other, hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        } else if (other.isEmpty()) {
            switch (type) {
                case SA -> {
                    boolean low = !Lights.low(s);
                    s.getOrCreateTag().putBoolean(Lights.LOW, low);
                    click(p, true);
                    tell(p, low ? "rplights.msg.mode_low" : "rplights.msg.mode_high");
                }
                case RGB -> {
                    s.getOrCreateTag().putInt(Lights.MODE, (Lights.mode(s) + 1) % Lights.RGB_MODES);
                    click(p, true);
                }
                case LAMP -> {
                    s.getOrCreateTag().putByte(Lights.WICK, (byte) (Lights.wickLow(s) ? 0 : 1));
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.4f, 1.6f);
                    tell(p, Lights.wickLow(s) ? "rplights.msg.wick_low" : "rplights.msg.wick_full");
                }
                default -> {
                    if (type.usesCells()) ejectCells(p, s);
                }
            }
        }
    }

    /** Поменять батарейки: берутся из второй руки, недостающие — из инвентаря; старые — игроку. */
    public static void swapCells(Player p, ItemStack light, LightType type, ItemStack offered) {
        int need = type.cells;
        List<ItemStack> sources = new ArrayList<>();
        sources.add(offered);
        for (ItemStack s : p.getInventory().items) if (s != offered && Cells.isBattery(s)) sources.add(s);
        int have = 0;
        for (ItemStack s : sources) have += s.getCount();
        if (have < need) {
            tell(p, "rplights.msg.need_cells", need);
            return;
        }
        List<Cells.Cell> fresh = new ArrayList<>(need);
        for (ItemStack s : sources) {
            while (fresh.size() < need && !s.isEmpty()) {
                if (Cells.needsInit(s)) Cells.initBattery(s, p.getRandom(), p.isCreative());
                fresh.add(Cells.fromItem(s));
                if (!p.isCreative()) s.shrink(1);
                else break;
            }
            if (fresh.size() >= need) break;
        }
        while (fresh.size() < need) fresh.add(fresh.get(0));
        List<Cells.Cell> old = Cells.read(light);
        Cells.write(light, fresh);
        Lights.setOn(light, false);
        Lights.refresh(light);
        giveCells(p, old);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    public static void ejectCells(Player p, ItemStack light) {
        List<Cells.Cell> old = Cells.read(light);
        if (old.isEmpty()) {
            tell(p, "rplights.msg.no_cells");
            return;
        }
        Cells.write(light, List.of());
        Lights.setOn(light, false);
        Lights.refresh(light);
        giveCells(p, old);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.5f, 1.4f);
    }

    public static void giveCells(Player p, List<Cells.Cell> cells) {
        for (Cells.Cell c : cells) give(p, Cells.toItem(c));
    }

    public static void swapBulb(Player p, ItemStack light, ItemStack bulb) {
        boolean wasOk = Lights.bulbOk(light);
        light.getOrCreateTag().putByte(Lights.BULB, (byte) 1);
        Lights.setOn(light, false);
        Lights.refresh(light);
        if (!p.isCreative()) bulb.shrink(1);
        give(p, new ItemStack(wasOk ? LightItems.BULB.get() : LightItems.BURNT_BULB.get()));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    public static void refuel(Player p, ItemStack lamp, ItemStack bucket, InteractionHand bucketHand) {
        lamp.getOrCreateTag().putFloat(Lights.FUEL, Lights.FUEL_MAX);
        Lights.refresh(lamp);
        if (!p.isCreative()) {
            bucket.shrink(1);
            ItemStack empty = new ItemStack(Items.BUCKET);
            if (p.getItemInHand(bucketHand).isEmpty()) p.setItemInHand(bucketHand, empty);
            else give(p, empty);
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    public static void give(Player p, ItemStack s) {
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    public static void tell(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }
}
