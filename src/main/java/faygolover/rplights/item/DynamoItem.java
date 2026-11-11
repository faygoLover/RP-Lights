package faygolover.rplights.item;

import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpcore.api.StaminaProvider;
import faygolover.rplights.LightsConfig;
import faygolover.rplights.RpLights;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Динамо-фонарик «жучок»: каждый жим (ПКМ) добавляет несколько секунд света, запас ограничен; яркость — по запасу,
 * к концу желтеет. Жим тратит немного выносливости RP Stamina. Батареек нет, ломаться нечему.
 */
public class DynamoItem extends Item {
    private static final ResourceLocation SOURCE = new ResourceLocation(RpLights.MODID, "dynamo");
    /** Жимов в секунду не больше трёх. */
    private static final int COOLDOWN = 6;

    public DynamoItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        p.getCooldowns().addCooldown(this, COOLDOWN);
        if (level.isClientSide) {
            DynamoSqueeze.squeezed();
        } else {
            long now = level.getGameTime();
            long until = Math.max(now, Lights.dynamoUntil(s)) + LightsConfig.get(LightsConfig.DYNAMO_SQUEEZE_TICKS);
            until = Math.min(until, now + LightsConfig.get(LightsConfig.DYNAMO_MAX_TICKS));
            s.getOrCreateTag().putLong(Lights.DYN, until);
            s.getOrCreateTag().putBoolean(Lights.ON, true);
            float rem = (until - now) / (float) LightsConfig.get(LightsConfig.DYNAMO_MAX_TICKS);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundSource.PLAYERS,
                    0.35f, 1.3f + rem * 0.6f);
            StaminaProvider st = RpCoreAPI.stamina();
            double cost = LightsConfig.get(LightsConfig.DYNAMO_STAMINA);
            if (st != null && cost > 0.0 && !p.isCreative()) st.spend(p, (float) cost, SOURCE);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** Своя рука от первого лица чуть дёргается при жиме. */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                                                   net.minecraft.world.entity.HumanoidArm arm, ItemStack stack, float partial,
                                                   float equip, float swing) {
                float a = DynamoSqueeze.amount();
                if (a > 0.0f) {
                    float side = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0f : -1.0f;
                    pose.translate(side * 0.012f * a, -0.035f * a, 0.02f * a);
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 4.0f * a));
                }
                return false;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.rplights." + LightType.DYNAMO.id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("rplights.tip.use." + LightType.DYNAMO.id).withStyle(ChatFormatting.DARK_GRAY));
    }
}
