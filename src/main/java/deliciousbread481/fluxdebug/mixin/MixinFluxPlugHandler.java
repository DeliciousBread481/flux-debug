package deliciousbread481.fluxdebug.mixin;

import deliciousbread481.fluxdebug.DebugLogger;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "sonar.fluxnetworks.common.connection.transfer.FluxPlugHandler", remap = false)
public class MixinFluxPlugHandler {

    @Inject(method = "receiveFromSupplier", at = @At("RETURN"), remap = false)
    private void fluxdebug$onReceive(long amount, EnumFacing side, boolean simulate,CallbackInfoReturnable<Long> cir) {
        if (simulate) {
            return;
        }
        DebugLogger log = DebugLogger.INSTANCE;

        long returned = cir.getReturnValueJ();
        long buffer = log.asLong(log.getField(this, "buffer"));

        Object device = log.getField(this, "device");
        String loc = "?";
        if (device instanceof TileEntity) {
            TileEntity te = (TileEntity) device;
            BlockPos p = te.getPos();
            int dim = te.getWorld() != null ? te.getWorld().provider.getDimension() : -999;
            loc = "dim" + dim + "/" + p.getX() + "," + p.getY() + "," + p.getZ();
        }

        String state;
        if (returned > 0) {
            state = "FLOW";
        } else if (amount > 0) {
            state = "REJECTED";
        } else {
            state = "IDLE";
        }

        log.logIfChanged("plug@" + loc + "/state", state,
                "amount=" + amount + " returned=" + returned + " buffer=" + buffer + " side=" + side
                        + ("REJECTED".equals(state) ? " → 发电机在推但被 bufferLimiter 拒收"
                        : "IDLE".equals(state) ? " → 发电机(MMCE)无人时未推电" : " → 正常接收"));
    }
}