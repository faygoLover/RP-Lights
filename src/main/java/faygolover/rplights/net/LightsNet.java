package faygolover.rplights.net;

import faygolover.rplights.RpLights;
import faygolover.rplights.compat.CuriosCompat;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.Lights;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Клавиша фонаря (по умолчанию L): включить / выключить надетый — налобный, нагрудный, иначе лампу на поясе. */
public final class LightsNet {
    private LightsNet() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(RpLights.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ToggleWorn.class, (m, b) -> {}, b -> new ToggleWorn(), ToggleWorn::handle);
    }

    public record ToggleWorn() {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p == null) return;
                for (String slot : new String[]{CuriosCompat.HEAD, CuriosCompat.BODY, CuriosCompat.BELT}) {
                    ItemStack s = CuriosCompat.worn(p, slot);
                    if (Lights.type(s) != null) {
                        LightActions.toggle(p, s);
                        return;
                    }
                }
                LightActions.tell(p, "rplights.msg.nothing_worn");
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
