package faygolover.rplights.ink;

import faygolover.rplights.RpLights;
import faygolover.rplights.net.LightsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Сервер: чернила чанка — игроку, который его увидел; сломанный / взорванный блок теряет свои чернила. */
@Mod.EventBusSubscriber(modid = RpLights.MODID)
public final class InkEvents {
    private InkEvents() {}

    @SubscribeEvent
    public static void onWatch(ChunkWatchEvent.Watch e) {
        int[] dots = InkData.get(e.getLevel()).chunk(e.getPos().toLong());
        if (dots != null) {
            LightsNet.CHANNEL.send(PacketDistributor.PLAYER.with(e::getPlayer), new LightsNet.InkDelta(e.getPos().toLong(), dots, null));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel level) InkData.get(level).clearBlock(level, e.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        InkData d = InkData.get(level);
        for (BlockPos pos : e.getAffectedBlocks()) d.clearBlock(level, pos);
    }
}
