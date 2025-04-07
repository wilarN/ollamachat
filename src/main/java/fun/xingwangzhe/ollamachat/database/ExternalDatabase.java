package fun.xingwangzhe.ollamachat.database;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.memory.MemoryOptimizer;
import fun.xingwangzhe.ollamachat.security.EncryptionManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;

public class ExternalDatabase implements Database {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat");
    private final Connection connection;
    private final ModConfig config;
    private final MemoryOptimizer memoryOptimizer;
    private long lastCleanupTime = 0;
    
    // TODO: Future optimization - Implement connection pooling for better performance
    // This would allow reusing connections instead of keeping a single connection open

    public ExternalDatabase(ModConfig config) throws SQLException {
        this.config = config;
        this.memoryOptimizer = new MemoryOptimizer(config);
        
        LOGGER.info("Initializing ExternalDatabase with host: {}, port: {}, database: {}, username: {}", 
            config.databaseHost, config.databasePort, config.databaseName, config.databaseUsername);
        
        // Explicitly load the MySQL driver
        try {
            LOGGER.info("Loading MySQL driver");
            // Try multiple driver class names
            String[] driverClassNames = {
                "com.mysql.cj.jdbc.Driver",
                "com.mysql.jdbc.Driver",
                "fun.xingwangzhe.ollamachat.shadow.mysql.cj.jdbc.Driver",
                "fun.xingwangzhe.ollamachat.shadow.mysql.jdbc.Driver"
            };
            
            boolean driverLoaded = false;
            for (String driverClassName : driverClassNames) {
                try {
                    LOGGER.info("Trying to load driver: {}", driverClassName);
                    Class.forName(driverClassName);
                    LOGGER.info("MySQL driver loaded successfully: {}", driverClassName);
                    driverLoaded = true;
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("Driver not found: {}", driverClassName);
                }
            }
            
            if (!driverLoaded) {
                LOGGER.error("MySQL driver not found. Please install Kosmolot's MySQL mod: https://modrinth.com/mod/kosmolot-mysql");
                throw new ClassNotFoundException("No MySQL driver found. Please install Kosmolot's MySQL mod: https://modrinth.com/mod/kosmolot-mysql");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.error("MySQL driver not found: {}", e.getMessage());
            LOGGER.error("To use MySQL database, you must install Kosmolot's MySQL mod: https://modrinth.com/mod/kosmolot-mysql");
            throw new SQLException("MySQL driver not found. Please install Kosmolot's MySQL mod: https://modrinth.com/mod/kosmolot-mysql", e);
        }
        
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000&socketTimeout=30000", 
            config.databaseHost, 
            config.databasePort, 
            config.databaseName);
            
        LOGGER.info("Connecting to MySQL database at: {}", url);
        
        try {
            connection = DriverManager.getConnection(url, config.databaseUsername, config.databasePassword);
            LOGGER.info("Successfully connected to MySQL database");
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to MySQL database: {}", e.getMessage());
            LOGGER.error("SQL State: {}, Error Code: {}", e.getSQLState(), e.getErrorCode());
            throw e;
        }
        
        // Create tables if they don't exist
        try (Statement stmt = connection.createStatement()) {
            LOGGER.info("Creating tables if they don't exist");
            
            // Create public conversations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS public_conversations (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    message TEXT NOT NULL,
                    response TEXT NOT NULL,
                    timestamp BIGINT NOT NULL,
                    last_access BIGINT NOT NULL,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_last_access (last_access)
                )
            """);
            
            // Create private conversations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS private_conversations (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    message TEXT NOT NULL,
                    response TEXT NOT NULL,
                    timestamp BIGINT NOT NULL,
                    last_access BIGINT NOT NULL,
                    INDEX idx_player_uuid (player_uuid),
                    INDEX idx_last_access (last_access)
                )
            """);
            
