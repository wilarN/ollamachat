[中文](README_CN.md)
# OllamaChat Mod (Fabric)

Empower Minecraft players to interact with locally deployed AI models through in-game commands! Built on [Ollama](https://ollama.ai/), this mod enables dynamic AI-generated conversations.

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

## Commands

### User Commands
- `/[aiCommandPrefix] <message>` - Chat with the AI in public chat
- `/p[aiCommandPrefix] <message>` - Chat with the AI in private chat (only visible to you)
- `/[aiCommandPrefix] clear` - Delete your chat history
- `/[aiCommandPrefix] help` - Show help information about the mod

### Admin Commands
- `/ollama list` - List available AI models
- `/ollama model name <modelname>` - Set the AI model to use
- `/ollama history <limit>` - Show conversation history
- `/ollama clear` - Delete your chat history
- `/ollama clearall` - Delete all chat history (admin only)
- `/ollama reload` - Reload the configuration

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
- `maxResponseLength`: Maximum length of AI responses (characters)
- `stripHtmlTags`: Whether to remove HTML tags from AI responses
- `messageCooldown`: Cooldown time between messages (seconds)
- `cooldownMessage`: Message shown when cooldown is active (use %d for seconds)

#### Memory Settings
- `enableMemory`: Whether to enable conversation memory
- `memoryHistoryLimit`: Maximum number of conversation entries to remember
- `memoryFormat`: Format string for conversation memory
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

- **William Johnsson** (william.jsson+mcmodding[at]hotmail.com) - Added database functionality, command improvements, and bug fixes
- **xingwangzhe** - Original creator of the OllamaChat mod

---

## 🛠️ Prerequisites
- **Must have [Ollama](https://ollama.ai/) installed locally** with at least one model deployed.
- Run `ollama serve` in your terminal to start the local service.
- Download models using `ollama pull <model-name>` (e.g., `llama3`).

### ⚠️ Database Dependencies (Important!)
If you plan to use the database features of this mod, you **must** install one of the following mods:

- **For SQLite database support**: [Kosmolot's SQLite mod](https://modrinth.com/mod/kosmolot-sqlite)
- **For MySQL/MariaDB database support**: [Kosmolot's MySQL mod](https://modrinth.com/mod/kosmolot-mysql)

These mods provide the necessary database connectors that work with both Fabric and Forge.

> **Note**: If you don't install the required database mod, you'll see an error message like "MySQL driver not found" or "SQLite driver not found" when the mod tries to connect to the database. The mod will continue to work without database support, but conversation history will not be saved.

---

## ✨ Key Features
1. **Model Switching**
   Seamlessly switch between downloaded models in-game.
2. **Real-time Chat**
   Send messages starting with `ai ` to receive AI-generated responses.
3. **Service Control**
   Manage Ollama service status directly via commands.
4. **Conversation Memory**
   AI remembers previous conversations for context-aware responses.
5. **Database Support**
   Store conversations in SQLite or external MySQL database.
6. **Privacy Controls**
   Clear your chat history with a simple command.

---

## 📜 Command List
| Command                       | Description                  |
|-------------------------------|------------------------------|
| `/ollama list`                | List all downloaded models   |
| `/ollama model <modelname>`  | Switch to a specific model   |
| `/ollama history <limit>`     | Show conversation history    |
| `/ollama serve`               | Start the Ollama service     |
| `/ollama ps`                  | View active model processes  |
| `/ai <message>`               | Chat with the AI             |
| `/ai clear`                   | Delete your chat history     |

---

## 🎮 Usage Examples
1. **Set Model**
   `/ollama model llama3`
   *Switch to the "llama3" model.*

2. **Public Chat**
   Type in chat:
   `/[aiCommandPrefix] How to build a house in Minecraft?`
   The AI will respond with an answer in-game, visible to all players.

3. **Private Chat**
   Type in chat:
   `/p[aiCommandPrefix] What's the best way to find diamonds?`
   The AI will respond with an answer in-game, visible only to you.

4. **View History**
   `/ollama history 5`
   *View your 5 most recent conversations.*

5. **Clear History**
   `/[aiCommandPrefix] clear`
   *Delete all your conversation history for privacy.*

6. **Show Help**
   `/[aiCommandPrefix] help`
   *Display information about the mod's features and commands.*

---

## ⚙️ Configuration
The mod supports two database types:
- **Local SQLite**: Default option, stores data in a local file
- **External MySQL**: For server environments with multiple instances

Database settings can be configured in the mod's config file:
- `databaseType`: "local" or "external"
- `localDatabasePath`: Path for SQLite database
- `maxConversationsPerPlayer`: Limit conversations per player
- `maxConversationAge`: Auto-delete conversations older than this (in seconds)
- `cleanupInterval`: How often to run cleanup (in seconds)

---

## ⚠️ Important Notes
- Ensure Ollama is properly installed and models are downloaded via CLI before use.
- Check if the Ollama service is running if you encounter timeout errors.
- Response speed depends on your local hardware performance.