package fun.xingwangzhe.ollamachat.server;

import fun.xingwangzhe.ollamachat.chat.MessageCooldownManager;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.Database;
import fun.xingwangzhe.ollamachat.database.LocalDatabase;
import fun.xingwangzhe.ollamachat.database.ExternalDatabase;
import fun.xingwangzhe.ollamachat.database.ConversationEntry;
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
                    LOGGER.error("Failed to initialize external database: {}", e.getMessage());
                    LOGGER.error("Falling back to local database");
                    database = new LocalDatabase(config);
                }
            } else {
                LOGGER.info("Using local database");
                database = new LocalDatabase(config);
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
                                    context.getSource().sendMessage(Text.literal("Listing available models..."));
                                    return 1;
                                }))
                        .then(literal("model")
                                .then(literal("name")
                                        .then(argument("modelname", StringArgumentType.string())
                                                .executes(context -> {
                                                    String modelName = StringArgumentType.getString(context, "modelname");
                                                    currentModel = modelName;
                                                    context.getSource().sendMessage(Text.literal("Setting model to: " + modelName));
                                                    return 1;
                                                }))))
                        .then(literal("history")
                                .then(argument("limit", IntegerArgumentType.integer(1))
                                        .executes(context -> {
                                            int limit = IntegerArgumentType.getInteger(context, "limit");
                                            UUID playerId = context.getSource().getPlayer().getUuid();
                                            var history = database.getConversationHistory(playerId, limit);
                                            
                                            context.getSource().sendMessage(Text.literal("=== Conversation History ==="));
                                            for (var entry : history) {
                                                context.getSource().sendMessage(Text.literal("You: " + entry.getMessage()));
                                                context.getSource().sendMessage(Text.literal("AI: " + entry.getResponse()));
                                            }
                                            return 1;
                                        })))
                        .then(literal("serve")
                                .executes(context -> {
                                    context.getSource().sendMessage(Text.literal("Starting Ollama service..."));
                                    return 1;
                                }))
                        .then(literal("ps")
                                .executes(context -> {
                                    context.getSource().sendMessage(Text.literal("Listing running models..."));
                                    return 1;
                                }))
                );

                // Register the AI command with custom prefix from config
                dispatcher.register(literal(config.aiCommandPrefix)
                        .then(argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();
                                    UUID playerId = source.getPlayer().getUuid();
                                    
                                    // Check cooldown
                                    if (!cooldownManager.canSendMessage(playerId)) {
                                        source.sendMessage(Text.literal(cooldownManager.getCooldownMessage(playerId))
                                                .formatted(Formatting.RED));
                                        return 1;
                                    }
                                    
                                    // Get the message from the argument
                                    String message = StringArgumentType.getString(context, "message");
                                    
                                    // Send message to AI
                                    CompletableFuture.runAsync(() -> {
                                        try {
                                            // Get conversation history if memory is enabled
                                            String conversationContext = "";
                                            if (config.enableMemory) {
                                                var history = database.getRecentConversations(playerId, config.memoryHistoryLimit);
                                                conversationContext = String.join("\n", history);
                                            }
                                            
                                            // Process message and get response
                                            String response = handleAIRequest(source, message);
                                            
                                            // Update cooldown
                                            cooldownManager.updateLastMessageTime(playerId);
                                            
                                            // Save to database if memory is enabled
                                            if (config.enableMemory) {
                                                database.addMessage(playerId, message, response);
                                            }
                                            
                                            // Send response to player
                                            source.getServer().execute(() -> {
                                                String formattedResponse = config.enableChatPrefix 
                                                    ? config.chatPrefix + " " + response 
                                                    : response;
                                                source.sendMessage(Text.literal(formattedResponse));
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
                                        database.deletePlayerHistory(playerId);
                                        source.sendMessage(Text.literal("Your chat history has been cleared."));
                                        return 1;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        source.sendMessage(Text.literal("Error clearing chat history: " + e.getMessage())
                                                .formatted(Formatting.RED));
                                        return 0;
                                    }
                                })));

                // Register the help command
                dispatcher.register(literal("help")
                        .executes(context -> {
                            context.getSource().sendMessage(Text.literal("=== Ollamachat Commands ==="));
                            context.getSource().sendMessage(Text.literal("/ollama list - List available models"));
                            context.getSource().sendMessage(Text.literal("/ollama model name <modelname> - Set the model to use"));
                            context.getSource().sendMessage(Text.literal("/ollama history <limit> - Show conversation history"));
                            context.getSource().sendMessage(Text.literal("/ollama clear - Delete your chat history"));
                            context.getSource().sendMessage(Text.literal("/" + config.aiCommandPrefix + " <message> - Chat with the AI"));
                            context.getSource().sendMessage(Text.literal("/" + config.aiCommandPrefix + " clear - Delete your chat history"));
                            return 1;
                        }));
            }
        });
    }

    private String handleAIRequest(ServerCommandSource source, String message) {
        try {
            UUID playerId = source.getPlayer().getUuid();
            
            // Get conversation history for context if memory is enabled
            String prompt;
            if (config.enableMemory) {
                List<ConversationEntry> history = database.getConversationHistory(playerId, config.memoryHistoryLimit);
                String context = history.stream()
                        .map(entry -> config.memoryFormat
                                .replace("{message}", entry.getMessage())
                                .replace("{response}", entry.getResponse()))
                        .collect(Collectors.joining("\n\n"));
                
                prompt = context.isEmpty() ? message : 
                    "Previous conversation:\n" + context + "\n\nCurrent message: " + message;
            } else {
                prompt = message;
            }

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
                    
                    // Don't encrypt the response here - we want to display clear text to the player
                    // The response will be encrypted when saved to the database
                    
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

    public static ModConfig getConfig() {
        return config;
    }

    public static MinecraftServer getServer() {
        return server;
    }
} 