package deliciousbread481.fluxdebug;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = FluxDebug.MODID, name = "Flux Debug", version = "1.1.0",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "after:fluxnetworks")
public class FluxDebug {

    public static final String MODID = "fluxdebug";

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChunkWatcher());
        DebugLogger.INSTANCE.line("BOOT", "-", "-", "FluxDebug started, watching Flux Networks state.");
    }
}