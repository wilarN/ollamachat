package fun.xingwangzhe.ollamachat.database;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dummy database implementation that does nothing.
 * This is used when no database drivers are available.
 */
public class DummyDatabase implements Database {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat");

    public DummyDatabase() {
        LOGGER.warn("Using dummy database implementation. No data will be stored.");
    }

    @Override
    public void addMessage(UUID playerUuid, String message, String response) {
        // Do nothing
    }

    @Override
    public List<String> getRecentConversations(UUID playerUuid, int limit) {
        // Return empty list
        return List.of();
    }

    @Override
    public List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit) {
        // Return empty list
        return List.of();
    }

    @Override
    public String decryptMessage(String encryptedMessage) {
        // Return the message as is
        return encryptedMessage;
    }

    @Override
    public void saveMessage(UUID playerUuid, String message, String response) {
        // Do nothing
    }

    @Override
    public void deletePlayerHistory(UUID playerUuid) {
        // Do nothing
    }

    @Override
    public void deleteAllHistory() {
        // Do nothing
    }

    @Override
    public void close() {
        // Do nothing
    }
} 