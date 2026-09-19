package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * At-rest encryption for saved account secrets (Microsoft/Mojang access + refresh tokens).
 *
 * <p>{@code opsec-accounts.json} previously stored these tokens as plaintext. A stolen or
 * accidentally-shared copy of that file was immediately usable to hijack the account. This
 * wraps each secret in AES-256-GCM using a random key generated on first use and stored
 * separately ({@code opsec-accounts.key}), with owner-only file permissions where the
 * platform supports POSIX permissions. This raises the bar from "plaintext token dump" to
 * "attacker needs both files" — it does not protect against an attacker with full access to
 * the profile directory (no client-side scheme can), and it is not a substitute for treating
 * exported account JSON as sensitive.</p>
 *
 * <p>Ciphertext is marked with a version prefix so plaintext values from older configs are
 * passed through unchanged on load (and re-encrypted the next time the account list saves).</p>
 */
public final class AccountCrypto {

    private static final String PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = 32; // AES-256

    private static final Path KEY_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("opsec-accounts.key");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKeySpec cachedKey;

    private AccountCrypto() {}

    /** Encrypts {@code plaintext}, returning a {@code enc:v1:}-prefixed value safe to store in JSON. */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKeySpec key = getOrCreateKey();
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            // Fail closed on the read side, not the write side: if encryption breaks we'd
            // rather keep the plaintext (still functional, matches pre-encryption behavior)
            // than lose the token entirely.
            Opsec.LOGGER.error("[OpSec] Failed to encrypt account secret, storing as plaintext: {}", e.getMessage());
            return plaintext;
        }
    }

    /** Decrypts a value previously returned by {@link #encrypt}. Values without the marker pass through unchanged. */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || !stored.startsWith(PREFIX)) return stored;
        try {
            SecretKeySpec key = getOrCreateKey();
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length < IV_BYTES) throw new IllegalArgumentException("ciphertext too short");

            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(raw, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[raw.length - IV_BYTES];
            System.arraycopy(raw, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to decrypt account secret (key file missing/changed?): {}", e.getMessage());
            return "";
        }
    }

    private static SecretKeySpec getOrCreateKey() throws Exception {
        SecretKeySpec key = cachedKey;
        if (key != null) return key;

        synchronized (AccountCrypto.class) {
            if (cachedKey != null) return cachedKey;

            byte[] keyBytes;
            if (Files.exists(KEY_PATH)) {
                keyBytes = Files.readAllBytes(KEY_PATH);
                if (keyBytes.length != KEY_BYTES) {
                    Opsec.LOGGER.warn("[OpSec] opsec-accounts.key has unexpected length, regenerating (existing encrypted secrets will fail to decrypt)");
                    keyBytes = generateAndStoreKey();
                }
            } else {
                keyBytes = generateAndStoreKey();
            }

            cachedKey = new SecretKeySpec(keyBytes, "AES");
            return cachedKey;
        }
    }

    private static byte[] generateAndStoreKey() throws Exception {
        byte[] keyBytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(keyBytes);

        Files.createDirectories(KEY_PATH.getParent());
        Path tempFile = KEY_PATH.resolveSibling(KEY_PATH.getFileName() + ".tmp");
        Files.write(tempFile, keyBytes);
        Files.move(tempFile, KEY_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        restrictToOwner(KEY_PATH);
        return keyBytes;
    }

    /** Best-effort owner-only permissions (POSIX only — no-op and silently ignored on Windows). */
    static void restrictToOwner(Path path) {
        try {
            path.getFileSystem().provider().checkAccess(path);
            Files.setPosixFilePermissions(path, Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
            ));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (Windows) — the OS ACLs already default to the owning user.
        } catch (Exception e) {
            Opsec.LOGGER.debug("[OpSec] Could not restrict permissions on {}: {}", path, e.getMessage());
        }
    }
}
