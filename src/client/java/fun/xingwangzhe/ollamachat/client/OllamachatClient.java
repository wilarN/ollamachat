package fun.xingwangzhe.ollamachat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import fun.xingwangzhe.ollamachat.client.OllamaCommandHandler;
import fun.xingwangzhe.ollamachat.client.OllamaMessageHandler;
import fun.xingwangzhe.ollamachat.client.OllamaClientDatabase;
import fun.xingwangzhe.ollamachat.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OllamachatClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing OllamaChat Client Mod");
        
        // Load configuration
        ModConfig config = ModConfig.load();
        
        // Set client-specific config
        config.requireOpForCommands = false;
        config.ollamaApiUrl = "http://localhost:11434/api/generate";
        
        // Initialize database
        OllamaClientDatabase.initialize(config);
        
        // Schedule model update
        Executors.newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(
                OllamaModelManager::updateModelsFromSystem,
                0,
                5,
                TimeUnit.MINUTES
            );
        
        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            OllamaCommandHandler.registerCommands(dispatcher);
        });
        
        // Initialize message handler
        OllamaMessageHandler.initialize();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down OllamaChat Client Mod");
            OllamaClientDatabase.close();
        }));
    }
}