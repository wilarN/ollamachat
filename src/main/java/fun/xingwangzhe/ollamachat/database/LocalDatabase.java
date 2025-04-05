package fun.xingwangzhe.ollamachat.database;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.memory.MemoryOptimizer;
import fun.xingwangzhe.ollamachat.security.EncryptionManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalDatabase implements Database {
    private final Connection connection;
    private final MemoryOptimizer memoryOptimizer;
    private final ModConfig config;
    private long lastCleanupTime;

    public LocalDatabase(ModConfig config) throws SQLException {
        this.config = config;
        this.memoryOptimizer = new MemoryOptimizer(config);
        this.lastCleanupTime = System.currentTimeMillis() / 1000;
        
        // Create database directory if it doesn't exist
        java.io.File dbFile = new java.io.File(config.localDatabasePath);
        dbFile.getParentFile().mkdirs();
        
        // Connect to SQLite database
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + config.localDatabasePath);
        } catch (SQLException e) {
            if (e.getMessage().contains("No suitable driver")) {
                System.err.println("SQLite driver not found. Please install Kosmolot's SQLite mod: https://modrinth.com/mod/kosmolot-sqlite");
                throw new SQLException("SQLite driver not found. Please install Kosmolot's SQLite mod: https://modrinth.com/mod/kosmolot-sqlite", e);
            }
            throw e;
        }
        
        // Check if the database is new or needs schema update
        boolean isNewDatabase = !dbFile.exists() || dbFile.length() == 0;
        
        // Create tables if they don't exist
        try (Statement stmt = connection.createStatement()) {
            if (isNewDatabase) {
                // Create new tables
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        message TEXT NOT NULL,
                        response TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        last_access INTEGER NOT NULL
                    )
                """);
                
                // Create index for faster lookups
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON conversations(player_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_last_access ON conversations(last_access)");
            } else {
                // Check if the table exists and has the correct schema
                try {
                    stmt.execute("SELECT player_uuid FROM conversations LIMIT 1");
                } catch (SQLException e) {
                    // Table doesn't exist or has wrong schema, recreate it
                    stmt.execute("DROP TABLE IF EXISTS conversations");
                    stmt.execute("""
                        CREATE TABLE conversations (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid TEXT NOT NULL,
                            message TEXT NOT NULL,
                            response TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            last_access INTEGER NOT NULL
                        )
                    """);
                    
                    // Create index for faster lookups
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON conversations(player_uuid)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_last_access ON conversations(last_access)");
                }
            }
        }
    }

    @Override
    public void addMessage(UUID playerUuid, String message, String response) {
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

    private void cleanupOldMessages() {
        try (PreparedStatement stmt = connection.prepareStatement(
            "DELETE FROM conversations WHERE last_access < ?")) {
            long cutoffTime = (System.currentTimeMillis() / 1000) - config.maxConversationAge;
            stmt.setLong(1, cutoffTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        // Implementation for closing the database connection
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String decryptMessage(String encryptedMessage) {
        if (EncryptionManager.isEncryptionEnabled()) {
            return EncryptionManager.decrypt(encryptedMessage);
        }
        return encryptedMessage;
    }
} 