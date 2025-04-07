package fun.xingwangzhe.ollamachat.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import fun.xingwangzhe.ollamachat.OllamaChatMod;

/**
 * Utility class for encrypting and decrypting messages
 */
public class MessageEncryption {
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORM = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final String ENCRYPTION_PREFIX = "ENC:";
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    /**
     * Creates a new encryption utility with a randomly generated key
     */
    public MessageEncryption() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE);
            this.secretKey = keyGen.generateKey();
            this.secureRandom = new SecureRandom();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }
    
    /**
     * Creates a new encryption utility with the provided key
     * 
     * @param keyBase64 Base64 encoded secret key
     */
    public MessageEncryption(String keyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            this.secureRandom = new SecureRandom();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption with provided key", e);
        }
    }
    
    /**
     * Encrypts a message
     * 
     * @param message The message to encrypt
     * @return Base64 encoded encrypted message with IV prepended
     */
    public String encrypt(String message) {
        try {
            // Generate a random IV
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            // Encrypt the message
            byte[] encrypted = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            // Encode as Base64 and add prefix
            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt message", e);
        }
    }
    
    /**
     * Decrypts a message
     * 
     * @param encryptedMessage Base64 encoded encrypted message with IV prepended
     * @return The decrypted message
     */
    public String decrypt(String encryptedMessage) {
        try {
            // Check if the message is actually encrypted
            if (encryptedMessage == null || encryptedMessage.isEmpty()) {
                return encryptedMessage;
            }
            
            // Check if the message has the encryption prefix
            if (!encryptedMessage.startsWith(ENCRYPTION_PREFIX)) {
                // Not encrypted, return as is
                return encryptedMessage;
            }
            
            // Remove the prefix
            String base64Encoded = encryptedMessage.substring(ENCRYPTION_PREFIX.length());
            
            // Decode the base64 string
            byte[] encryptedBytes = Base64.getDecoder().decode(base64Encoded);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[16];
            System.arraycopy(encryptedBytes, 0, iv, 0, 16);
            
            byte[] ciphertext = new byte[encryptedBytes.length - 16];
            System.arraycopy(encryptedBytes, 16, ciphertext, 0, ciphertext.length);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            // Decrypt
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Log the error but don't throw an exception
            OllamaChatMod.LOGGER.error("Failed to decrypt message: {}", e.getMessage());
            // Return the original message if decryption fails
            return encryptedMessage;
        }
    }
    
    /**
     * Gets the secret key as a Base64 encoded string
     * 
     * @return Base64 encoded secret key
     */
    public String getKeyAsBase64() {
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }
} 