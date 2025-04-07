package fun.xingwangzhe.ollamachat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class OllamaHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private static final AtomicInteger activeRequests = new AtomicInteger(0);

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

                // Create request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "llama2");
                requestBody.addProperty("prompt", "Message: " + message + "\n\nPlease provide a direct response without using a conversation format or role-playing.");

                // Create HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

                // Send request and get response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // Handle response
                if (response.statusCode() == 200) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                    String aiResponse = jsonResponse.get("response").getAsString();
                    
                    // Format response - use a more direct format
                    String formattedResponse = "[AI] " + aiResponse;
                    
                    // Send to chat
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(Text.literal(formattedResponse), false);
                        
                        // Save to database
                        OllamaMessageHandler.handleAIResponse(aiResponse);
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
}