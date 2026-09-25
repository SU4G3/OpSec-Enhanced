package aurick.opsec.mod.util;

import aurick.opsec.mod.Opsec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Wires the "Export Sanitized Log" button: scrubs {@code logs/latest.log} with
 * {@link LogScrubber} and writes it next to the original as {@code logs/latest-scrubbed.log}.
 */
public final class LogExportHandler {

    private LogExportHandler() {
    }

    /**
     * Returns true on success. Failures are logged to console rather than shown
     * in-game — this button lives on a settings screen the player is already
     * looking at, so its own text/state is the feedback.
     */
    public static boolean exportSanitizedLog() {
        try {
            Path logsDir = NeoCompat.getGameDir().resolve("logs");
            Path input = logsDir.resolve("latest.log");
            Path output = logsDir.resolve("latest-scrubbed.log");
            LogScrubber.scrubFile(input, output);
            Opsec.LOGGER.info("[OpSec] Sanitized log exported to {}", output);
            return true;
        } catch (IOException e) {
            Opsec.LOGGER.warn("[OpSec] Failed to export sanitized log: {}", e.getMessage());
            return false;
        }
    }
}
