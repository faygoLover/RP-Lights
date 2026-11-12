package faygolover.rplights.net;

import faygolover.rplights.RpLights;
import faygolover.rplights.compat.CuriosCompat;
import faygolover.rplights.light.LightActions;
import faygolover.rplights.light.Lights;
import faygolover.rplights.ink.InkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Сеть: клавиша фонаря (по умолчанию L) — включить / выключить надетый (налобный, нагрудный, иначе лампу на поясе);
 * чернила чанков — клиенту ({@link InkDelta}).
 */
public final class LightsNet {
    private LightsNet() {}

    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(RpLights.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ToggleWorn.class, (m, b) -> {}, b -> new ToggleWorn(), ToggleWorn::handle);
        CHANNEL.registerMessage(1, InkDelta.class, InkDelta::write, InkDelta::read, InkDelta::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Чернила чанка: {@code remove == null} — весь чанк заново (= add), иначе добавить и стереть точки. */
    public record InkDelta(long chunk, int[] add, int[] remove) {
        void write(FriendlyByteBuf b) {
            b.writeLong(chunk);
            b.writeVarIntArray(add);
            b.writeBoolean(remove != null);
            if (remove != null) b.writeVarIntArray(remove);
        }

        static InkDelta read(FriendlyByteBuf b) {
            long c = b.readLong();
            int[] add = b.readVarIntArray(InkData.MAX_PER_CHUNK);
            int[] remove = b.readBoolean() ? b.readVarIntArray(InkData.MAX_PER_CHUNK) : null;
            return new InkDelta(c, add, remove);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> faygolover.rplights.client.InkClient.apply(chunk, add, remove));
            ctx.get().setPacketHandled(true);
        }
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
