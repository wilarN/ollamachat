package fun.xingwangzhe.ollamachat.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.*;
import net.minecraft.util.Formatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaCommandHandler {
    private static final ExecutorService COMMAND_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final String GENERIC_ERROR = "command.ollama.error.generic";
    private static final String MODEL_NOT_FOUND_ERROR = "command.ollama.error.model_not_found";
    private static final Logger LOGGER = LoggerFactory.getLogger("OllamaChat-Client");

    private static final SuggestionProvider<FabricClientCommandSource> MODEL_SUGGESTIONS = (context, builder) -> {
        try {
            Process process = new ProcessBuilder("ollama", "list").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.readLine(); // Skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        String modelName = line.split("\\s+")[0];
                        builder.suggest(modelName);
                    }
                }
            }
        } catch (Exception e) {
            builder.suggest(Text.translatable("command.ollama.error.list_failed").getString());
        }
        return CompletableFuture.completedFuture(builder.build());
    };

    public static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("ollama")
                .then(ClientCommandManager.literal("list")
                        .executes(context -> {
                            COMMAND_EXECUTOR.submit(() -> listModels(context.getSource()));
                            context.getSource().sendFeedback(Text.translatable("command.ollama.status.list_running"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("serve")
                        .executes(context -> {
                            COMMAND_EXECUTOR.submit(() -> serveOllama(context.getSource()));
                            context.getSource().sendFeedback(Text.translatable("command.ollama.status.serve_starting"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("ps")
                        .executes(context -> {
                            COMMAND_EXECUTOR.submit(() -> listRunningModels(context.getSource()));
                            context.getSource().sendFeedback(Text.translatable("command.ollama.status.ps_running"));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("refresh")
                        .executes(context -> {
                            COMMAND_EXECUTOR.submit(() -> {
                                OllamaModelManager.updateModelsFromSystem();
                                context.getSource().sendFeedback(Text.translatable("command.ollama.status.models_refreshed"));
                            });
                            return 1;
                        }))
                .then(ClientCommandManager.literal("model")
                        .then(ClientCommandManager.argument("modelname", StringArgumentType.greedyString())
                                .suggests(MODEL_SUGGESTIONS)
                                .executes(context -> {
                                    String modelName = StringArgumentType.getString(context, "modelname").trim();
                                    if (validateModel(modelName, context.getSource())) {
                                        OllamaModelManager.setCurrentModel(modelName);
                                        context.getSource().sendFeedback(Text.translatable("command.ollama.status.model_set", modelName));
                                    }
                                    return 1;
                                })))
        );
        
        dispatcher.register(ClientCommandManager.literal("ai")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Usage: /ai <message> - Chat with the AI"));
                    return 1;
                })
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            String message = StringArgumentType.getString(context, "message");
                            context.getSource().sendFeedback(Text.literal("Sending message to AI: " + message));
                            OllamaMessageHandler.setProcessingCommand(true);
                            try {
                                OllamaHttpClient.handleAIRequest(message, true);
                            } finally {
                                OllamaMessageHandler.setProcessingCommand(false);
                            }
                            return 1;
                        }))
                .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal("Clearing chat history..."));
                            OllamaClientDatabase.clearHistory(true);
                            context.getSource().sendFeedback(Text.literal("Chat history cleared!").formatted(Formatting.GREEN));
                            return 1;
                        }))
                .then(ClientCommandManager.literal("history")
                        .executes(context -> {
                            context.getSource().sendFeedback(Text.literal("Usage: /ai history <1-30>"));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("limit", IntegerArgumentType.integer(1, 30))
                                .executes(context -> {
                                    int limit = IntegerArgumentType.getInteger(context, "limit");
                                    context.getSource().sendFeedback(Text.literal("Showing last " + limit + " messages:"));
                                    // TODO: Implement history display
                                    return 1;
                                })))
        );
    }

    private static boolean validateModel(String modelName, FabricClientCommandSource source) {
        if (OllamaModelManager.isModelValid(modelName)) {
            return true;
        } else {
            source.sendFeedback(Text.translatable(MODEL_NOT_FOUND_ERROR));
            return false;
        }
    }

    private static void listModels(FabricClientCommandSource source) {
        executeCommand(source, "list", "command.ollama.status.list_success");
    }

    private static void serveOllama(FabricClientCommandSource source) {
        executeCommand(source, "serve", "command.ollama.status.service_started");
    }

    private static void listRunningModels(FabricClientCommandSource source) {
        executeCommand(source, "ps", "command.ollama.status.ps_success");
    }

    private static void executeCommand(FabricClientCommandSource source, String subCommand, String successMessage, Object... args) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ollama", subCommand);
            if (subCommand.equals("run") && args.length > 0) {
                processBuilder.command().add(args[0].toString());
            }

            Process process = processBuilder.start();

            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        source.sendFeedback(Text.of("[Ollama Error] " + errorLine));
                    }
                } catch (Exception e) {
                    source.sendFeedback(Text.translatable(GENERIC_ERROR));
                }
            });
            errorThread.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    source.sendFeedback(Text.of(line));
                }
            }

            errorThread.join();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                source.sendFeedback(Text.translatable("command.ollama.error.timeout"));
                process.destroy();
                return;
            }

            if (process.exitValue() == 0) {
                source.sendFeedback(Text.translatable(successMessage, args));
            } else {
                source.sendFeedback(Text.translatable(GENERIC_ERROR));
            }
        } catch (Exception e) {
            source.sendFeedback(Text.translatable(GENERIC_ERROR));
        }
    }
}