            LOGGER.info("Tables created successfully");
        } catch (SQLException e) {
            LOGGER.error("Failed to create tables: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void addMessage(UUID playerUuid, String message, String response) {
        // For backward compatibility, use public conversations
        savePublicMessage(playerUuid, message, response);
    }
    
    @Override
    public void saveMessage(UUID playerUuid, String message, String response) {
        // For backward compatibility, use public conversations
        savePublicMessage(playerUuid, message, response);
    }
    
    @Override
    public void savePublicMessage(UUID playerUuid, String message, String response) {
        try {
            // Check if cleanup is needed
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime - lastCleanupTime > config.cleanupInterval) {
                cleanupOldMessages();
                lastCleanupTime = currentTime;
            }

            // Optimize message and response
            message = memoryOptimizer.optimizeMessage(message);
            response = memoryOptimizer.optimizeMessage(response);
            
            // Encrypt messages if encryption is enabled
            if (EncryptionManager.isEncryptionEnabled()) {
                message = EncryptionManager.encrypt(message);
                response = EncryptionManager.encrypt(response);
            }

            // Insert new message
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO public_conversations (player_uuid, message, response, timestamp, last_access) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, message);
                stmt.setString(3, response);
                stmt.setLong(4, currentTime);
                stmt.setLong(5, currentTime);
                stmt.executeUpdate();
            }

            // Check and limit conversations per player
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM public_conversations WHERE player_uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt(1) > config.maxConversationsPerPlayer) {
                    // Delete oldest conversations for this player
                    try (PreparedStatement deleteStmt = connection.prepareStatement(
                        "DELETE FROM public_conversations WHERE player_uuid = ? AND id IN " +
                        "(SELECT id FROM public_conversations WHERE player_uuid = ? ORDER BY timestamp ASC LIMIT ?)")) {
                        int excess = rs.getInt(1) - config.maxConversationsPerPlayer;
                        deleteStmt.setString(1, playerUuid.toString());
                        deleteStmt.setString(2, playerUuid.toString());
                        deleteStmt.setInt(3, excess);
                        deleteStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to save public message: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void savePrivateMessage(UUID playerUuid, String message, String response) {
        try {
            // Check if cleanup is needed
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime - lastCleanupTime > config.cleanupInterval) {
                cleanupOldMessages();
                lastCleanupTime = currentTime;
            }

            // Optimize message and response
            message = memoryOptimizer.optimizeMessage(message);
            response = memoryOptimizer.optimizeMessage(response);
            
            // Encrypt messages if encryption is enabled
            if (EncryptionManager.isEncryptionEnabled()) {
                message = EncryptionManager.encrypt(message);
                response = EncryptionManager.encrypt(response);
            }

            // Insert new message
            try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO private_conversations (player_uuid, message, response, timestamp, last_access) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, message);
                stmt.setString(3, response);
                stmt.setLong(4, currentTime);
                stmt.setLong(5, currentTime);
                stmt.executeUpdate();
            }

            // Check and limit conversations per player
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM private_conversations WHERE player_uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt(1) > config.maxConversationsPerPlayer) {
                    // Delete oldest conversations for this player
                    try (PreparedStatement deleteStmt = connection.prepareStatement(
                        "DELETE FROM private_conversations WHERE player_uuid = ? AND id IN " +
                        "(SELECT id FROM private_conversations WHERE player_uuid = ? ORDER BY timestamp ASC LIMIT ?)")) {
                        int excess = rs.getInt(1) - config.maxConversationsPerPlayer;
                        deleteStmt.setString(1, playerUuid.toString());
                        deleteStmt.setString(2, playerUuid.toString());
                        deleteStmt.setInt(3, excess);
                        deleteStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to save private message: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getRecentConversations(UUID playerUuid, int limit) {
        // For backward compatibility, use public conversations
        List<String> conversations = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM public_conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String message = rs.getString("message");
                String response = rs.getString("response");
                
                // Decrypt messages if encryption is enabled
                if (EncryptionManager.isEncryptionEnabled()) {
                    message = decryptMessage(message);
                    response = decryptMessage(response);
                }
                
                conversations.add(String.format(config.memoryFormat, message, response));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get recent conversations: {}", e.getMessage());
            e.printStackTrace();
        }
        return conversations;
    }

    public void streamRecentConversations(UUID playerUuid, int limit, Consumer<String> consumer) {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM public_conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String message = rs.getString("message");
                String response = rs.getString("response");
                
                // Decrypt messages if encryption is enabled
                if (EncryptionManager.isEncryptionEnabled()) {
                    message = decryptMessage(message);
                    response = decryptMessage(response);
                }
                
                consumer.accept(String.format(config.memoryFormat, message, response));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to stream recent conversations: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit) {
        // For backward compatibility, use public conversations
        return getPublicConversationHistory(playerUuid, limit);
    }
    
    @Override
    public List<ConversationEntry> getPublicConversationHistory(UUID playerUuid, int limit) {
        List<ConversationEntry> history = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM public_conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String message = rs.getString("message");
                String response = rs.getString("response");
                
                // Decrypt messages if encryption is enabled
                if (EncryptionManager.isEncryptionEnabled()) {
                    message = decryptMessage(message);
                    response = decryptMessage(response);
                }
                
                history.add(new ConversationEntry(message, response));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get public conversation history: {}", e.getMessage());
            e.printStackTrace();
        }
        return history;
    }
    
    @Override
    public List<ConversationEntry> getPrivateConversationHistory(UUID playerUuid, int limit) {
        List<ConversationEntry> history = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM private_conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String message = rs.getString("message");
                String response = rs.getString("response");
                
                // Decrypt messages if encryption is enabled
                if (EncryptionManager.isEncryptionEnabled()) {
                    message = decryptMessage(message);
                    response = decryptMessage(response);
                }
                
                history.add(new ConversationEntry(message, response));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get private conversation history: {}", e.getMessage());
            e.printStackTrace();
        }
        return history;
    }

    @Override
    public String decryptMessage(String encryptedMessage) {
        if (EncryptionManager.isEncryptionEnabled()) {
            return EncryptionManager.decrypt(encryptedMessage);
        }
        return encryptedMessage;
    }

    @Override
    public void deletePlayerHistory(UUID playerUuid) {
        // For backward compatibility, delete both public and private history
        deletePlayerPublicHistory(playerUuid);
        deletePlayerPrivateHistory(playerUuid);
    }
    
    @Override
    public void deletePlayerPublicHistory(UUID playerUuid) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM public_conversations WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete player public history: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete player public history", e);
        }
    }
    
    @Override
    public void deletePlayerPrivateHistory(UUID playerUuid) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM private_conversations WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete player private history: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete player private history", e);
        }
    }

    @Override
    public void deleteAllHistory() {
        // For backward compatibility, delete both public and private history
        deleteAllPublicHistory();
        deleteAllPrivateHistory();
    }
    
    @Override
    public void deleteAllPublicHistory() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM public_conversations");
        } catch (SQLException e) {
            LOGGER.error("Failed to delete all public history: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete all public history", e);
        }
    }
    
    @Override
    public void deleteAllPrivateHistory() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM private_conversations");
        } catch (SQLException e) {
            LOGGER.error("Failed to delete all private history: {}", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete all private history", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to close database connection: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanupOldMessages() {
        try (PreparedStatement stmt = connection.prepareStatement(
            "DELETE FROM public_conversations WHERE last_access < ?")) {
            long cutoffTime = (System.currentTimeMillis() / 1000) - config.maxConversationAge;
            stmt.setLong(1, cutoffTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to cleanup old public messages: {}", e.getMessage());
            e.printStackTrace();
        }
        
        try (PreparedStatement stmt = connection.prepareStatement(
            "DELETE FROM private_conversations WHERE last_access < ?")) {
            long cutoffTime = (System.currentTimeMillis() / 1000) - config.maxConversationAge;
            stmt.setLong(1, cutoffTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to cleanup old private messages: {}", e.getMessage());
            e.printStackTrace();
        }
    }
} 