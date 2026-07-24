package deliciousbread481.fluxdebug;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;

public class ChunkWatcher {

    private static final String FLUX_PKG = "sonar.fluxnetworks.common.tileentity.";

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DebugLogger.INSTANCE.tick();

            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                return;
            }
            WorldServer ow = server.getWorld(0);
            if (ow != null) {
                DebugLogger.INSTANCE.logWorldTime(0, ow.getTotalWorldTime(), ow.getWorldTime());
            }
            DebugLogger.INSTANCE.pollFlux();
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        report(event, "CHUNK_LOAD");
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        report(event, "CHUNK_UNLOAD");
    }

    private void report(ChunkEvent event, String kind) {
        if (event.getWorld() == null || event.getWorld().isRemote) {
            return;
        }
        int dim = event.getWorld().provider.getDimension();
        for (Map.Entry<BlockPos, TileEntity> e : event.getChunk().getTileEntityMap().entrySet()) {
            TileEntity te = e.getValue();
            if (te != null && te.getClass().getName().startsWith(FLUX_PKG)) {
                BlockPos p = e.getKey();
                String key = "flux@dim" + dim + "/" + p.getX() + "," + p.getY() + "," + p.getZ();
                DebugLogger.INSTANCE.line("EVENT", kind, key,
                        te.getClass().getSimpleName() + " chunk=[" + event.getChunk().x + "," + event.getChunk().z + "]");
            }
        }
    }
}