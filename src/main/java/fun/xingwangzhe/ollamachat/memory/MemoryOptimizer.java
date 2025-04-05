package fun.xingwangzhe.ollamachat.memory;

import fun.xingwangzhe.ollamachat.config.ModConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class MemoryOptimizer {
    private final ModConfig config;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");

    public MemoryOptimizer(ModConfig config) {
        this.config = config;
    }

    public String optimizeMessage(String message) {
        if (!config.messageCompression || message.length() <= config.maxMessageLength) {
            return message;
        }

        // Split into sentences
        String[] sentences = SENTENCE_PATTERN.split(message);
        List<String> importantSentences = new ArrayList<>();
        int currentLength = 0;

        // Keep sentences until we reach the limit
        for (String sentence : sentences) {
            if (currentLength + sentence.length() > config.maxMessageLength) {
                break;
            }
            importantSentences.add(sentence);
            currentLength += sentence.length();
        }

        // Join the sentences back together
        return String.join(". ", importantSentences) + ".";
    }

    public String truncateContext(String context) {
        if (context.length() <= config.maxContextTokens) {
            return context;
        }

        // Split into words
        String[] words = WHITESPACE_PATTERN.split(context);
        List<String> importantWords = new ArrayList<>();
        int currentTokens = 0;

        // Keep words until we reach the token limit
        for (String word : words) {
            // Rough estimate: each word is about 1.3 tokens on average
            int wordTokens = (int) Math.ceil(word.length() / 4.0);
            if (currentTokens + wordTokens > config.maxContextTokens) {
                break;
            }
            importantWords.add(word);
            currentTokens += wordTokens;
        }

        return String.join(" ", importantWords);
    }

    public boolean shouldCleanup(long lastAccessTime) {
        long currentTime = System.currentTimeMillis() / 1000;
        return (currentTime - lastAccessTime) > config.maxConversationAge;
    }

    public String compressMessage(String message) {
        if (!config.messageCompression) {
            return message;
        }

        // Remove extra whitespace
        message = WHITESPACE_PATTERN.matcher(message).replaceAll(" ").trim();

        // Remove redundant information
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+am\\s+", "$1'm ");
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+are\\s+", "$1're ");
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+have\\s+", "$1've ");
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+will\\s+", "$1'll ");
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+would\\s+", "$1'd ");
        message = message.replaceAll("\\b(I|you|he|she|it|we|they)\\s+had\\s+", "$1'd ");

        return message;
    }
} 