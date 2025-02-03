package fun.xingwangzhe.ollamachat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OllamaHttpClient {
    private static final String OLLAMA_API_URL = "http://localhost:11434/api/chat";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final AtomicInteger activeRequests = new AtomicInteger(0);
    private static final ExecutorService requestExecutor = Executors.newCachedThreadPool();

    public static void handleAIRequest(String userInput, boolean isClientMessage) {
        String currentModel = OllamaModelManager.getCurrentModel();
        if (currentModel.isEmpty()) {
            sendAsPlayerMessage(Text.translatable("command.ollama.error.no_model_selected").getString());
            return;
        }

        activeRequests.incrementAndGet();
        String escapedInput = userInput
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        String requestBody = String.format(
                "{\"model\": \"%s\", \"messages\": [{\"role\": \"user\", \"content\": \"%s\"}], \"stream\": false, \"num_predict\": 60}",
                currentModel, escapedInput);

        OllamaDebugTracker.setLastRequest(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_API_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        requestExecutor.submit(() -> {
            CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            responseFuture.thenCompose(response -> {
                if (response.statusCode() == 200) {
                    return CompletableFuture.completedFuture(parseResponse(response.body()));
                } else {
                    return CompletableFuture.completedFuture(Text.translatable("command.ollama.error.http_code", response.statusCode()).getString());
                }
            }).thenAccept(aiResponse -> {
                OllamaDebugTracker.setLastResponse(aiResponse);
                sendAsPlayerMessage(aiResponse);
                activeRequests.decrementAndGet();
            }).exceptionally(e -> {
                sendAsPlayerMessage(Text.translatable("command.ollama.error.timeout").getString());
                activeRequests.decrementAndGet();
                return null;
            });
        });
    }

    private static String parseResponse(String responseBody) {
        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            if (jsonObject.has("message") && jsonObject.get("message").isJsonObject()) {
                JsonObject messageObj = jsonObject.getAsJsonObject("message");
                if (messageObj.has("content")) {
                    String responseText = messageObj.get("content").getAsString();
                    responseText = responseText.replaceAll("<[^>]*>", "");
                    return responseText.length() > 500 ? responseText.substring(0, 500) + "..." : responseText;
                }
            }
        } catch (Exception e) {
            return Text.translatable("command.ollama.error.parse_failed").getString();
        }
        return Text.translatable("command.ollama.error.generic").getString();
    }

    private static void sendAsPlayerMessage(String message) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.of(message), false);
            }
        });
    }

    public static int getActiveRequests() {
        return activeRequests.get();
    }
}