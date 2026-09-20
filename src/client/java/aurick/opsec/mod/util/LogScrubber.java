package aurick.opsec.mod.util;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Redacts personally-identifying substrings — the OS account name (leaked via file
 * paths in stack traces and system-info dumps), IP addresses, and the current
 * player's name — from crash reports and log exports, so they're safe to paste
 * into mclo.gs, a pastebin, or a bug report.
 */
public final class LogScrubber {

    private static final Pattern WINDOWS_USER_PATH = Pattern.compile("([A-Za-z]:\\\\Users\\\\)([^\\\\/:*?\"<>|\\r\\n]+)");
    private static final Pattern UNIX_HOME_PATH = Pattern.compile("(/home/)([^/\\s]+)");
    private static final Pattern MAC_USER_PATH = Pattern.compile("(/Users/)([^/\\s]+)");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern IPV6 = Pattern.compile("\\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\\b");

    private LogScrubber() {
    }

    /**
     * Returns {@code text} with OS username paths, IP addresses, and the current
     * player's name replaced by fixed placeholders. Null-safe (returns null for null).
     */
    public static String scrub(String text) {
        if (text == null) return null;
        String result = text;
        result = WINDOWS_USER_PATH.matcher(result).replaceAll("$1<redacted>");
        result = UNIX_HOME_PATH.matcher(result).replaceAll("$1<redacted>");
        result = MAC_USER_PATH.matcher(result).replaceAll("$1<redacted>");
        result = IPV4.matcher(result).replaceAll("<redacted-ip>");
        result = IPV6.matcher(result).replaceAll("<redacted-ip>");

        String serverAddress = LocalAddressUtil.serverAddress;
        if (serverAddress != null && !serverAddress.isBlank()) {
            result = result.replace(serverAddress, "<redacted-ip>");
        }

        String username = currentUsername();
        if (username != null && !username.isBlank()) {
            result = result.replace(username, "<redacted-player>");
        }

        return result;
    }

    /**
     * Reads {@code input}, scrubs it, and writes the result to {@code output}
     * (UTF-8, overwriting). Used for the "Export Sanitized Log" button — scrubbing
     * a live-streaming log in place isn't worth the risk of destabilizing Minecraft's
     * own Log4j2 pipeline, so this runs on-demand against the finished file instead.
     */
    public static void scrubFile(Path input, Path output) throws IOException {
        String content = Files.readString(input, StandardCharsets.UTF_8);
        Files.writeString(output, scrub(content), StandardCharsets.UTF_8);
    }

    private static String currentUsername() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.getUser() != null ? mc.getUser().getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
