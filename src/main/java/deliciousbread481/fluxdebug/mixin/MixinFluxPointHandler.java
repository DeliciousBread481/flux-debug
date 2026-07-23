package deliciousbread481.fluxdebug.mixin;

import deliciousbread481.fluxdebug.DebugLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "sonar.fluxnetworks.common.connection.transfer.FluxPointHandler", remap = false)
public abstract class MixinFluxPointHandler {

    @Inject(method = "onCycleStart", at = @At("HEAD"), remap = false)
    private void fluxdebug$cycleStartHead(CallbackInfo ci) {
        DebugLogger.INSTANCE.onPointCycleStartHead(this);
    }

    @Inject(method = "onCycleStart", at = @At("TAIL"), remap = false)
    private void fluxdebug$cycleStartTail(CallbackInfo ci) {
        DebugLogger.INSTANCE.onPointCycleStartTail(this);
    }
}