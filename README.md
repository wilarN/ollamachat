[中文](README_CN.md)
# OllamaChat Mod (Fabric)

Empower Minecraft players to interact with locally deployed AI models through in-game commands! Built on [Ollama](https://ollama.ai/), this mod enables dynamic AI-generated conversations.


## Features

- Chat with AI using `/ai <message>` command
- Conversation memory with configurable history limit
- Database support (SQLite and MySQL)
- Privacy controls with chat history deletion
- End-to-end encryption for secure communication
- Configurable AI model selection
- Memory optimization for better performance

## Commands

- `/ollama model <modelname>` - Set the model to use
- `/ollama history <limit>` - Show conversation history
- `/ai <message>` - Chat with the AI
- `/ai clear` - Delete your chat history

## Configuration

The mod can be configured in `config/ollamachat.json`:

```json
{
  "ollamaEndpoint": "http://localhost:11434/api/generate",
  "defaultModel": "llama3",
  "memoryLimit": 10,
  "memoryFormat": "Human: %s\nAssistant: %s",
  "enableEncryption": true,
  "encryptionKey": ""
}
```

### Configuration Options

- `ollamaEndpoint`: The endpoint URL for the Ollama API
- `defaultModel`: The default AI model to use
- `memoryLimit`: Maximum number of conversation entries to remember
- `memoryFormat`: Format string for conversation memory
- `enableEncryption`: Enable/disable end-to-end encryption
- `encryptionKey`: Custom encryption key (leave empty for auto-generated key)

## Security

The mod includes database encryption for all chat messages and AI responses. When encryption is enabled:
- All messages are encrypted before being stored in the database
- Messages are decrypted only when displayed to the user
- A unique encryption key is generated for each server
- The encryption key can be customized in the configuration file

Note: This is database encryption (at-rest encryption), not end-to-end encryption. The server can decrypt all messages. The encryption is primarily to protect against database breaches, not against server compromise.

### Encryption Implementation Details

The encryption system uses AES encryption with a random initialization vector (IV) for each message. Encrypted messages are prefixed with a special marker to identify them. If decryption fails (e.g., due to corrupted data or a key mismatch), the system will gracefully fall back to displaying the original message.

## Contributors

- **William Johnsson** (william.jsson+mcmodding[at]hotmail.com) - Added database functionality, command improvements, and bug fixes

---

## 🛠️ Prerequisites
- **Must have [Ollama](https://ollama.ai/) installed locally** with at least one model deployed.
- Run `ollama serve` in your terminal to start the local service.
- Download models using `ollama pull <model-name>` (e.g., `llama3`).

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

2. **Send Request**
   Type in chat:
   `ai How to build a house in Minecraft?`
   The AI will respond with an answer in-game.

3. **View History**
   `/ollama history 5`
   *View your 5 most recent conversations.*

4. **Clear History**
   `/ai clear`
   *Delete all your conversation history for privacy.*

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