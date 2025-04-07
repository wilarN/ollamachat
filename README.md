# OllamaChat Mod (Fabric)

Empower Minecraft players to interact with locally deployed AI models through in-game commands! Built on [Ollama](https://ollama.com/), this mod enables dynamic AI-generated conversations.

> **Note**: This is a fork of the original OllamaChat mod with additional features. I am a contributor to the project, helping add features to the original project.

## Features

- Chat with AI using `/[aiCommandPrefix] <message>` command in public chat
- Private chat with AI using `/p[aiCommandPrefix] <message>` command (only visible to you)
- Conversation memory with configurable history limit
- Database support (SQLite and MySQL)
- Privacy controls with chat history deletion
- At-rest encryption for secure message storage
- Configurable AI model selection
- Memory optimization for better performance
- Works on both client and server installations
- Individual player memory and conversation history
- Automatic database cleanup and management
- Help command to display mod information
- Separate databases for private and public conversations
- View your conversation history with the history command

## Commands

### User Commands
- `/[aiCommandPrefix] <message>` - Chat with the AI in public chat
- `/p[aiCommandPrefix] <message>` - Chat with the AI in private chat (only visible to you)
- `/[aiCommandPrefix] clear` - Delete your chat history
- `/[aiCommandPrefix] history <1-30>` - View your conversation history
- `/[aiCommandPrefix] help` - Show help information about the mod

### Admin Commands
- `/ollama list` - List available AI models
- `/ollama model <modelname>` - Set the AI model to use
- `/ollama history <limit>` - Show conversation history
- `/ollama clear` - Delete your chat history
- `/ollama clearall` - Delete all chat history (admin only)
- `/ollama reload` - Reload the configuration
- `/ollama serve` - Start the Ollama service
- `/ollama ps` - View active model processes

## Configuration

The mod can be configured in `config/ollamachat.json`:

```json
{
  "ollamaApiUrl": "http://localhost:11434/api/generate",
  "defaultModel": "llama3",
  "requireOpForCommands": true,
  "opPermissionLevel": 4,
  "aiCommandPrefix": "ai",
  "enableChatPrefix": true,
  "chatPrefix": "[AI]",
  "prefixColor": "gold",
  "responseColor": "aqua",
  "privateChatPrefix": "pai",
  "maxResponseLength": 1000,
  "stripHtmlTags": true,
  "messageCooldown": 5,
  "cooldownMessage": "Please wait %d seconds before sending another message to the AI.",
  "enableMemory": true,
  "memoryHistoryLimit": 5,
  "memoryFormat": "User: {message}\nAssistant: {response}",
  "maxContextTokens": 4096,
  "maxMessageLength": 500,
  "messageCompression": true,
  "cleanupInterval": 3600,
  "maxConversationAge": 604800,
  "maxConversationsPerPlayer": 100,
  "databaseType": "local",
  "databaseHost": "localhost",
  "databasePort": 3306,
  "databaseName": "ollamachat",
  "databaseUsername": "root",
  "databasePassword": "",
  "localDatabasePath": "data/ollamachat/conversations.db",
  "enableEncryption": true,
  "encryptionKey": ""
}
```

### Configuration Options

#### General Settings
- `ollamaApiUrl`: The endpoint URL for the Ollama API
- `defaultModel`: The default AI model to use
- `requireOpForCommands`: Whether to require operator permissions for commands
- `opPermissionLevel`: The permission level required for commands (if enabled)
- `aiCommandPrefix`: The prefix for the AI chat command (default: "ai")
- `enableChatPrefix`: Whether to add a prefix to AI responses
- `chatPrefix`: The prefix to add to AI responses (if enabled)
- `prefixColor`: Color for the AI prefix (Minecraft color names)
- `responseColor`: Color for the AI response text (Minecraft color names)
- `privateChatPrefix`: Prefix for private chat commands (default: "pai")
- `maxResponseLength`: Maximum length of AI responses (characters)
- `stripHtmlTags`: Whether to remove HTML tags from AI responses
- `messageCooldown`: Cooldown time between messages (seconds)
- `cooldownMessage`: Message shown when cooldown is active (use %d for seconds)

#### Memory Settings
- `enableMemory`: Whether to enable conversation memory
- `memoryHistoryLimit`: Maximum number of conversation entries to remember
- `memoryFormat`: Format string for conversation memory (supports {message} and {response} placeholders)
- `maxContextTokens`: Maximum number of tokens to include in context
- `maxMessageLength`: Maximum length of user messages (characters)
- `messageCompression`: Whether to compress messages for memory optimization

#### Database Settings
- `cleanupInterval`: How often to clean up old messages (seconds)
- `maxConversationAge`: Maximum age of conversations before deletion (seconds)
- `maxConversationsPerPlayer`: Maximum number of conversations per player
- `databaseType`: Database type ("local" for SQLite, "external" for MySQL)
- `databaseHost`: MySQL database host (for external database)
- `databasePort`: MySQL database port (for external database)
- `databaseName`: MySQL database name (for external database)
- `databaseUsername`: MySQL database username (for external database)
- `databasePassword`: MySQL database password (for external database)
- `localDatabasePath`: Path to the SQLite database file (for local database)

#### Security Settings
- `enableEncryption`: Enable/disable at-rest encryption
- `encryptionKey`: Custom encryption key (leave empty for auto-generated key)

## Technical Details

### Memory System
The conversation memory system:
- Maintains context between messages
- Uses configurable format for memory entries
- Supports token limits for context
- Implements message compression
- Automatically cleans up old conversations
- Separates private and public conversations in different database tables

### Privacy Features
The mod includes several privacy features:
- Private chat mode that only shows messages to the player who sent them
- Separate database tables for private and public conversations
- At-rest encryption for all chat messages and AI responses
- Individual player conversation history
- Ability to clear personal chat history

### API Integration
The mod integrates with Ollama through:
- HTTP API calls to the local Ollama service
- JSON request/response handling
- Stream support for real-time responses
- Automatic model validation
- Error recovery and retry logic

## Security

The mod includes at-rest encryption for all chat messages and AI responses. When encryption is enabled:
- All messages are encrypted before being stored in the database
- Messages are decrypted only when displayed to the user
- A unique encryption key is automatically generated for each server
- The encryption key is saved to the configuration file and persists across server restarts
- The encryption key can be customized in the configuration file

Note: This is database encryption (at-rest encryption), not end-to-end encryption. The server can decrypt all messages. The encryption is primarily to protect against database breaches, not against server compromise.

### Encryption Implementation Details

The encryption system uses AES encryption with a random initialization vector (IV) for each message. Encrypted messages are prefixed with a special marker to identify them. If decryption fails (e.g., due to corrupted data or a key mismatch), the system will gracefully fall back to displaying the original message.

## Contributors

- **William Johnsson** (william.jsson+mcmodding[at]hotmail.com) - Added database functionality, command improvements, and additional features + bug fixes.
- **xingwangzhe** - Original creator of the OllamaChat mod.

---

## 🛠️ Prerequisites
- **Must have [Ollama](https://ollama.com/) installed locally** with at least one model deployed.
- Run `ollama serve` in your terminal to start the local service.
- Download models using `ollama pull <model-name>` (e.g., `llama3`).

### Quick Ollama Installation Guide

> **Disclaimer**: This is a basic guide for getting started. For the most up-to-date installation instructions and troubleshooting, please refer to the [official Ollama documentation](https://github.com/ollama/ollama/blob/main/README.md#quickstart).

#### Linux
```bash
# Install using curl
curl -fsSL https://ollama.com/install.sh | sh

# Or using wget
wget -O - https://ollama.com/install.sh | sh
```

#### macOS
[Download for macOS](https://ollama.com/download/mac)

#### Windows
1. [Download for Windows](https://ollama.com/download/windows)
2. Run the installer
3. Follow the installation wizard

### Getting Started with Ollama

1. **Start the Ollama service**
   ```bash
   ollama serve
   ```

2. **Download a model** (choose one):
   ```bash
   # Pull a model
   ollama pull <modelname ex. llama3.2>
   ```

3. **Test the model**
   ```bash
   # Simple chat
   ollama run mistral "Hello, how are you?"
   
   # Or start an interactive session
   ollama run mistral
   ```

4. **Verify the API is working**
   ```bash
   # Test the API endpoint
   curl http://localhost:11434/api/generate -d '{
     "model": "<modelname ex. llama3.2>",
     "prompt": "Hello, how are you?"
   }'
   ```

> **Note**: While the mod might work without additional database mods, you might encounter issues with conversation history storage. If you want to use database features (SQLite or MySQL), you may need to install:
> - [Kosmolot's SQLite mod](https://modrinth.com/mod/kosmolot-sqlite) for SQLite support
> - [Kosmolot's MySQL mod](https://modrinth.com/mod/kosmolot-mysql) for MySQL support
> 
> Without these mods, conversation history features may be limited, but basic chat functionality should work.

---

## ⚠️ Important Notes
- Ensure Ollama is properly installed and models are downloaded via CLI before use.
- Check if the Ollama service is running if you encounter timeout errors.
- Response speed depends on your local hardware performance.
- The mod requires proper permissions to execute commands (configurable in settings).
- Memory usage can be optimized through the configuration options.
- For best performance, use a model that fits your hardware capabilities.
- The first response might be slower as the model loads into memory.