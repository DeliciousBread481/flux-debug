package deliciousbread481.fluxdebug.mixin;

import deliciousbread481.fluxdebug.DebugLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "sonar.fluxnetworks.common.connection.FluxNetworkServer", remap = false)
public class MixinFluxNetworkServer {

    @Inject(method = "onEndServerTick", at = @At("TAIL"), remap = false)
    private void fluxdebug$onEndTick(CallbackInfo ci) {
        DebugLogger log = DebugLogger.INSTANCE;

        Object netId = log.invoke(this, "getNetworkID");
        String nid = netId == null ? "?" : String.valueOf(netId);

        int plugs = log.sizeOf(log.getField(this, "sortedPlugs"));
        int points = log.sizeOf(log.getField(this, "sortedPoints"));
        long limiter = log.asLong(log.getField(this, "bufferLimiter"));

        boolean cycleRuns = plugs > 0 && points > 0;

        log.logIfChanged("net" + nid + "/sortedPlugs", String.valueOf(plugs),
                plugs == 0 ? "PLUG_LIST_EMPTY：主世界侧塞子未登记，CYCLE 跳过，传输归零" : "");
        log.logIfChanged("net" + nid + "/sortedPoints", String.valueOf(points),
                points == 0 ? "POINT_LIST_EMPTY：目标维度侧点未登记" : "");
        log.logIfChanged("net" + nid + "/cycleRuns", String.valueOf(cycleRuns),
                cycleRuns ? "CYCLE 执行" : "CYCLE 跳过（列表为空）");
        log.logIfChanged("net" + nid + "/bufferLimiter", String.valueOf(limiter),
                limiter <= 0 ? "bufferLimiter<=0：Plug 会拒收发电机推电" : "");

        if (log.isSnapshotTick()) {
            log.line("SNAPSHOT", "net" + nid,
                    "plugs=" + plugs + " points=" + points + " limiter=" + limiter + " cycle=" + cycleRuns, "周期性全量快照");
        }
    }
}