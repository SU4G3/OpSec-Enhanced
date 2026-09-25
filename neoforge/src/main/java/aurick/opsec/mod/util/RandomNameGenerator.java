package aurick.opsec.mod.util;

import java.util.concurrent.ThreadLocalRandom;

/** Generates random valid Minecraft usernames (3-16 chars, letters/digits/underscore) for offline accounts. */
public final class RandomNameGenerator {
    private RandomNameGenerator() {}

    private static final String[] ADJECTIVES = {
        "Silent", "Shadow", "Frozen", "Crimson", "Rusty", "Lucky", "Wild", "Quiet",
        "Golden", "Iron", "Sneaky", "Lone", "Swift", "Grim", "Dusty", "Bright",
        "Cosmic", "Feral", "Hollow", "Jolly", "Mellow", "Rapid", "Salty", "Vivid",
        "Amber", "Arctic", "Ashen", "Blazing", "Bold", "Brave", "Broken", "Burning",
        "Chilly", "Clever", "Cloudy", "Coral", "Dark", "Dread", "Emerald", "Fading",
        "Fierce", "Foggy", "Ghastly", "Gloomy", "Glossy", "Grumpy", "Hazy", "Hidden",
        "Humble", "Icy", "Jagged", "Keen", "Lively", "Loud", "Mad", "Misty",
        "Moody", "Muddy", "Mystic", "Neon", "Noble", "Northern", "Obsidian", "Pale",
        "Phantom", "Prime", "Quick", "Rabid", "Radiant", "Rough", "Royal", "Ruthless",
        "Savage", "Scarlet", "Sharp", "Sly", "Smoky", "Sober", "Solar", "Sour",
        "Spare", "Spectral", "Spiky", "Stark", "Steel", "Stern", "Stormy", "Sunny",
        "Tame", "Tangled", "Toxic", "Twisted", "Umbral", "Vast", "Violet", "Void",
        "Warped", "Weary", "Windy", "Wispy", "Wry", "Zesty"
    };

    private static final String[] NOUNS = {
        "Wolf", "Fox", "Hawk", "Bear", "Raven", "Viper", "Otter", "Falcon",
        "Badger", "Lynx", "Panther", "Eagle", "Cobra", "Wombat", "Yak", "Moose",
        "Goblin", "Pixel", "Ranger", "Nomad", "Drifter", "Rogue", "Ghost", "Miner",
        "Archer", "Bandit", "Beetle", "Bison", "Bramble", "Brawler", "Buzzard", "Camel",
        "Cipher", "Comet", "Coyote", "Crow", "Dagger", "Dingo", "Draco", "Ember",
        "Fennec", "Ferret", "Gargoyle", "Gecko", "Golem", "Griffin", "Harrier", "Hermit",
        "Hornet", "Hunter", "Hyena", "Jackal", "Jester", "Kestrel", "Kraken", "Lantern",
        "Marten", "Mongrel", "Mystic", "Ocelot", "Osprey", "Outlaw", "Owl", "Phoenix",
        "Pirate", "Prowler", "Puma", "Python", "Quail", "Raider", "Ram", "Reaper",
        "Reptile", "Roamer", "Sable", "Scout", "Serpent", "Shark", "Sniper", "Sparrow",
        "Specter", "Stalker", "Stoat", "Talon", "Tiger", "Tracker", "Vagrant", "Vulture",
        "Wanderer", "Warden", "Wasp", "Weasel", "Wraith", "Wyvern", "Yeti", "Zealot"
    };

    /** Adjective + Noun + 2-4 digit number, trimmed to the 16-char username limit. */
    public static String generate() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String adjective = ADJECTIVES[rng.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[rng.nextInt(NOUNS.length)];
        String base = adjective + noun;

        if (base.length() > 16) {
            base = base.substring(0, 16);
        }

        int remaining = 16 - base.length();
        if (remaining >= 2) {
            int digits = Math.min(remaining, 2 + rng.nextInt(3));
            int min = (int) Math.pow(10, digits - 1);
            int max = (int) Math.pow(10, digits);
            base = base + (min + rng.nextInt(max - min));
        } else if (remaining >= 1) {
            base = base + rng.nextInt(10);
        }
        return base;
    }
}
