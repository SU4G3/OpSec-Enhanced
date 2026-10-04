package aurick.opsec.mod.mixin.client;

//? if <1.21.11 {
public class DebugEntryPositionMixin {}
//?} else {
/*
import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Streamer Mode > Hide Coordinates. Replaces vanilla's F3 position group (world XYZ,
// block pos, chunk pos, facing, force-loaded-chunk count) with a single placeholder line.
//
// This only covers vanilla's own DebugEntryPosition -- a debug-HUD replacement mod (e.g.
// BetterF3) that reads the camera entity's position directly and renders its own text,
// bypassing this entry entirely, is NOT covered by this mixin. Doing so generally would
// mean intercepting raw text draws during F3 rendering regardless of source, which risks
// false-positive redaction of unrelated debug lines (FPS, memory, etc.) -- not attempted
// here; a known limitation, not an oversight.
//
// DebugEntryPosition/DebugScreenEntries is a 1.21.11+ refactor of the F3 overlay's entry
// list (independent of the later 26.1 render-pipeline extraction split -- this entry's
// display(...) signature is identical on both sides of that split); earlier targets have
// no equivalent hook here yet.
@Mixin(DebugEntryPosition.class)
public class DebugEntryPositionMixin {

    @Inject(method = "display", at = @At("HEAD"), cancellable = true)
    private void opsec$hideCoordinates(
            DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk,
            CallbackInfo ci) {
        var settings = OpsecConfig.getInstance().getSettings();
        if (!settings.isStreamerModeEnabled() || !settings.isStreamerHideCoordinates()) return;

        displayer.addToGroup(DebugEntryPosition.GROUP, List.of("XYZ: hidden (Streamer Mode)"));
        ci.cancel();
    }
}
*///?}
