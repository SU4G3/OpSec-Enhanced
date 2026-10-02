package aurick.opsec.mod.util;

import net.minecraft.world.entity.HumanoidArm;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fixed-for-the-session randomized values for the Client Information Normalizer.
 *
 * <p>A single universal baseline (view distance 10, always right-handed, ...) is itself a
 * fingerprint: every OpSec user reports the exact same combination, which a server could key
 * on just as easily as a real varied setting. These two fields vary per game launch within
 * realistic bounds — picked once and reused for every packet in a session (not reshuffled per
 * packet or per connection), since a value that changes without the player touching the
 * settings screen would itself look suspicious.</p>
 *
 * <p>Everything else in {@code ClientInformation} stays at the fixed baseline — language,
 * chat visibility, skin layers, text filtering, and server listing opt-in are behavioral
 * rather than physical traits, and varying them risks looking more inconsistent (e.g.
 * filtering silently flipping between sessions) than a single common value does.</p>
 */
public final class NormalizedClientInfo {

    /** Common real view-distance settings; vanilla's own default (10) is weighted heaviest. */
    private static final int[] VIEW_DISTANCE_CHOICES = {8, 8, 10, 10, 10, 10, 12, 12, 16};

    public static final int VIEW_DISTANCE =
            VIEW_DISTANCE_CHOICES[ThreadLocalRandom.current().nextInt(VIEW_DISTANCE_CHOICES.length)];

    /** ~10% of real players report off-hand/left-handed, matching general left-handedness rates. */
    public static final HumanoidArm MAIN_HAND =
            ThreadLocalRandom.current().nextInt(10) == 0 ? HumanoidArm.LEFT : HumanoidArm.RIGHT;

    private NormalizedClientInfo() {}
}
