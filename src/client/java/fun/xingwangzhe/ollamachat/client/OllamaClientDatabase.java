package fun.xingwangzhe.ollamachat.client;

import com.mojang.authlib.GameProfile;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.Database;
import fun.xingwangzhe.ollamachat.database.LocalDatabase;
import fun.xingwangzhe.ollamachat.database.DummyDatabase;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

public class OllamaClientDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");
    private static Database database;

    public static void initialize(ModConfig config) {
        try {
            // Try to create local database
            database = new LocalDatabase(config);
            LOGGER.info("Initialized local database for client");
        } catch (Exception e) {
            if (e.getMessage().contains("SQLite JDBC driver not found")) {
                LOGGER.error("SQLite driver not found. Please install the SQLite mod.");
            } else {
                LOGGER.error("Failed to initialize database: " + e.getMessage());
            }
            // Fallback to dummy database
            database = new DummyDatabase();
            LOGGER.info("Using dummy database (no persistence)");
        }
    }

    public static void close() {
        if (database != null) {
            try {
                database.close();
                LOGGER.info("Closed OllamaChat client database");
            } catch (Exception e) {
                LOGGER.error("Failed to close database: " + e.getMessage());
            }
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
            
            // Save to appropriate database
            if (isPrivate) {
                database.savePrivateMessage(playerUuid, message, response);
            } else {
                database.savePublicMessage(playerUuid, message, response);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save message: " + e.getMessage());
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
} 