package aurick.opsec.mod.mixin.client;

//? if >=1.20.2 {
import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.CommandHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts the arguments of auth-plugin login/register commands before they reach
 * {@code CommandHistory}'s in-memory recall list or its on-disk persisted file
 * ({@code command_history.txt}) — vanilla stores exactly what was typed, passwords
 * included. The command name is kept (so history stays useful) and only the
 * argument text is replaced.
 */
@Mixin(CommandHistory.class)
public abstract class CommandHistoryMixin {

    private static final Pattern SENSITIVE_COMMAND = Pattern.compile(
            "(?i)^(/?)(login|l|register|reg|changepassword|changepw|cpw|premium|2fa|authme)(\\s+.+)$");

    @ModifyVariable(method = "addCommand", at = @At("HEAD"), argsOnly = true)
    private String opsec$redactSensitiveCommand(String command) {
        if (!OpsecConfig.getInstance().getSettings().isCommandHistoryGuard()) return command;
        Matcher matcher = SENSITIVE_COMMAND.matcher(command);
        if (!matcher.matches()) return command;
        return matcher.group(1) + matcher.group(2) + " <redacted>";
    }
}
//?} else {
/*
import net.minecraft.network.protocol.PacketUtils;
import org.spongepowered.asm.mixin.Mixin;

// 1.20.1: CommandHistory doesn't exist yet (introduced 1.20.2) — command recall
// on this era is an in-memory-only list with no disk persistence, so there's
// nothing to guard.
@Mixin(PacketUtils.class)
public abstract class CommandHistoryMixin {
}
*///?}
