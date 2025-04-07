package fun.xingwangzhe.ollamachat.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");
    private static boolean processingCommand = false;

    public static void initialize() {
        // Register message handlers
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (processingCommand) return;
            if (message.getString().startsWith("ai ")) {
                processingCommand = true;
                String prompt = message.getString().substring(3);
                OllamaHttpClient.handleAIRequest(prompt, true);
                processingCommand = false;
            }
        });

        ClientSendMessageEvents.CHAT.register((message) -> {
            if (processingCommand) return;
            if (message.startsWith("ai ")) {
                processingCommand = true;
                String prompt = message.substring(3);
                OllamaHttpClient.handleAIRequest(prompt, true);
                processingCommand = false;
            }
        });
    }

    public static void setProcessingCommand(boolean processing) {
        processingCommand = processing;
    }

    public static void handleAIResponse(String response) {
        LOGGER.info("Handling AI response: " + response);
        // Save response to database
        OllamaClientDatabase.saveMessage("", response, true);
    }
}