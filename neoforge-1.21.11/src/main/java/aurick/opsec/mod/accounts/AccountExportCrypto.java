package aurick.opsec.mod.accounts;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.PrivacyLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Optional passphrase-based encryption for exported account JSON files.
 *
 * <p>Unlike {@link AccountCrypto} (which protects the on-disk {@code opsec-accounts.json}
 * with a key file that only makes sense on the machine that generated it), an exported file
 * is meant to travel — backed up, moved to another PC, etc. A key file isn't portable, so
 * this derives the key from a passphrase the user supplies at export/import time instead.
 * The export JSON is not encrypted unless the user opts in and enters a passphrase.</p>
 */
public final class AccountExportCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000; // OWASP 2023 minimum for PBKDF2-HMAC-SHA256

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountExportCrypto() {}

    /** Wraps {@code plainJson} in an encrypted envelope JSON string using the given passphrase. */
    public static String encrypt(String plainJson, char[] passphrase) {
        try {
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);

            SecretKey key = deriveKey(passphrase, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainJson.getBytes(StandardCharsets.UTF_8));

            JsonObject envelope = new JsonObject();
            envelope.addProperty("opsecExportVersion", 1);
            envelope.addProperty("encrypted", true);
            envelope.addProperty("kdf", "PBKDF2WithHmacSHA256");
            envelope.addProperty("iterations", PBKDF2_ITERATIONS);
            envelope.addProperty("salt", Base64.getEncoder().encodeToString(salt));
            envelope.addProperty("iv", Base64.getEncoder().encodeToString(iv));
            envelope.addProperty("ciphertext", Base64.getEncoder().encodeToString(ciphertext));
            return envelope.toString();
        } catch (Exception e) {
            Opsec.LOGGER.error("[OpSec] Failed to encrypt account export: {}", PrivacyLogger.redactSecrets(e.getMessage()));
            throw new RuntimeException("Export encryption failed", e);
        }
    }

    /** True iff {@code content} looks like an envelope produced by {@link #encrypt}. */
    public static boolean isEncryptedEnvelope(String content) {
        try {
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return json.has("encrypted") && json.get("encrypted").getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /** Decrypts an envelope produced by {@link #encrypt}. Throws if the passphrase is wrong or the envelope is malformed. */
    public static String decrypt(String envelopeJson, char[] passphrase) throws Exception {
        JsonObject envelope = JsonParser.parseString(envelopeJson).getAsJsonObject();
        int iterations = envelope.has("iterations") ? envelope.get("iterations").getAsInt() : PBKDF2_ITERATIONS;
        byte[] salt = Base64.getDecoder().decode(envelope.get("salt").getAsString());
        byte[] iv = Base64.getDecoder().decode(envelope.get("iv").getAsString());
        byte[] ciphertext = Base64.getDecoder().decode(envelope.get("ciphertext").getAsString());

        SecretKey key = deriveKey(passphrase, salt, iterations);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        return deriveKey(passphrase, salt, PBKDF2_ITERATIONS);
    }

    private static SecretKey deriveKey(char[] passphrase, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, KEY_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
