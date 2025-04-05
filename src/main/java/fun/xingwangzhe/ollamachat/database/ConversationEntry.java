package fun.xingwangzhe.ollamachat.database;

import java.time.Instant;

public class ConversationEntry {
    private final String message;
    private final String response;
    private final long timestamp;

    public ConversationEntry(String message, String response) {
        this.message = message;
        this.response = response;
        this.timestamp = Instant.now().toEpochMilli();
    }

    public String getMessage() {
        return message;
    }

    public String getResponse() {
        return response;
    }

    public long getTimestamp() {
        return timestamp;
    }
} 