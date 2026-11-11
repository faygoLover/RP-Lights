package faygolover.rplights.client;

import com.mojang.blaze3d.platform.InputConstants;
import faygolover.rplights.RpLights;
import faygolover.rplights.light.Glow;
import faygolover.rplights.light.LightType;
import faygolover.rplights.light.Lights;
import faygolover.rplights.net.LightsNet;
import faygolover.rplights.registry.LightItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/** Клиент: модель «горит», клавиша надетого фонаря, лучи для FL Render, экранный проход, подсказка фонаря SA. */
public final class LightsClient {
    private LightsClient() {}

    public static final KeyMapping TOGGLE_WORN = new KeyMapping("key.rplights.toggle_worn", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, "key.categories.rplights");
    private static final ResourceLocation ON = new ResourceLocation(RpLights.MODID, "on");
    /** Строки подсказки SA про заряд — заряд на глаз не виден. */
    private static final Set<String> SA_HIDDEN = Set.of("tooltip.sa_combat.flashlight.battery", "tooltip.sa_combat.flashlight.recharge");

    public static void init(IEventBus bus) {
        bus.addListener(LightsClient::setup);
        bus.addListener(LightsClient::keys);
        MinecraftForge.EVENT_BUS.addListener(ScreenFlash::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(LightsClient::tick);
        MinecraftForge.EVENT_BUS.addListener(LightsClient::tooltip);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ScreenFlash.dispose());
    }

    private static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            for (RegistryObject<Item> ro : LightItems.ITEMS.getEntries()) {
                Item item = ro.get();
                if (Lights.type(new ItemStack(item)) == null) continue;
                ItemProperties.register(item, ON, (stack, level, entity, seed) -> {
                    LightType type = Lights.type(stack);
                    if (type == null) return 0.0f;
                    var lvl = level != null ? level : Minecraft.getInstance().level;
                    long tick = lvl == null ? 0L : lvl.getGameTime();
                    return Glow.of(stack, type, tick, entity == null ? 0 : entity.getId() * 31) != null ? 1.0f : 0.0f;
                });
            }
            ClientBeams.register();
        });
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        e.register(TOGGLE_WORN);
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        while (TOGGLE_WORN.consumeClick()) {
            if (Minecraft.getInstance().player != null) LightsNet.CHANNEL.sendToServer(new LightsNet.ToggleWorn());
        }
    }

    private static void tooltip(ItemTooltipEvent e) {
        if (!Lights.isSa(e.getItemStack())) return;
        e.getToolTip().removeIf(c -> c.getContents() instanceof TranslatableContents t && SA_HIDDEN.contains(t.getKey()));
        e.getToolTip().add(Component.translatable("rplights.tip.cells", 1).withStyle(ChatFormatting.DARK_GRAY));
        e.getToolTip().add(Component.translatable("rplights.tip.use.sa").withStyle(ChatFormatting.DARK_GRAY));
    }
}
