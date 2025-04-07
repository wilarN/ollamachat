package fun.xingwangzhe.ollamachat.database;

import java.util.List;
import java.util.UUID;

public interface Database {
    /**
     * Saves a message and its response to the database
     * 
     * @deprecated Use savePublicMessage or savePrivateMessage instead. This method will be removed in a future version.
     */
    @Deprecated
    void saveMessage(UUID playerUuid, String message, String response);

    /**
     * Gets the conversation history for a player
     * 
     * @deprecated Use getPublicConversationHistory or getPrivateConversationHistory instead. This method will be removed in a future version.
     */
    @Deprecated
    List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit);

    /**
     * Deletes a player's conversation history
     * 
     * @deprecated Use deletePlayerPublicHistory and deletePlayerPrivateHistory instead. This method will be removed in a future version.
     */
    @Deprecated
    void deletePlayerHistory(UUID playerUuid);

    /**
     * Deletes all conversation history
     * 
     * @deprecated Use deleteAllPublicHistory and deleteAllPrivateHistory instead. This method will be removed in a future version.
     */
    @Deprecated
    void deleteAllHistory();

    /**
     * Closes the database connection
     */
    void close();
    
    /**
     * Decrypts a message if it's encrypted
     * 
     * @param encryptedMessage The potentially encrypted message
     * @return The decrypted message, or the original message if not encrypted or if decryption fails
     */
    default String decryptMessage(String encryptedMessage) {
        return encryptedMessage;
    }

    /**
     * Adds a message to the database
     * 
     * @deprecated Use savePublicMessage or savePrivateMessage instead. This method will be removed in a future version.
     */
    @Deprecated
    void addMessage(UUID playerUuid, String message, String response);

    /**
     * Gets recent conversations for a player
     * 
     * @deprecated Use getPublicConversationHistory or getPrivateConversationHistory instead. This method will be removed in a future version.
     */
    @Deprecated
    List<String> getRecentConversations(UUID playerUuid, int limit);
    
    /**
     * Saves a public message and its response to the database
     */
    void savePublicMessage(UUID playerUuid, String message, String response);
    
    /**
     * Saves a private message and its response to the database
     */
    void savePrivateMessage(UUID playerUuid, String message, String response);
    
    /**
     * Gets the public conversation history for a player
     */
    List<ConversationEntry> getPublicConversationHistory(UUID playerUuid, int limit);
    
    /**
     * Gets the private conversation history for a player
     */
    List<ConversationEntry> getPrivateConversationHistory(UUID playerUuid, int limit);
    
    /**
     * Deletes a player's public conversation history
     */
    void deletePlayerPublicHistory(UUID playerUuid);
    
    /**
     * Deletes a player's private conversation history
     */
    void deletePlayerPrivateHistory(UUID playerUuid);
    
    /**
     * Deletes all public conversation history
     */
    void deleteAllPublicHistory();
    
    /**
     * Deletes all private conversation history
     */
    void deleteAllPrivateHistory();
} 