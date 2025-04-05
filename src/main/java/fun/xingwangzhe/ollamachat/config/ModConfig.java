package fun.xingwangzhe.ollamachat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ModConfig {
    // Ollama API settings
    @SerializedName("ollamaApiUrl") // The URL of your Ollama API server
    public String ollamaApiUrl = "http://localhost:11434/api/generate";
    
    @SerializedName("defaultModel") // The default AI model to use (e.g., llama3, mistral, etc.)
    public String defaultModel = "llama3";
    
    // Command settings
    @SerializedName("requireOpForCommands") // Whether commands require operator permissions
    public boolean requireOpForCommands = true;
    
    @SerializedName("opPermissionLevel") // The required operator level (1-4)
    public int opPermissionLevel = 4;
    
    @SerializedName("aiCommandPrefix") // The prefix for the AI command (e.g., "ai", "chat", "bot")
    public String aiCommandPrefix = "ai";
    
    // Chat settings
    @SerializedName("enableChatPrefix") // Whether to add a prefix to AI responses
    public boolean enableChatPrefix = true;
    
    @SerializedName("chatPrefix") // The prefix to use for AI responses
    public String chatPrefix = "[AI]";
    
    @SerializedName("maxResponseLength") // Maximum length of AI responses (characters)
    public int maxResponseLength = 1000;
    
    @SerializedName("stripHtmlTags") // Whether to remove HTML tags from responses
    public boolean stripHtmlTags = true;
    
    // Message cooldown settings
    @SerializedName("messageCooldown")
    public int messageCooldown = 5; // Cooldown time in seconds between messages
    
    @SerializedName("cooldownMessage")
    public String cooldownMessage = "Please wait %d seconds before sending another message to the AI.";
    
    // Memory settings
    @SerializedName("enableMemory") // Whether to use conversation history
    public boolean enableMemory = true;
    
    @SerializedName("memoryHistoryLimit") // Number of previous messages to include in context
    public int memoryHistoryLimit = 5;
    
    @SerializedName("memoryFormat") // Format for each message in history. Use {message} and {response} as placeholders
    public String memoryFormat = "User: {message}\nAssistant: {response}";
    
    // Memory optimization settings
    @SerializedName("maxContextTokens")
    public int maxContextTokens = 4096; // Maximum tokens to include in context
    
    @SerializedName("maxMessageLength")
    public int maxMessageLength = 500; // Maximum length of stored messages
    
    @SerializedName("messageCompression")
    public boolean messageCompression = true; // Whether to compress long messages
    
    @SerializedName("cleanupInterval")
    public int cleanupInterval = 3600; // How often to clean up old messages (in seconds)
    
    @SerializedName("maxConversationAge")
    public int maxConversationAge = 604800; // Maximum age of conversations to keep (in seconds, default 7 days)
    
    @SerializedName("maxConversationsPerPlayer")
    public int maxConversationsPerPlayer = 100; // Maximum number of conversations to keep per player
    
    // Database settings
    @SerializedName("databaseType") // Type of database to use: "local" for SQLite or "external" for MySQL
    public String databaseType = "local";
    
    @SerializedName("databaseHost") // MySQL server host (only used for external database)
    public String databaseHost = "localhost";
    
    @SerializedName("databasePort") // MySQL server port (only used for external database)
    public int databasePort = 3306;
    
    @SerializedName("databaseName") // MySQL database name (only used for external database)
    public String databaseName = "ollamachat";
    
    @SerializedName("databaseUsername") // MySQL username (only used for external database)
    public String databaseUsername = "root";
    
    @SerializedName("databasePassword") // MySQL password (only used for external database)
    public String databasePassword = "";
    
    @SerializedName("localDatabasePath") // Path to the SQLite database file (only used for local database)
    public String localDatabasePath = "data/ollamachat/conversations.db";
    
    // Encryption settings
    @SerializedName("enableEncryption") // Whether to encrypt messages
    public boolean enableEncryption = true;
    
    @SerializedName("encryptionKey") // Base64 encoded encryption key (if empty, a new key will be generated)
    public String encryptionKey = "";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final String CONFIG_PATH = "config/ollamachat.json";

    public static ModConfig load() {
        try {
            Path configPath = Paths.get(CONFIG_PATH);
            if (!Files.exists(configPath)) {
                ModConfig defaultConfig = new ModConfig();
                defaultConfig.save();
                return defaultConfig;
            }

            try (FileReader reader = new FileReader(configPath.toFile())) {
                return GSON.fromJson(reader, ModConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ModConfig();
        }
    }

    public void save() {
        try {
            Path configPath = Paths.get(CONFIG_PATH);
            Files.createDirectories(configPath.getParent());
            
            try (FileWriter writer = new FileWriter(configPath.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
} 