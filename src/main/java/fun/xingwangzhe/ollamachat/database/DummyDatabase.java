package fun.xingwangzhe.ollamachat.database;

import java.util.ArrayList;
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
    public void saveMessage(UUID playerUuid, String message, String response) {
        // Dummy implementation - does nothing
    }

    @Override
    public void savePublicMessage(UUID playerUuid, String message, String response) {
        // Dummy implementation - does nothing
    }

    @Override
    public void savePrivateMessage(UUID playerUuid, String message, String response) {
        // Dummy implementation - does nothing
    }

    @Override
    public List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit) {
        // Dummy implementation - returns empty list
        return new ArrayList<>();
    }

    @Override
    public List<ConversationEntry> getPublicConversationHistory(UUID playerUuid, int limit) {
        // Dummy implementation - returns empty list
        return new ArrayList<>();
    }

    @Override
    public List<ConversationEntry> getPrivateConversationHistory(UUID playerUuid, int limit) {
        // Dummy implementation - returns empty list
        return new ArrayList<>();
    }

    @Override
    public void deletePlayerHistory(UUID playerUuid) {
        // Dummy implementation - does nothing
    }

    @Override
    public void deletePlayerPublicHistory(UUID playerUuid) {
        // Dummy implementation - does nothing
    }

    @Override
    public void deletePlayerPrivateHistory(UUID playerUuid) {
        // Dummy implementation - does nothing
    }

    @Override
    public void deleteAllHistory() {
        // Dummy implementation - does nothing
    }

    @Override
    public void deleteAllPublicHistory() {
        // Dummy implementation - does nothing
    }

    @Override
    public void deleteAllPrivateHistory() {
        // Dummy implementation - does nothing
    }

    @Override
    public void close() {
        // Dummy implementation - does nothing
    }

    @Override
    public String decryptMessage(String encryptedMessage) {
        return encryptedMessage;
    }

    @Override
    public void addMessage(UUID playerUuid, String message, String response) {
        // Dummy implementation - does nothing
    }

    @Override
    public List<String> getRecentConversations(UUID playerUuid, int limit) {
        // Dummy implementation - returns empty list
        return new ArrayList<>();
    }
} 