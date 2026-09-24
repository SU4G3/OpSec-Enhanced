package aurick.opsec.mod.mixin;

/**
 * Stub for the NeoForge port. The real implementation cancels Meteor Client's
 * broken key-resolution mixin via MixinSquared's {@code MixinCanceller} — a
 * Fabric-ecosystem library with no NeoForge equivalent. This is moot anyway:
 * Meteor Client itself is Fabric-only and has no NeoForge build to fix.
 * {@code needsRestart} always returns false so the config screen's "restart
 * needed" warning never fires here.
 */
public final class MeteorMixinCanceller {
    private MeteorMixinCanceller() {
    }

    public static boolean needsRestart(boolean currentSetting) {
        return false;
    }
}
