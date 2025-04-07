package fun.xingwangzhe.ollamachat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.ConversationEntry;
import com.mojang.authlib.GameProfile;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

public class OllamaHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");
    private static final AtomicInteger activeRequests = new AtomicInteger(0);
    private static ModConfig config;

    public static void initialize(ModConfig modConfig) {
        config = modConfig;
    }

    public static void handleAIRequest(String message, boolean isPrivate) {
        if (activeRequests.get() > 0) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(Text.literal("Please wait for the previous request to complete."), false);
            }
            return;
        }

        activeRequests.incrementAndGet();
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // Create HTTP client
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

                // Get conversation history for context
                StringBuilder contextBuilder = new StringBuilder();
                if (config.enableMemory) {
                    GameProfile profile = MinecraftClient.getInstance().getGameProfile();
                    if (profile != null && profile.getId() != null) {
                        List<ConversationEntry> history;
                        if (isPrivate) {
                            history = OllamaClientDatabase.getPrivateConversationHistory(profile.getId(), config.memoryHistoryLimit);
                        } else {
                            history = OllamaClientDatabase.getPublicConversationHistory(profile.getId(), config.memoryHistoryLimit);
                        }
                        
                        if (!history.isEmpty()) {
                            contextBuilder.append("Previous conversation:\n");
                            for (ConversationEntry entry : history) {
                                contextBuilder.append(String.format(config.memoryFormat, 
                                    entry.getMessage(), 
                                    entry.getResponse())).append("\n\n");
                            }
                        }
                    }
                }

                // Create request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", OllamaModelManager.getCurrentModel());
                requestBody.addProperty("prompt", contextBuilder.toString() + message);

                // Create HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.ollamaApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

                // Send request and get response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // Handle response
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    LOGGER.debug("Raw response: " + responseBody);
                    
                    // Parse the response using a more lenient approach
                    String aiResponse = parseOllamaResponse(responseBody);
                    
                    // Format response to look like a player message
                    String formattedResponse = "<AI> " + aiResponse;
                    
                    // Send to chat
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(Text.literal(formattedResponse), false);
                        
                        // Save to database if memory is enabled
                        if (config.enableMemory) {
                            OllamaClientDatabase.saveMessage(message, aiResponse, isPrivate);
                        }
                    }
                } else {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(Text.literal("Error: " + response.body()), false);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error handling AI request: " + e.getMessage());
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player != null) {
                    player.sendMessage(Text.literal("Error: " + e.getMessage()), false);
                }
            } finally {
                activeRequests.decrementAndGet();
            }
        });
    }
    
    private static String parseOllamaResponse(String responseBody) {
        try {
            // Try to parse as a single JSON object first
            try {
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                if (jsonResponse.has("response")) {
                    return jsonResponse.get("response").getAsString();
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to parse as single JSON object: " + e.getMessage());
            }
            
            // If that fails, try to parse as a stream of JSON objects
            StringBuilder result = new StringBuilder();
            String[] lines = responseBody.split("\n");
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                
                try {
                    JsonObject jsonLine = JsonParser.parseString(line).getAsJsonObject();
                    if (jsonLine.has("response")) {
                        result.append(jsonLine.get("response").getAsString());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Failed to parse line: " + line + " - " + e.getMessage());
                }
            }
            
            if (result.length() > 0) {
                return result.toString();
            }
            
            // If all parsing fails, return the raw response
            return responseBody;
        } catch (Exception e) {
            LOGGER.error("Error parsing Ollama response: " + e.getMessage());
            return "Error parsing response: " + e.getMessage();
        }
    }
}