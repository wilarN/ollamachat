package fun.xingwangzhe.ollamachat.security;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.OllamaChatMod;

/**
 * Manages message encryption and decryption
 */
public class EncryptionManager {
    private static MessageEncryption encryption;
    private static boolean initialized = false;
    
    /**
     * Initializes the encryption manager with the provided configuration
     * 
     * @param config The mod configuration
     */
    public static void initialize(ModConfig config) {
        if (initialized) {
            return;
        }
        
        if (config.enableEncryption) {
            try {
                if (config.encryptionKey != null && !config.encryptionKey.isEmpty()) {
                    // Use the provided key
                    encryption = new MessageEncryption(config.encryptionKey);
                } else {
                    // Generate a new key
                    encryption = new MessageEncryption();
                    // Save the generated key to the config
                    config.encryptionKey = encryption.getKeyAsBase64();
                    config.save();
                    OllamaChatMod.LOGGER.info("Generated new encryption key");
                }
                OllamaChatMod.LOGGER.info("Message encryption enabled");
            } catch (Exception e) {
                OllamaChatMod.LOGGER.error("Failed to initialize encryption", e);
                encryption = null;
            }
        } else {
            OllamaChatMod.LOGGER.info("Message encryption disabled");
        }
        
        initialized = true;
    }
    
    /**
     * Encrypts a message if encryption is enabled
     * 
     * @param message The message to encrypt
     * @return The encrypted message, or the original message if encryption is disabled
     */
    public static String encrypt(String message) {
        if (!initialized || encryption == null) {
            return message;
        }
        
        try {
            return encryption.encrypt(message);
        } catch (Exception e) {
            OllamaChatMod.LOGGER.error("Failed to encrypt message", e);
            return message;
        }
    }
    
    /**
     * Decrypts a message if encryption is enabled
     * 
     * @param encryptedMessage The encrypted message to decrypt
     * @return The decrypted message, or the original message if encryption is disabled
     */
    public static String decrypt(String encryptedMessage) {
        if (!isEncryptionEnabled()) {
            return encryptedMessage;
        }
        
        try {
            return encryption.decrypt(encryptedMessage);
        } catch (Exception e) {
            // Log the error but don't throw an exception
            OllamaChatMod.LOGGER.error("Failed to decrypt message in EncryptionManager: {}", e.getMessage());
            // Return the original message if decryption fails
            return encryptedMessage;
        }
    }
    
    /**
     * Checks if encryption is enabled
     * 
     * @return true if encryption is enabled, false otherwise
     */
    public static boolean isEncryptionEnabled() {
        return initialized && encryption != null;
    }
} 