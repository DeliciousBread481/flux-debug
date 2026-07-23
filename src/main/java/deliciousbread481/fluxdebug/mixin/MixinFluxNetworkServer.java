package deliciousbread481.fluxdebug.mixin;

import deliciousbread481.fluxdebug.DebugLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "sonar.fluxnetworks.common.connection.FluxNetworkServer", remap = false)
public class MixinFluxNetworkServer {

    @Inject(method = "onEndServerTick", at = @At("TAIL"), remap = false)
    private void fluxdebug$tail(CallbackInfo ci) {
        DebugLogger.INSTANCE.onNetworkTick(this, Thread.currentThread().getName());
    }
}