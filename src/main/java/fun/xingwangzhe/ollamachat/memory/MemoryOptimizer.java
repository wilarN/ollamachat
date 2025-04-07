package fun.xingwangzhe.ollamachat.memory;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import java.util.regex.Pattern;

public class MemoryOptimizer {
    private final ModConfig config;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    public MemoryOptimizer(ModConfig config) {
        this.config = config;
    }

    /**
     * Optimizes a message by normalizing whitespace and truncating if needed
     * 
     * @param message The message to optimize
     * @return The optimized message
     */
    public String optimizeMessage(String message) {
        if (!config.messageCompression || message == null) {
            return message;
        }

        // Normalize whitespace
        message = WHITESPACE_PATTERN.matcher(message).replaceAll(" ").trim();
        
        // Truncate if needed
        if (message.length() > config.maxMessageLength) {
            return message.substring(0, config.maxMessageLength);
        }
        
        return message;
    }

    /**
     * Truncates context to fit within token limits
     * 
     * @param context The context to truncate
     * @return The truncated context
     */
    public String truncateContext(String context) {
        if (context == null || context.length() <= config.maxContextTokens) {
            return context;
        }
        return context.substring(0, config.maxContextTokens);
    }

    /**
     * Checks if a conversation should be cleaned up based on age
     * 
     * @param lastAccessTime The last access time in seconds
     * @return true if the conversation should be cleaned up
     */
    public boolean shouldCleanup(long lastAccessTime) {
        return (System.currentTimeMillis() / 1000 - lastAccessTime) > config.maxConversationAge;
    }
    
    /**
     * @deprecated Use optimizeMessage instead
     */
    @Deprecated
    public String compressMessage(String message) {
        return optimizeMessage(message);
    }
} 