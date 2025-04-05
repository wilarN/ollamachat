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
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
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
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            
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
                "INSERT INTO conversations (player_uuid, message, response, timestamp, last_access) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, message);
                stmt.setString(3, response);
                stmt.setLong(4, currentTime);
                stmt.setLong(5, currentTime);
                stmt.executeUpdate();
            }

            // Check and limit conversations per player
            try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT COUNT(*) FROM conversations WHERE player_uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt(1) > config.maxConversationsPerPlayer) {
                    // Delete oldest conversations for this player
                    try (PreparedStatement deleteStmt = connection.prepareStatement(
                        "DELETE FROM conversations WHERE player_uuid = ? AND id IN " +
                        "(SELECT id FROM conversations WHERE player_uuid = ? ORDER BY timestamp ASC LIMIT ?)")) {
                        int excess = rs.getInt(1) - config.maxConversationsPerPlayer;
                        deleteStmt.setString(1, playerUuid.toString());
                        deleteStmt.setString(2, playerUuid.toString());
                        deleteStmt.setInt(3, excess);
                        deleteStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getRecentConversations(UUID playerUuid, int limit) {
        List<String> conversations = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
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
            e.printStackTrace();
        }
        return conversations;
    }

    /**
     * Optimizes memory usage when retrieving conversations by using a streaming approach
     * This is useful for large result sets to prevent OutOfMemoryError
     */
    public void streamRecentConversations(UUID playerUuid, int limit, Consumer<String> consumer) {
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            
            // Set fetch size to optimize memory usage
            stmt.setFetchSize(10);
            
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
            e.printStackTrace();
        }
    }

    @Override
    public List<ConversationEntry> getConversationHistory(UUID playerUuid, int limit) {
        List<ConversationEntry> history = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT message, response FROM conversations WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?")) {
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
    public void saveMessage(UUID playerUuid, String message, String response) {
        // Implementation for saving a message
        // This can be the same as addMessage or have different logic
        addMessage(playerUuid, message, response);
    }

    @Override
    public void deletePlayerHistory(UUID playerUuid) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM conversations WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete player history", e);
        }
    }

    @Override
    public void deleteAllHistory() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM conversations");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete all history", e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
} 