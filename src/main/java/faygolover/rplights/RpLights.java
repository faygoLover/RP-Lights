package faygolover.rplights;

import com.mojang.logging.LogUtils;
import faygolover.rplights.client.LightsClient;
import faygolover.rplights.net.LightsNet;
import faygolover.rplights.registry.LightItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;

/**
 * RP Lights — фонари и лампы семейства RP: ручной фонарь с лампой накаливания, старый хромированный, налобный /
 * нагрудный, динамо, керосиновая «Летучая мышь», УФ и RGB; фонарь Survivor's Arsenal — «отличный», на тех же
 * батарейках. У каждой батарейки свой скрытый заряд, аккумуляторы и зарядка, тестер. Расход — по RP-времени (Core).
 * Свет — настоящий через FL Render ({@code LightBeams}) и свой экранный проход (с шейдерпаком). План — docs/00_plan.md.
 */
@Mod(RpLights.MODID)
public final class RpLights {
    public static final String MODID = "rplights";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpLights() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        LightItems.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, LightsConfig.SPEC);
        bus.addListener(this::setup);
        // Общий вопрос «несёт включённый свет» (RP Psyche — полная тьма, Zone Artifacts — паутина) — через RP Core.
        faygolover.rpcore.api.RpCoreAPI.registerLight(new net.minecraft.resources.ResourceLocation(MODID, "lights"),
                faygolover.rplights.light.Lights::carried);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LightsClient.init(bus));
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(LightsNet::register);
    }
}
