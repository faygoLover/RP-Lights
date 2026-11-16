package faygolover.rplights.server;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rplights.LightsConfig;
import faygolover.rplights.RpLights;
import faygolover.rplights.compat.CuriosCompat;
import faygolover.rplights.light.Cells;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import faygolover.rplights.recipe.LightRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Сервер. Раз в секунду — свет в руках и в слотах Curios: расход батареек / керосина по RP-времени (на паузе RP-времени
 * не тратится), перегорание лампочки, вода и дождь у старого фонарика, динамо гаснет по запасу. Раз в полсекунды — всё,
 * что игрок видит (инвентарь и открытый ящик): найденным батарейкам и фонарям — их заряд, старый фонарик сохнет.
 * Ещё: Shift+ПКМ фонарём SA (батарейки, ближний свет), падение с ручным фонарём, возврат вынутого при крафте.
 */
@Mod.EventBusSubscriber(modid = RpLights.MODID)
public final class LightTicker {
    private LightTicker() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        Player p = e.player;
        int phase = Math.floorMod(p.getId(), 20);
        int t = p.tickCount;
        if (t % 10 == phase % 10) scan(p, 0.5);
        if (t % 20 == phase) {
            double rp = RpCoreAPI.bodyScale();
            light(p, p.getMainHandItem(), rp);
            light(p, p.getOffhandItem(), rp);
            light(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.HEAD), rp);
            light(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.BODY), rp);
            light(p, faygolover.rpcore.api.Worn.first(p, CuriosCompat.BELT), rp);
        }
    }

    /** Найденное — получает заряд; мокрый старый фонарик сохнет ({@code realSeconds} реального времени). */
    private static void scan(Player p, double realSeconds) {
        boolean creative = p.isCreative();
        boolean dryAir = !p.isInWaterOrBubble() && !rainOn(p);
        double rpHours = RpCoreAPI.bodyScale() * realSeconds / 3600.0;
        for (Slot slot : p.containerMenu.slots) {
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            if (Cells.isBattery(s)) {
                if (Cells.needsInit(s)) Cells.initBattery(s, p.getRandom(), creative);
                continue;
            }
            LightType type = Lights.type(s);
            if (type == null) continue;
            if (Lights.needsInit(s)) Lights.init(s, p.getRandom(), creative);
            if (type == LightType.OLD && dryAir && Lights.wet(s) && rpHours > 0.0) {
                CompoundTag tag = s.getOrCreateTag();
                tag.putFloat(Lights.WET, (float) Math.max(0.0, tag.getFloat(Lights.WET) - rpHours));
            }
        }
    }

    private static boolean rainOn(Player p) {
        BlockPos head = BlockPos.containing(p.getEyePosition());
        return p.level().isRainingAt(head) || p.level().isRainingAt(head.above());
    }

    /** Секунда работы фонаря. */
    private static void light(Player p, ItemStack s, double rpSeconds) {
        if (s.isEmpty()) return;
        LightType type = Lights.type(s);
        if (type == null) return;
        if (Lights.needsInit(s)) Lights.init(s, p.getRandom(), p.isCreative());
        CompoundTag tag = s.getOrCreateTag();
        switch (type) {
            case DYNAMO -> {
                boolean on = Lights.dynamoUntil(s) > p.level().getGameTime();
                if (tag.getBoolean(Lights.ON) != on) tag.putBoolean(Lights.ON, on);
            }
            case LAMP -> {
                if (!Lights.isOn(s)) return;
                if (p.isUnderWater()) {
                    Lights.setOn(s, false);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.4f);
                    return;
                }
                if (Lights.burnLamp(s, rpSeconds)) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 0.8f);
            }
            default -> cells(p, s, type, rpSeconds);
        }
    }

    private static void cells(Player p, ItemStack s, LightType type, double rpSeconds) {
        if (!Lights.isOn(s)) {
            // SA сам немного тратит свой заряд и в выключенном виде его не трогаем; сверяем только после замены.
            return;
        }
        double hours = Lights.hours(s, type);
        if (rpSeconds > 0.0 && hours > 0.0) {
            float amount = (float) (rpSeconds / (hours * 3600.0));
            Cells.write(s, Cells.drain(Cells.read(s), amount));
        }
        double rpHours = rpSeconds / 3600.0;
        if (type == LightType.HAND_LANTERN && rpHours > 0.0) {
            double perHour = LightsConfig.get(LightsConfig.BULB_BURN_PER_HOUR);
            if (p.getRandom().nextDouble() < 1.0 - Math.pow(1.0 - perHour, rpHours)) burnBulb(p, s);
        }
        if (type == LightType.OLD) {
            boolean soak = p.isUnderWater();
            if (!soak && rainOn(p) && rpSeconds > 0.0) {
                double perMinute = LightsConfig.get(LightsConfig.OLD_RAIN_PER_MINUTE);
                soak = p.getRandom().nextDouble() < 1.0 - Math.pow(1.0 - perMinute, rpSeconds / 60.0);
            }
            if (soak) soak(p, s);
        }
        boolean was = Lights.isOn(s);
        Lights.refresh(s);
        if (was && !Lights.isOn(s)) LightActions.click(p, false);
    }

    /** Лампочка перегорела: щелчок стекла, фонарь гаснет. */
    public static void burnBulb(Player p, ItemStack s) {
        s.getOrCreateTag().putByte(Lights.BULB, (byte) 0);
        Lights.setOn(s, false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.35f, 2.0f);
    }

    /** Старый фонарик намок: замкнуло, батарейки сели (протекли) — сохнуть и менять батарейки. */
    private static void soak(Player p, ItemStack s) {
        s.getOrCreateTag().putFloat(Lights.WET, (float) LightsConfig.get(LightsConfig.OLD_DRY_HOURS));
        List<Cells.Cell> dead = Cells.read(s).stream().map(c -> c.withCharge(0.0f)).toList();
        Cells.write(s, dead);
        Lights.setOn(s, false);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25f, 2.0f);
    }

    // ------------------------------------------------------------------ события

    /** Shift+ПКМ фонарём SA — наши батарейки и ближний/дальний свет вместо его щелчка. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        ItemStack s = e.getItemStack();
        if (!Lights.isSa(s) || !e.getEntity().isShiftKeyDown()) return;
        if (!e.getLevel().isClientSide) LightActions.sneakUse(e.getEntity(), e.getHand(), s);
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(e.getLevel().isClientSide));
    }

    /** Упал с высоты с ручным фонарём — лампочка может перегореть. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.level().isClientSide || e.getDistance() <= 4.0f) return;
        double chance = LightsConfig.get(LightsConfig.BULB_BURN_ON_FALL);
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(hand);
            if (Lights.type(s) == LightType.HAND_LANTERN && Lights.bulbOk(s) && p.getRandom().nextDouble() < chance) burnBulb(p, s);
        }
    }

    /** Забрал фонарь из верстака — вынутые батарейки или старая лампочка в инвентарь. */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity().level().isClientSide || Lights.type(e.getCrafting()) == null) return;
        LightRecipe.Parts parts = LightRecipe.parse(e.getInventory());
        if (parts == null) return;
        for (ItemStack s : LightRecipe.returned(parts)) LightActions.give(e.getEntity(), s);
    }
}
