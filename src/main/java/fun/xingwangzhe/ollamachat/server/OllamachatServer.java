package fun.xingwangzhe.ollamachat.server;

import fun.xingwangzhe.ollamachat.chat.MessageCooldownManager;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.Database;
import fun.xingwangzhe.ollamachat.database.LocalDatabase;
import fun.xingwangzhe.ollamachat.database.ExternalDatabase;
import fun.xingwangzhe.ollamachat.database.ConversationEntry;
import fun.xingwangzhe.ollamachat.database.DummyDatabase;
import fun.xingwangzhe.ollamachat.security.EncryptionManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.util.Formatting;
import java.util.concurrent.CompletableFuture;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collections;

public class OllamachatServer implements DedicatedServerModInitializer {
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static ModConfig config;
    private static MinecraftServer server;
    private static String currentModel;
    private static Database database;
    private static final int CONTEXT_HISTORY_LIMIT = 5; // Number of previous messages to include as context
    private static MessageCooldownManager cooldownManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamachatServer.class);

    @Override
    public void onInitializeServer() {
        config = ModConfig.load();
        currentModel = config.defaultModel;
        
        // Initialize encryption
        EncryptionManager.initialize(config);
        
        // Initialize database
        try {
            LOGGER.info("Database type from config: {}", config.databaseType);
            LOGGER.info("Database configuration - Host: {}, Port: {}, Name: {}, Username: {}", 
                config.databaseHost, config.databasePort, config.databaseName, config.databaseUsername);
            
            // Check if the database type is "external" (case-insensitive)
            if ("external".equalsIgnoreCase(config.databaseType)) {
                LOGGER.info("Using external database");
                try {
                    database = new ExternalDatabase(config);
                    LOGGER.info("External database initialized successfully");
                } catch (SQLException e) {
                    if (e.getMessage().contains("MySQL driver not found")) {
                        LOGGER.error("MySQL driver not found. Please install Kosmolot's MySQL mod: https://modrinth.com/mod/kosmolot-mysql");
                        LOGGER.error("Falling back to local database");
                    } else {
                        LOGGER.error("Failed to initialize external database: {}", e.getMessage());
                        LOGGER.error("Falling back to local database");
                    }
                    try {
                        database = new LocalDatabase(config);
                    } catch (SQLException sqliteException) {
                        if (sqliteException.getMessage().contains("SQLite driver not found")) {
                            LOGGER.error("SQLite driver not found. Please install Kosmolot's SQLite mod: https://modrinth.com/mod/kosmolot-sqlite");
                            LOGGER.error("Database initialization failed. The mod will continue without database support.");
                            // Create a dummy database implementation that does nothing
                            database = new DummyDatabase();
                        } else {
                            throw sqliteException;
                        }
                    }
                }
            } else {
                LOGGER.info("Using local database");
                try {
                    database = new LocalDatabase(config);
                } catch (SQLException e) {
                    if (e.getMessage().contains("SQLite driver not found")) {
                        LOGGER.error("SQLite driver not found. Please install Kosmolot's SQLite mod: https://modrinth.com/mod/kosmolot-sqlite");
                        LOGGER.error("Database initialization failed. The mod will continue without database support.");
                        // Create a dummy database implementation that does nothing
                        database = new DummyDatabase();
                    } else {
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize database: {}", e.getMessage());
            e.printStackTrace();
            // Fallback to in-memory database or handle the error appropriately
            throw new RuntimeException("Failed to initialize database", e);
        }
        
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            OllamachatServer.server = server;
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (database != null) {
                database.close();
            }
        });

        // Initialize cooldown manager
        cooldownManager = new MessageCooldownManager(config);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (environment.dedicated) {
                // Register the "ollama" command
                dispatcher.register(literal("ollama")
                        .requires(source -> !config.requireOpForCommands || source.hasPermissionLevel(config.opPermissionLevel))
                        .then(literal("list")
                                .executes(context -> {
                                    context.getSource().sendMessage(Text.literal("Listing available models...")
                                            .formatted(Formatting.YELLOW));
                                    return 1;
                                }))
                        .then(literal("model")
                                .then(literal("name")
                                        .then(argument("modelname", StringArgumentType.string())
                                                .executes(context -> {
                                                    String modelName = StringArgumentType.getString(context, "modelname");
                                                    currentModel = modelName;
                                                    context.getSource().sendMessage(Text.literal("Setting model to: ")
                                                            .formatted(Formatting.YELLOW)
                                                            .append(Text.literal(modelName)
                                                                    .formatted(Formatting.GREEN)));
                                                    return 1;
                                                }))))
                        .then(literal("history")
                                .then(argument("limit", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int limit = IntegerArgumentType.getInteger(context, "limit");
                                            UUID playerId = context.getSource().getPlayer().getUuid();
                                            var history = database.getConversationHistory(playerId, limit);
                                            
                                            // Get the formatting for the AI response
                                            Formatting responseFormatting = getFormattingFromString(config.responseColor);
                                            
                                            context.getSource().sendMessage(Text.literal("=== Conversation History ===")
                                                    .formatted(Formatting.GOLD, Formatting.BOLD));
                                            for (var entry : history) {
                                                context.getSource().sendMessage(Text.literal("You: ")
                                                        .formatted(Formatting.GREEN)
                                                        .append(Text.literal(entry.getMessage())
                                                                .formatted(Formatting.WHITE)));
                                                context.getSource().sendMessage(Text.literal("AI: ")
                                                        .formatted(responseFormatting)
                                                        .append(Text.literal(entry.getResponse())
                                                                .formatted(Formatting.WHITE)));
                                            }
                                            return 1;
                                        })))
                        .then(literal("serve")
                                .executes(context -> {
                                    context.getSource().sendMessage(Text.literal("Starting Ollama service...")
                                            .formatted(Formatting.YELLOW));
                                    return 1;
                                }))
                        .then(literal("ps")
                                .executes(context -> {
                                    context.getSource().sendMessage(Text.literal("Listing running models...")
                                            .formatted(Formatting.YELLOW));
                                    return 1;
                                }))
                        .then(literal("reload")
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    
                                    // Reload the configuration
                                    config = ModConfig.load();
                                    
                                    // Reinitialize encryption
                                    EncryptionManager.initialize(config);
                                    
                                    // Update the current model
                                    currentModel = config.defaultModel;
                                    
                                    // Reinitialize the cooldown manager
                                    cooldownManager = new MessageCooldownManager(config);
                                    
                                    // Send confirmation message
                                    source.sendMessage(Text.literal("OllamaChat configuration reloaded successfully!")
                                            .formatted(Formatting.GREEN));
                                    
                                    // Log the reload
                                    LOGGER.info("OllamaChat configuration reloaded by {}", source.getName());
                                    
                                    return 1;
                                }))
                        .then(literal("clearall")
                                .requires(source -> source.hasPermissionLevel(config.opPermissionLevel))
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    
                                    // Delete all chat history
                                    try {
                                        database.deleteAllPublicHistory();
                                        database.deleteAllPrivateHistory();
                                        source.sendMessage(Text.literal("All chat history has been cleared.")
                                                .formatted(Formatting.GREEN));
                                        return 1;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        source.sendMessage(Text.literal("Error clearing all chat history: ")
                                                .formatted(Formatting.RED)
                                                .append(Text.literal(e.getMessage())
                                                        .formatted(Formatting.RED)));
                                        return 0;
                                    }
                                }))
                );

                // Register the AI command with custom prefix from config
                dispatcher.register(literal(config.aiCommandPrefix)
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    UUID playerId = source.getPlayer().getUuid();
                                    String playerName = source.getPlayer().getName().getString();
                                    
                                    // Check cooldown
                                    if (!cooldownManager.canSendMessage(playerId)) {
                                        source.sendMessage(Text.literal(cooldownManager.getCooldownMessage(playerId))
                                                .formatted(Formatting.RED));
                                        return 1;
                                    }
                                    
                                    // Get the message from the argument
                                    String message = StringArgumentType.getString(context, "message");
                                    
                                    // Get the AI name from config
                                    String aiName = config.aiCommandPrefix;
                                    
                                    // Broadcast the player's message to all players with the new format
                                    Text playerMessage = Text.literal(playerName)
                                            .formatted(Formatting.GREEN)
                                            .append(Text.literal(" >> "))
                                            .append(Text.literal(aiName)
                                                    .formatted(getFormattingFromString(config.prefixColor)))
                                            .append(Text.literal(": "))
                                            .append(Text.literal(message)
                                                    .formatted(Formatting.WHITE));
                                    server.getPlayerManager().broadcast(playerMessage, false);
                                    
                                    // Add username to the message for the AI
                                    String messageWithUsername = playerName + ": " + message;
                                    
                                    // Send message to AI
                                    CompletableFuture.runAsync(() -> {
                                        try {
                                            // Process message and get response
                                            String response = handleAIRequest(source, messageWithUsername, false);
                                            
                                            // Update cooldown
                                            cooldownManager.updateLastMessageTime(playerId);
                                            
                                            // Save to database if memory is enabled
                                            if (config.enableMemory) {
                                                // For public chat, use the player's actual UUID
                                                database.savePublicMessage(playerId, messageWithUsername, response);
                                            }
                                            
                                            // Send response to all players with the new format
                                            source.getServer().execute(() -> {
                                                // Create a colorful text with the AI response
                                                Text colorfulText;
                                                if (config.enableChatPrefix) {
                                                    // Get the AI name from config
                                                    String aiNameForResponse = config.aiCommandPrefix;
                                                    
                                                    // Get the formatting for the AI name and response
                                                    Formatting aiNameFormatting = getFormattingFromString(config.prefixColor);
                                                    Formatting responseFormatting = getFormattingFromString(config.responseColor);
                                                    
                                                    // Create a colorful text with the AI name and response
                                                    colorfulText = Text.literal(aiNameForResponse)
                                                            .formatted(aiNameFormatting)
                                                            .append(Text.literal(" >> "))
                                                            .append(Text.literal(playerName)
                                                                    .formatted(Formatting.GREEN))
                                                            .append(Text.literal(": "))
                                                            .append(Text.literal(response)
                                                                    .formatted(responseFormatting));
                                                } else {
                                                    // Just color the response
                                                    Formatting responseFormatting = getFormattingFromString(config.responseColor);
                                                    colorfulText = Text.literal(response)
                                                            .formatted(responseFormatting);
                                                }
                                                
                                                // Broadcast the AI response to all players
                                                server.getPlayerManager().broadcast(colorfulText, false);
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            source.getServer().execute(() -> {
                                                source.sendMessage(Text.literal("An error occurred while processing your message.")
                                                        .formatted(Formatting.RED));
                                            });
                                        }
                                    });
                                    
                                    return 1;
                                }))
                        .then(literal("clear")
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    UUID playerId = source.getPlayer().getUuid();
                                    
                                    // Delete the player's chat history
                                    try {
                                        database.deletePlayerPublicHistory(playerId);
                                        database.deletePlayerPrivateHistory(playerId);
                                        source.sendMessage(Text.literal("Your chat history has been cleared.")
                                                .formatted(Formatting.GREEN));
                                        return 1;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        source.sendMessage(Text.literal("Error clearing chat history: ")
                                                .formatted(Formatting.RED)
                                                .append(Text.literal(e.getMessage())
                                                        .formatted(Formatting.RED)));
                                        return 0;
                                    }
                                }))
                        .then(literal("history")
                                .then(argument("limit", IntegerArgumentType.integer(1, 30))
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            UUID playerId = source.getPlayer().getUuid();
                                            int limit = IntegerArgumentType.getInteger(context, "limit");
                                            
                                            // Get the player's public conversation history
                                            List<ConversationEntry> publicHistory = database.getPublicConversationHistory(playerId, limit);
                                            
                                            // Get the player's private conversation history
                                            List<ConversationEntry> privateHistory = database.getPrivateConversationHistory(playerId, limit);
                                            
                                            // Get the formatting for the AI response
                                            Formatting responseFormatting = getFormattingFromString(config.responseColor);
                                            
                                            // Display public conversation history
                                            if (!publicHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("=== Public Conversation History ===")
                                                        .formatted(Formatting.GOLD, Formatting.BOLD));
                                                
                                                // Create a copy of the list and reverse it to show oldest first
                                                List<ConversationEntry> chronologicalPublicHistory = new ArrayList<>(publicHistory);
                                                Collections.reverse(chronologicalPublicHistory);
                                                
                                                for (ConversationEntry entry : chronologicalPublicHistory) {
                                                    source.sendMessage(Text.literal("You: ")
                                                            .formatted(Formatting.GREEN)
                                                            .append(Text.literal(entry.getMessage())
                                                                    .formatted(Formatting.WHITE)));
                                                    source.sendMessage(Text.literal("AI: ")
                                                            .formatted(responseFormatting)
                                                            .append(Text.literal(entry.getResponse())
                                                                    .formatted(Formatting.WHITE)));
                                                }
                                            }
                                            
                                            // Display private conversation history
                                            if (!privateHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("=== Private Conversation History ===")
                                                        .formatted(Formatting.GOLD, Formatting.BOLD));
                                                
                                                // Create a copy of the list and reverse it to show oldest first
                                                List<ConversationEntry> chronologicalPrivateHistory = new ArrayList<>(privateHistory);
                                                Collections.reverse(chronologicalPrivateHistory);
                                                
                                                for (ConversationEntry entry : chronologicalPrivateHistory) {
                                                    source.sendMessage(Text.literal("You: ")
                                                            .formatted(Formatting.GREEN)
                                                            .append(Text.literal(entry.getMessage())
                                                                    .formatted(Formatting.WHITE)));
                                                    source.sendMessage(Text.literal("AI: ")
                                                            .formatted(responseFormatting)
                                                            .append(Text.literal(entry.getResponse())
                                                                    .formatted(Formatting.WHITE)));
                                                }
                                            }
                                            
                                            // If no history is found
                                            if (publicHistory.isEmpty() && privateHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("No conversation history found.")
                                                        .formatted(Formatting.YELLOW));
                                            }
                                            
                                            return 1;
                                        })))
                        .then(literal("help")
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    
                                    // Get the AI name from config
                                    String aiName = config.aiCommandPrefix;
                                    String privatePrefix = "p" + config.aiCommandPrefix;
                                    
                                    // Get the formatting for the AI name
                                    Formatting aiNameFormatting = getFormattingFromString(config.prefixColor);
                                    
                                    // Send comprehensive help information
                                    source.sendMessage(Text.literal("=== OllamaChat Help ===")
                                            .formatted(Formatting.GOLD, Formatting.BOLD));
                                    
                                    // Basic commands
                                    source.sendMessage(Text.literal("Basic Commands:")
                                            .formatted(Formatting.YELLOW, Formatting.BOLD));
                                    source.sendMessage(Text.literal("/" + aiName + " <message>")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal(" - Chat with the AI in public chat (visible to all players)")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("/" + privatePrefix + " <message>")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal(" - Chat with the AI in private chat (visible only to you)")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("/" + aiName + " clear")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal(" - Clear your chat history")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("/" + aiName + " history <1-30>")
                                            .formatted(Formatting.YELLOW)
                                            .append(Text.literal(" - View your conversation history")
                                                    .formatted(Formatting.WHITE)));
                                    
                                    // Features
                                    source.sendMessage(Text.literal("\nFeatures:")
                                            .formatted(Formatting.YELLOW, Formatting.BOLD));
                                    source.sendMessage(Text.literal("• Public Chat: ")
                                            .formatted(Formatting.WHITE)
                                            .append(Text.literal("Messages are visible to all players and saved in a shared history")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("• Private Chat: ")
                                            .formatted(Formatting.WHITE)
                                            .append(Text.literal("Messages are visible only to you and saved in your personal history")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("• Memory: ")
                                            .formatted(Formatting.WHITE)
                                            .append(Text.literal(config.enableMemory ? "Enabled" : "Disabled")
                                                    .formatted(config.enableMemory ? Formatting.GREEN : Formatting.RED))
                                            .append(Text.literal(" - The AI remembers previous conversations")
                                                    .formatted(Formatting.WHITE)));
                                    source.sendMessage(Text.literal("• Cooldown: ")
                                            .formatted(Formatting.WHITE)
                                            .append(Text.literal(config.messageCooldown + " seconds")
                                                    .formatted(Formatting.GREEN))
                                            .append(Text.literal(" between messages")
                                                    .formatted(Formatting.WHITE)));
                                    
                                    return 1;
                                }))
                );

                // Register the private AI command with custom prefix from config
                dispatcher.register(literal("p" + config.aiCommandPrefix)
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    UUID playerId = source.getPlayer().getUuid();
                                    String playerName = source.getPlayer().getName().getString();
                                    
                                    // Check cooldown
                                    if (!cooldownManager.canSendMessage(playerId)) {
                                        source.sendMessage(Text.literal(cooldownManager.getCooldownMessage(playerId))
                                                .formatted(Formatting.RED));
                                        return 1;
                                    }
                                    
                                    // Get the message from the argument
                                    String message = StringArgumentType.getString(context, "message");
                                    
                                    // Get the AI name from config
                                    String aiName = config.aiCommandPrefix;
                                    
                                    // Send private message to the player with the new format
                                    Text playerMessage = Text.literal(playerName)
                                            .formatted(Formatting.GREEN)
                                            .append(Text.literal(" >> "))
                                            .append(Text.literal(aiName)
                                                    .formatted(getFormattingFromString(config.prefixColor)))
                                            .append(Text.literal(": "))
                                            .append(Text.literal(message)
                                                    .formatted(Formatting.WHITE));
                                    source.sendMessage(playerMessage);
                                    
                                    // Add username to the message for the AI
                                    String messageWithUsername = playerName + ": " + message;
                                    
                                    // Send message to AI
                                    CompletableFuture.runAsync(() -> {
                                        try {
                                            // Process message and get response
                                            String response = handleAIRequest(source, messageWithUsername, true);
                                            
                                            // Update cooldown
                                            cooldownManager.updateLastMessageTime(playerId);
                                            
                                            // Save to database if memory is enabled
                                            if (config.enableMemory) {
                                                // For private chat, use the player's actual UUID
                                                database.savePrivateMessage(playerId, messageWithUsername, response);
                                            }
                                            
                                            // Send response to player (private) with the new format
                                            source.getServer().execute(() -> {
                                                // Create a colorful text with the AI response
                                                Text colorfulText;
                                                if (config.enableChatPrefix) {
                                                    // Get the AI name from config
                                                    String aiNameForPrivateResponse = config.aiCommandPrefix;
                                                    
                                                    // Get the formatting for the AI name and response
                                                    Formatting aiNameFormatting = getFormattingFromString(config.prefixColor);
                                                    Formatting responseFormatting = getFormattingFromString(config.privateResponseColor);
                                                    
                                                    // Create a colorful text with the AI name and response
                                                    colorfulText = Text.literal(aiNameForPrivateResponse)
                                                            .formatted(aiNameFormatting)
                                                            .append(Text.literal(" >> "))
                                                            .append(Text.literal(playerName)
                                                                    .formatted(Formatting.GREEN))
                                                            .append(Text.literal(": "))
                                                            .append(Text.literal(response)
                                                                    .formatted(responseFormatting));
                                                } else {
                                                    // Just color the response
                                                    Formatting responseFormatting = getFormattingFromString(config.privateResponseColor);
                                                    colorfulText = Text.literal(response)
                                                            .formatted(responseFormatting);
                                                }
                                                
                                                // Send private message to the player
                                                source.sendMessage(colorfulText);
                                            });
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            source.getServer().execute(() -> {
                                                source.sendMessage(Text.literal("An error occurred while processing your message.")
                                                        .formatted(Formatting.RED));
                                            });
                                        }
                                    });
                                    
                                    return 1;
                                }))
                        .then(literal("clear")
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    UUID playerId = source.getPlayer().getUuid();
                                    
                                    // Delete the player's chat history
                                    try {
                                        database.deletePlayerPublicHistory(playerId);
                                        database.deletePlayerPrivateHistory(playerId);
                                        source.sendMessage(Text.literal("Your chat history has been cleared.")
                                                .formatted(Formatting.GREEN));
                                        return 1;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        source.sendMessage(Text.literal("Error clearing chat history: ")
                                                .formatted(Formatting.RED)
                                                .append(Text.literal(e.getMessage())
                                                        .formatted(Formatting.RED)));
                                        return 0;
                                    }
                                }))
                        .then(literal("history")
                                .then(argument("limit", IntegerArgumentType.integer(1, 30))
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            UUID playerId = source.getPlayer().getUuid();
                                            int limit = IntegerArgumentType.getInteger(context, "limit");
                                            
                                            // Get the player's public conversation history
                                            List<ConversationEntry> publicHistory = database.getPublicConversationHistory(playerId, limit);
                                            
                                            // Get the player's private conversation history
                                            List<ConversationEntry> privateHistory = database.getPrivateConversationHistory(playerId, limit);
                                            
                                            // Get the formatting for the AI response
                                            Formatting responseFormatting = getFormattingFromString(config.responseColor);
                                            
                                            // Display public conversation history
                                            if (!publicHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("=== Public Conversation History ===")
                                                        .formatted(Formatting.GOLD, Formatting.BOLD));
                                                
                                                // Create a copy of the list and reverse it to show oldest first
                                                List<ConversationEntry> chronologicalPublicHistory = new ArrayList<>(publicHistory);
                                                Collections.reverse(chronologicalPublicHistory);
                                                
                                                for (ConversationEntry entry : chronologicalPublicHistory) {
                                                    source.sendMessage(Text.literal("You: ")
                                                            .formatted(Formatting.GREEN)
                                                            .append(Text.literal(entry.getMessage())
                                                                    .formatted(Formatting.WHITE)));
                                                    source.sendMessage(Text.literal("AI: ")
                                                            .formatted(responseFormatting)
                                                            .append(Text.literal(entry.getResponse())
                                                                    .formatted(Formatting.WHITE)));
                                                }
                                            }
                                            
                                            // Display private conversation history
                                            if (!privateHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("=== Private Conversation History ===")
                                                        .formatted(Formatting.GOLD, Formatting.BOLD));
                                                
                                                // Create a copy of the list and reverse it to show oldest first
                                                List<ConversationEntry> chronologicalPrivateHistory = new ArrayList<>(privateHistory);
                                                Collections.reverse(chronologicalPrivateHistory);
                                                
                                                for (ConversationEntry entry : chronologicalPrivateHistory) {
                                                    source.sendMessage(Text.literal("You: ")
                                                            .formatted(Formatting.GREEN)
                                                            .append(Text.literal(entry.getMessage())
                                                                    .formatted(Formatting.WHITE)));
                                                    source.sendMessage(Text.literal("AI: ")
                                                            .formatted(responseFormatting)
                                                            .append(Text.literal(entry.getResponse())
                                                                    .formatted(Formatting.WHITE)));
                                                }
                                            }
                                            
                                            // If no history is found
                                            if (publicHistory.isEmpty() && privateHistory.isEmpty()) {
                                                source.sendMessage(Text.literal("No conversation history found.")
                                                        .formatted(Formatting.YELLOW));
                                            }
                                            
                                            return 1;
                                        })))
                );

                // Register the help command
                dispatcher.register(literal("help")
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("=== Ollamachat Commands ===")
                                    .formatted(Formatting.GOLD, Formatting.BOLD));
                            context.getSource().sendMessage(Text.literal("/ollama list")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - List available AI models")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/ollama model <modelname>")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Set the AI model to use")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/ollama history <limit>")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Show conversation history")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/ollama clear")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Delete your chat history")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/ollama clearall")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Delete all chat history (admin only)")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/" + config.aiCommandPrefix + " <message>")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Chat with the AI (public)")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/p" + config.aiCommandPrefix + " <message>")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Chat with the AI (private)")
                                            .formatted(Formatting.WHITE)));
                            context.getSource().sendMessage(Text.literal("/" + config.aiCommandPrefix + " clear")
                                    .formatted(Formatting.YELLOW)
                                    .append(Text.literal(" - Delete your chat history")
                                            .formatted(Formatting.WHITE)));
                            return 1;
                        }));
            }
        });
    }

    private String handleAIRequest(ServerCommandSource source, String message, boolean isPrivate) {
        try {
            UUID playerId = source.getPlayer().getUuid();
            String playerName = source.getPlayer().getName().getString();
            
            // Get conversation history for context based on whether it's private or public
            List<ConversationEntry> history;
            if (isPrivate) {
                history = database.getPrivateConversationHistory(playerId, config.memoryHistoryLimit);
            } else {
                history = database.getPublicConversationHistory(playerId, config.memoryHistoryLimit);
            }
            
            // Create a more explicit context that clearly shows who said what
            StringBuilder contextBuilder = new StringBuilder();
            
            if (!history.isEmpty()) {
                contextBuilder.append("Previous conversation:\n");
                
                for (ConversationEntry entry : history) {
                    // Extract username from the message if it's in the format "username: message"
                    String messageText = entry.getMessage();
                    String responseText = entry.getResponse();
                    
                    // Check if the message already has a username prefix
                    if (messageText.contains(": ")) {
                        // Message already has a username prefix, use it as is
                        contextBuilder.append(messageText).append("\n");
                    } else {
                        // Add a generic "User" prefix if no username is found
                        contextBuilder.append("User: ").append(messageText).append("\n");
                    }
                    
                    // Format the AI response
                    contextBuilder.append("AI: ").append(responseText).append("\n\n");
                }
            }
            
            // Add the current message with clear indication of who is speaking
            contextBuilder.append("Message from ").append(playerName).append(": ").append(message);
            contextBuilder.append("\n\nPlease provide a direct response without using a conversation format or role-playing.");
            
            String prompt = contextBuilder.toString();

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", currentModel);
            requestBody.addProperty("prompt", prompt);
            requestBody.addProperty("stream", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.ollamaApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    String aiResponse = jsonResponse.get("response").getAsString();
                    
                    // Process response based on config
                    if (config.stripHtmlTags) {
                        aiResponse = aiResponse.replaceAll("<[^>]*>", "");
                    }
                    if (aiResponse.length() > config.maxResponseLength) {
                        aiResponse = aiResponse.substring(0, config.maxResponseLength) + "...";
                    }
                    
                    // Save the message and response to the appropriate database
                    if (isPrivate) {
                        database.savePrivateMessage(playerId, message, aiResponse);
                    } else {
                        database.savePublicMessage(playerId, message, aiResponse);
                    }
                    
                    return aiResponse;
                } else {
                    return "Error: " + response.statusCode();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "Error connecting to Ollama service: " + e.getMessage();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing AI request: " + e.getMessage();
        }
    }

    /**
     * Converts a string color name to a Minecraft Formatting
     * 
     * @param colorName The name of the color
     * @return The corresponding Formatting, or WHITE if not found
     */
    private static Formatting getFormattingFromString(String colorName) {
        try {
            return Formatting.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid color name: " + colorName + ", using WHITE instead");
            return Formatting.WHITE;
        }
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static MinecraftServer getServer() {
        return server;
    }
} 