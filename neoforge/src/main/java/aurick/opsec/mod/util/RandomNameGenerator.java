package aurick.opsec.mod.util;

import java.util.concurrent.ThreadLocalRandom;

/** Generates random valid Minecraft usernames (3-16 chars, letters/digits/underscore) for offline accounts. */
public final class RandomNameGenerator {
    private RandomNameGenerator() {}

    private static final String[] ADJECTIVES = {
        "Silent", "Shadow", "Frozen", "Crimson", "Rusty", "Lucky", "Wild", "Quiet",
        "Golden", "Iron", "Sneaky", "Lone", "Swift", "Grim", "Dusty", "Bright",
        "Cosmic", "Feral", "Hollow", "Jolly", "Mellow", "Rapid", "Salty", "Vivid"
    };

    private static final String[] NOUNS = {
        "Wolf", "Fox", "Hawk", "Bear", "Raven", "Viper", "Otter", "Falcon",
        "Badger", "Lynx", "Panther", "Eagle", "Cobra", "Wombat", "Yak", "Moose",
        "Goblin", "Pixel", "Ranger", "Nomad", "Drifter", "Rogue", "Ghost", "Miner"
    };

    /** Adjective + Noun + optional 1-3 digit number, trimmed to the 16-char username limit. */
    public static String generate() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String adjective = ADJECTIVES[rng.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[rng.nextInt(NOUNS.length)];
        String base = adjective + noun;

        if (base.length() > 16) {
            base = base.substring(0, 16);
            return base;
        }

        int remaining = 16 - base.length();
        if (remaining >= 1 && rng.nextBoolean()) {
            int digits = Math.min(remaining, 1 + rng.nextInt(3));
            int max = (int) Math.pow(10, digits);
            base = base + rng.nextInt(max);
        }
        return base;
    }
}
