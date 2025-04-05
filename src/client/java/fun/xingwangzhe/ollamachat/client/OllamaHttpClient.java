package fun.xingwangzhe.ollamachat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
    private static String OLLAMA_API_URL = "http://localhost:11434/api/generate";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final AtomicInteger activeRequests = new AtomicInteger(0);
    private static final ExecutorService requestExecutor = Executors.newCachedThreadPool();

    public static void setApiUrl(String url) {
        OLLAMA_API_URL = url;
    }

    public static void handleAIRequest(String userInput, boolean isClientMessage) {
        String currentModel = OllamaModelManager.getCurrentModel();
        if (currentModel.isEmpty()) {
            sendAsPlayerMessage(Text.translatable("command.ollama.error.no_model_selected").getString());
            return;
        }

        activeRequests.incrementAndGet();

        // 增强输入清洗
        String escapedInput = userInput
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\t", "    ")  // 替换制表符
                .replace("\n", "\\n")   // 处理换行符
                .replaceAll("[\\x00-\\x1F]", ""); // 去除控制字符

        // 根据生成接口调整请求体
        String requestBody = String.format(
                "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false, \"num_predict\": 60}",
                currentModel, escapedInput);

        OllamaDebugTracker.setLastRequest(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_API_URL))
                .timeout(Duration.ofSeconds(60))
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
                sendAsPlayerMessage("Error: " + e.getMessage() + ". Make sure Ollama is running with 'ollama serve'.");
                activeRequests.decrementAndGet();
                return null;
            });
        });
    }

    private static String parseResponse(String responseBody) {
        try {
            JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
            if (jsonObject.has("response")) {
                String responseText = jsonObject.get("response").getAsString();

                // 增强输出处理
                responseText = responseText
                        .replaceAll("<[^>]*>", "")   // 去除HTML标签
                        .replace("\n", " ")          // 替换换行符为空格
                        .replaceAll("\\s{2,}", " ")  // 合并多个空格
                        .trim();

                return responseText.length() > 500 ?
                        responseText.substring(0, 500) + "..." :
                        responseText;
            }
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
        return "Error: Invalid response format";
    }

    private static void sendAsPlayerMessage(String message) {
        MinecraftClient.getInstance().execute(() -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                String formattedMsg = "[AI] " + message;
                player.sendMessage(Text.literal(formattedMsg), false);
            }
        });
    }

    public static int getActiveRequests() {
        return activeRequests.get();
    }
}