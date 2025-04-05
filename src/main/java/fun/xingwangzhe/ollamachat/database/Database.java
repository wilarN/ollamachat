package fun.xingwangzhe.ollamachat.database;

import java.util.List;
import java.util.UUID;

public interface Database {
    void addMessage(UUID playerUuid, String message, String response);
    List<String> getRecentConversations(UUID playerUuid, int limit);
    List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit);
    void saveMessage(UUID playerUuid, String message, String response);
    void deletePlayerHistory(UUID playerUuid);
    void deleteAllHistory();
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
} 