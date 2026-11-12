package faygolover.rplights.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import faygolover.rplights.RpLights;
import faygolover.rplights.compat.CuriosCompat;
import faygolover.rplights.light.Glow;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ModelEvent;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Надетое на игроке (Curios): налобный — ремешок вокруг головы и лампа на лбу; на груди — лямки и лампа; «Летучая
 * мышь» — висит на поясе справа спереди и чуть качается. Горит ли лампа на модели — по тому же {@link Glow}, что и луч.
 * Модели — {@code models/item/headlamp_worn_*} (сетка головы / тела игрока, см. tools/gen_models.py).
 */
public final class WornRenderer implements ICurioRenderer {
    public static final ResourceLocation HEAD = rl("headlamp_worn_head"), HEAD_ON = rl("headlamp_worn_head_on"),
            CHEST = rl("headlamp_worn_chest"), CHEST_ON = rl("headlamp_worn_chest_on");

    private static ResourceLocation rl(String name) {
        return new ResourceLocation(RpLights.MODID, "item/" + name);
    }

    public static void registerModels(ModelEvent.RegisterAdditional e) {
        e.register(HEAD);
        e.register(HEAD_ON);
        e.register(CHEST);
        e.register(CHEST_ON);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot, PoseStack ps,
            RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
            float partial, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> hm)) return;
        LivingEntity e = slot.entity();
        LightType type = Lights.type(stack);
        if (type == null) return;
        String id = slot.identifier();
        Minecraft mc = Minecraft.getInstance();
        long tick = e.level().getGameTime();
        ps.pushPose();
        if (type == LightType.HEADLAMP && (CuriosCompat.HEAD.equals(id) || CuriosCompat.BODY.equals(id))) {
            boolean head = CuriosCompat.HEAD.equals(id);
            boolean on = Glow.of(stack, type, tick, e.getId() * 31 + (head ? 2 : 3)) != null;
            (head ? hm.head : hm.body).translateAndRotate(ps);
            ps.mulPose(Axis.ZP.rotationDegrees(180.0f));
            ps.translate(-0.5, -0.5, -0.5);
            BakedModel model = mc.getModelManager().getModel(head ? (on ? HEAD_ON : HEAD) : (on ? CHEST_ON : CHEST));
            mc.getItemRenderer().renderModelLists(model, stack, light, OverlayTexture.NO_OVERLAY, ps,
                    buffers.getBuffer(Sheets.cutoutBlockSheet()));
        } else if (type == LightType.LAMP && CuriosCompat.BELT.equals(id)) {
            hm.body.translateAndRotate(ps);
            // у правого бедра чуть спереди: ось тела — +Y вниз, −Z вперёд, правая сторона — −X (пиксели / 16)
            ps.translate(-3.5 / 16.0, 10.5 / 16.0, -3.2 / 16.0);
            float sway = Mth.sin(limbSwing * 0.6662f) * limbSwingAmount * 18.0f;
            ps.mulPose(Axis.XP.rotationDegrees(sway - hm.body.xRot * Mth.RAD_TO_DEG));
            ps.mulPose(Axis.ZP.rotationDegrees(180.0f));
            ps.scale(0.5f, 0.5f, 0.5f);
            ps.translate(-0.5, -11.5 / 16.0, -0.5);
            BakedModel model = mc.getItemRenderer().getModel(stack, e.level(), e, e.getId());
            mc.getItemRenderer().renderModelLists(model, stack, light, OverlayTexture.NO_OVERLAY, ps,
                    buffers.getBuffer(Sheets.translucentItemSheet()));
        }
        ps.popPose();
    }
}
