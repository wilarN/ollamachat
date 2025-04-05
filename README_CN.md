[English](README.md)
# OllamaChat 模组 (Fabric)

> **免责声明**: 作者不懂中文，此中文翻译可能存在错误。欢迎任何母语为中文的人士提供修正建议。

> **注意**: 这是原始 OllamaChat 模组的分支版本，添加了更多功能。我是该项目的贡献者，帮助为原始项目添加了功能。

让 Minecraft 玩家通过游戏内命令与本地部署的 AI 模型交互！本模组基于 [Ollama](https://ollama.ai/) 实现，支持调用本地模型生成对话响应。

感谢deepseek chat 帮我解决疑难杂症，并编写大部分网络通信代码和其他代码。

---

## 🛠️ 前置要求

* **必须预先安装 [Ollama](https://ollama.ai/)** 并部署至少一个模型。
* 在命令行中运行 `ollama serve` 启动本地服务。
* 使用 `ollama pull <模型名>` 下载所需模型（如 `llama3`）。

### ⚠️ 数据库依赖（重要！）
如果您计划使用本模组的数据库功能，您**必须**安装以下模组之一：

- **SQLite 数据库支持**: [Kosmolot's SQLite 模组](https://modrinth.com/mod/kosmolot-sqlite)
- **MySQL/MariaDB 数据库支持**: [Kosmolot's MySQL 模组](https://modrinth.com/mod/kosmolot-mysql)

这些模组提供了必要的数据库连接器，同时支持 Fabric 和 Forge。

> **注意**: 如果您不安装所需的数据库模组，当模组尝试连接到数据库时，您会看到类似 "MySQL driver not found" 或 "SQLite driver not found" 的错误消息。模组将继续在没有数据库支持的情况下工作，但对话历史记录不会被保存。

---

## ✨ 核心功能

1. **公共聊天**  
   使用 `/[aiCommandPrefix] <消息>` 命令与 AI 进行公共对话。
2. **私人聊天**  
   使用 `/p[aiCommandPrefix] <消息>` 命令与 AI 进行私人对话（仅您可见）。
3. **模型切换**  
   在游戏中随时切换已下载的模型。
4. **即时对话**  
   发送消息，AI 将生成响应。
5. **服务管理**  
   直接通过游戏命令管理 Ollama 服务状态。
6. **对话记忆**  
   AI 会记住之前的对话，提供上下文相关的回答。
7. **数据库支持**  
   支持 SQLite 本地数据库或外部 MySQL 数据库存储对话。
8. **隐私控制**  
   使用简单命令清除聊天历史记录。
9. **数据库加密**  
   所有消息在存储前都会被加密，保护数据安全。
10. **客户端和服务器支持**  
    可在客户端和服务器端安装使用。
11. **个人记忆**  
    每个玩家都有独立的对话记忆和历史记录。
12. **自动数据库管理**  
    自动清理旧消息，优化数据库性能。
13. **帮助命令**  
    显示模组的帮助信息。

---

## 📜 命令列表

### 用户命令
- `/[aiCommandPrefix] <消息>` - 在公共聊天中与 AI 对话
- `/p[aiCommandPrefix] <消息>` - 与 AI 进行私人对话（仅您可见）
- `/[aiCommandPrefix] clear` - 删除您的聊天历史记录
- `/[aiCommandPrefix] help` - 显示模组的帮助信息

### 管理员命令
| 命令                          | 功能描述                     |
|-------------------------------|------------------------------|
| `/ollama list`                | 列出所有已下载的模型         |
| `/ollama model <模型名称>`    | 切换当前使用的模型           |
| `/ollama history <数量>`      | 显示对话历史记录             |
| `/ollama serve`               | 启动本地 Ollama 服务         |
| `/ollama ps`                  | 查看当前运行的模型进程       |
| `/ollama reload`              | 重新加载模组配置             |
| `/ollama clear`               | 删除您的聊天历史记录         |
| `/ollama clearall`           | 删除所有聊天历史记录（仅管理员）

---

## 🎮 使用示例

1. **设置模型**  
   `/ollama model llama3`  
   *切换到名为 "llama3" 的模型。*
   
2. **公共聊天**  
   在聊天框输入：  
   `/[aiCommandPrefix] 怎么在 Minecraft 里造房子？`  
   AI 会生成回答并以游戏消息形式返回，所有玩家可见。

3. **私人聊天**  
   在聊天框输入：  
   `/p[aiCommandPrefix] 钻石在哪里最容易找到？`  
   AI 会生成回答并以游戏消息形式返回，仅您可见。

4. **查看历史**  
   `/ollama history 5`  
   *查看您最近的 5 条对话记录。*

5. **清除历史**  
   `/[aiCommandPrefix] clear`  
   *删除所有聊天历史记录以保护隐私。*

6. **显示帮助**  
   `/[aiCommandPrefix] help`  
   *显示模组的功能和命令信息。*

---

## ⚙️ 配置选项

模组可在 `config/ollamachat.json` 中配置：

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
  "cooldownMessage": "请等待 %d 秒后再发送消息给AI。",
  "enableMemory": true,
  "memoryHistoryLimit": 5,
  "memoryFormat": "用户: {message}\n助手: {response}",
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

### 配置选项

#### 常规设置
- `ollamaApiUrl`: Ollama API 的端点 URL
- `defaultModel`: 默认使用的 AI 模型
- `requireOpForCommands`: 是否需要管理员权限才能使用命令
- `opPermissionLevel`: 使用命令所需的权限级别（如果启用）
- `aiCommandPrefix`: AI 聊天命令的前缀（默认："ai"）
- `enableChatPrefix`: 是否在 AI 回复前添加前缀
- `chatPrefix`: 添加到 AI 回复的前缀（如果启用）
- `maxResponseLength`: AI 回复的最大长度（字符数）
- `stripHtmlTags`: 是否从 AI 回复中移除 HTML 标签
- `messageCooldown`: 消息之间的冷却时间（秒）
- `cooldownMessage`: 冷却期间显示的消息（使用 %d 表示秒数）

#### 记忆设置
- `enableMemory`: 是否启用对话记忆
- `memoryHistoryLimit`: 记忆的对话条目最大数量
- `memoryFormat`: 对话记忆的格式字符串
- `maxContextTokens`: 上下文中包含的最大令牌数
- `maxMessageLength`: 用户消息的最大长度（字符数）
- `messageCompression`: 是否压缩消息以优化内存

#### 数据库设置
- `cleanupInterval`: 清理旧消息的频率（秒）
- `maxConversationAge`: 对话删除前的最大年龄（秒）
- `maxConversationsPerPlayer`: 每个玩家的最大对话数量
- `databaseType`: 数据库类型（"local" 表示 SQLite，"external" 表示 MySQL）
- `databaseHost`: MySQL 数据库主机（用于外部数据库）
- `databasePort`: MySQL 数据库端口（用于外部数据库）
- `databaseName`: MySQL 数据库名称（用于外部数据库）
- `databaseUsername`: MySQL 数据库用户名（用于外部数据库）
- `databasePassword`: MySQL 数据库密码（用于外部数据库）
- `localDatabasePath`: SQLite 数据库文件路径（用于本地数据库）

#### 安全设置
- `enableEncryption`: 启用/禁用数据库加密
- `encryptionKey`: 自定义加密密钥（留空则自动生成）

## 安全

模组包含对所有聊天消息和 AI 回复的数据库加密。启用加密时：
- 所有消息在存储到数据库前都会被加密
- 消息仅在显示给用户时才会解密
- 每个服务器都会自动生成唯一的加密密钥
- 加密密钥会保存到配置文件中，并在服务器重启后保持不变
- 加密密钥可以在配置文件中自定义

注意：这是数据库加密（静态加密），而不是端到端加密。服务器可以解密所有消息。加密主要是为了防止数据库泄露，而不是防止服务器被入侵。

### 加密实现细节

加密系统使用 AES 加密，每条消息都使用随机初始化向量 (IV)。加密消息会添加特殊标记以便识别。如果解密失败（例如由于数据损坏或密钥不匹配），系统会优雅地回退到显示原始消息。

---

## ⚠️ 注意事项

* 首次使用前，请确保已通过命令行正确安装 Ollama 并下载模型。
* 如果遇到超时错误，请检查本地 Ollama 服务是否正常运行。
* 模型响应速度取决于本地硬件性能。
* 加密密钥会自动生成并保存在配置文件中，无需手动设置。

## 贡献者

- **William Johnsson** (william.jsson+mcmodding[at]hotmail.com) - 添加了数据库功能、命令改进和错误修复
- **xingwangzhe** - OllamaChat 模组的原始创建者