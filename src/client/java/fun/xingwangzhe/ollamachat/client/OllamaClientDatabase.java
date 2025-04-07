package fun.xingwangzhe.ollamachat.client;

import com.mojang.authlib.GameProfile;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.Database;
import fun.xingwangzhe.ollamachat.database.LocalDatabase;
import fun.xingwangzhe.ollamachat.database.DummyDatabase;
import fun.xingwangzhe.ollamachat.database.ConversationEntry;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.UUID;

public class OllamaClientDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");
    private static Database database;
    private static ModConfig config;

    public static void initialize(ModConfig modConfig) {
        config = modConfig;
        try {
            database = new LocalDatabase(config);
            LOGGER.info("Client database initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize client database: " + e.getMessage());
            LOGGER.info("Falling back to dummy database");
            database = new DummyDatabase();
        }
    }

    public static void saveMessage(String message, String response, boolean isPrivate) {
        if (database == null) {
            LOGGER.error("Database not initialized");
            return;
        }

        try {
            // Get player UUID from game profile
            GameProfile profile = MinecraftClient.getInstance().getGameProfile();
            if (profile == null) {
                LOGGER.error("Could not get player profile");
                return;
            }
            UUID playerUuid = profile.getId();
            if (playerUuid == null) {
                LOGGER.error("Could not get player UUID");
                return;
            }
            
            // Save message to appropriate history
            if (isPrivate) {
                database.savePrivateMessage(playerUuid, message, response);
            } else {
                database.savePublicMessage(playerUuid, message, response);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save message: " + e.getMessage());
        }
    }

    public static List<ConversationEntry> getPublicConversationHistory(UUID playerUuid, int limit) {
        if (database == null) {
            LOGGER.error("Database not initialized");
            return List.of();
        }

        try {
            return database.getPublicConversationHistory(playerUuid, limit);
        } catch (Exception e) {
            LOGGER.error("Failed to get public conversation history: " + e.getMessage());
            return List.of();
        }
    }

    public static List<ConversationEntry> getPrivateConversationHistory(UUID playerUuid, int limit) {
        if (database == null) {
            LOGGER.error("Database not initialized");
            return List.of();
        }

        try {
            return database.getPrivateConversationHistory(playerUuid, limit);
        } catch (Exception e) {
            LOGGER.error("Failed to get private conversation history: " + e.getMessage());
            return List.of();
        }
    }

    public static void clearHistory(boolean isPrivate) {
        if (database == null) {
            LOGGER.error("Database not initialized");
            return;
        }

        try {
            // Get player UUID from game profile
            GameProfile profile = MinecraftClient.getInstance().getGameProfile();
            if (profile == null) {
                LOGGER.error("Could not get player profile");
                return;
            }
            UUID playerUuid = profile.getId();
            if (playerUuid == null) {
                LOGGER.error("Could not get player UUID");
                return;
            }
            
            // Clear appropriate history
            if (isPrivate) {
                database.deletePlayerPrivateHistory(playerUuid);
            } else {
                database.deletePlayerPublicHistory(playerUuid);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to clear history: " + e.getMessage());
        }
    }

    public static void close() {
        if (database != null) {
            try {
                database.close();
            } catch (Exception e) {
                LOGGER.error("Failed to close database: " + e.getMessage());
            }
        }
    }
} 