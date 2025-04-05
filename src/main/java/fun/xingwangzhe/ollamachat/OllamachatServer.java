package fun.xingwangzhe.ollamachat;

import fun.xingwangzhe.ollamachat.chat.MessageCooldownManager;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import fun.xingwangzhe.ollamachat.database.Database;
import fun.xingwangzhe.ollamachat.database.LocalDatabase;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.*;

public class OllamachatServer implements DedicatedServerModInitializer {
    private static ModConfig config;
    private static Database database;
    private static MessageCooldownManager cooldownManager;

    @Override
    public void onInitializeServer() {
        // Load configuration
        config = ModConfig.load();
        
        // Initialize database
        try {
            database = new LocalDatabase(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Initialize cooldown manager
        cooldownManager = new MessageCooldownManager(config);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("ai")
                .then(literal("chat")
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        UUID playerId = source.getPlayer().getUuid();
                        
                        // Check cooldown
                        if (!cooldownManager.canSendMessage(playerId)) {
                            source.sendMessage(Text.literal(cooldownManager.getCooldownMessage(playerId))
                                .formatted(Formatting.RED));
                            return 1;
                        }
                        
                        // Get the message from the command
                        String message = context.getInput().substring("ai chat ".length()).trim();
                        
                        // Send message to AI
                        CompletableFuture.runAsync(() -> {
                            try {
                                // Process message and get response
                                String response = "AI Response"; // Replace with actual AI call
                                
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
                    })
                )
            );
        });
    }
} 