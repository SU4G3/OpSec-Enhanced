package aurick.opsec.mod.command;

/**
 * TODO (NeoForge port): the {@code /opsec} debug command isn't ported yet.
 *
 * <p>The Fabric implementation is built entirely on Fabric's client-command API
 * ({@code ClientCommandRegistrationCallback}, {@code ClientCommandManager},
 * {@code FabricClientCommandSource}) — porting it means rewriting against
 * NeoForge's {@code RegisterClientCommandsEvent} and vanilla's
 * {@code CommandDispatcher<CommandSourceStack>} instead. It's a real, mechanical
 * rewrite (~300 lines), just not done yet — deferred in favor of the actual
 * protection features first, since this command is off by default and exists
 * purely for debugging ({@code /opsec info}, {@code /opsec channels}).
 */
public final class OpsecCommand {
    private OpsecCommand() {
    }

    public static void register() {
        // No-op until ported — see class javadoc.
    }
}
