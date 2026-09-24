package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import aurick.opsec.mod.util.LogScrubber;
import net.minecraft.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scrubs the OS username (from file paths), IP addresses, and the player's own name out
 * of a crash report before it's written to disk or shown on the crash screen —
 * {@code saveToFile} builds its text from this same method, so this one hook covers both.
 */
@Mixin(CrashReport.class)
public abstract class CrashReportMixin {

    //? if >=1.21.1 {
    @Inject(method = "getFriendlyReport(Lnet/minecraft/ReportType;Ljava/util/List;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
    private void opsec$scrub(net.minecraft.ReportType reportType, java.util.List<String> extraDetails, CallbackInfoReturnable<String> cir) {
        if (OpsecConfig.getInstance().getSettings().isLogScrubberEnabled()) {
            cir.setReturnValue(LogScrubber.scrub(cir.getReturnValue()));
        }
    }
    //?} else {
    /*@Inject(method = "getFriendlyReport()Ljava/lang/String;", at = @At("RETURN"), cancellable = true)
    private void opsec$scrub(CallbackInfoReturnable<String> cir) {
        if (OpsecConfig.getInstance().getSettings().isLogScrubberEnabled()) {
            cir.setReturnValue(LogScrubber.scrub(cir.getReturnValue()));
        }
    }
    *///?}
}
