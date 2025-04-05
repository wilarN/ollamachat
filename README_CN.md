[English](README.md)
# OllamaChat 模组 (Fabric)

让 Minecraft 玩家通过游戏内命令与本地部署的 AI 模型交互！本模组基于 [Ollama](https://ollama.ai/) 实现，支持调用本地模型生成对话响应。

## 👨‍💻 贡献者
- **William Johnsson** (william.jsson+mcmodding[at]hotmail.com) - 添加数据库功能、改进命令系统和修复错误

---

## 🛠️ 前置要求
- **必须预先安装 [Ollama](https://ollama.com/)** 并部署至少一个模型。
- 在命令行中运行 `ollama serve` 启动本地服务。
- 使用 `ollama pull <模型名>` 下载所需模型（如 `llama3`）。

---

## ✨ 核心功能
1. **模型切换**  
   在游戏中随时切换已下载的模型。
2. **即时对话**  
   发送以 `ai ` 开头的消息，AI 将生成响应。
3. **服务管理**  
   直接通过游戏命令管理 Ollama 服务状态。
4. **对话记忆**  
   AI 会记住之前的对话，提供上下文相关的回答。
5. **数据库支持**  
   支持 SQLite 本地数据库或外部 MySQL 数据库存储对话。
6. **隐私控制**  
   使用简单命令清除聊天历史记录。

---

## 📜 命令列表
| 命令                          | 功能描述                     |
|-------------------------------|------------------------------|
| `/ollama list`                | 列出所有已下载的模型         |
| `/ollama model <模型名称>`    | 切换当前使用的模型           |
| `/ollama history <数量>`      | 显示对话历史记录             |
| `/ollama serve`               | 启动本地 Ollama 服务         |
| `/ollama ps`                  | 查看当前运行的模型进程       |
| `/ai <消息>`                  | 与 AI 对话                   |
| `/ai clear`                   | 删除您的聊天历史记录         |

---

## 🎮 使用示例
1. **设置模型**  
   `/ollama model llama3`  
   *切换到名为 "llama3" 的模型。*
   
2. **发送请求**  
   在聊天框输入：  
   `ai 怎么在 Minecraft 里造房子？`  
   AI 会生成回答并以游戏消息形式返回。

3. **查看历史**  
   `/ollama history 5`  
   *查看您最近的 5 条对话记录。*

4. **清除历史**  
   `/ai clear`  
   *删除所有聊天历史记录以保护隐私。*

---

## ⚙️ 配置选项
模组支持两种数据库类型：
- **本地 SQLite**：默认选项，数据存储在本地文件中
- **外部 MySQL**：适用于多实例服务器环境

数据库设置可在模组的配置文件中配置：
- `databaseType`："local" 或 "external"
- `localDatabasePath`：SQLite 数据库路径
- `maxConversationsPerPlayer`：每个玩家的对话数量限制
- `maxConversationAge`：自动删除超过此时间的对话（秒）
- `cleanupInterval`：清理频率（秒）

---

## ⚠️ 注意事项
- 首次使用前，请确保已通过命令行正确安装 Ollama 并下载模型。
- 如果遇到超时错误，请检查本地 Ollama 服务是否正常运行。
- 模型响应速度取决于本地硬件性能。

## 功能特性

- 使用 `/ai <消息>` 命令与AI聊天
- 可配置历史记录限制的对话记忆
- 数据库支持（SQLite和MySQL）
- 聊天历史删除的隐私控制
- 端到端加密的安全通信
- 可配置的AI模型选择
- 内存优化以提升性能

## 命令

- `/ollama model <模型名>` - 设置要使用的模型
- `/ollama history <限制>` - 显示对话历史
- `/ai <消息>` - 与AI聊天
- `/ai clear` - 删除你的聊天历史

## 配置

模组可以在 `config/ollamachat.json` 中配置：

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

### 配置选项

- `ollamaEndpoint`: Ollama API的端点URL
- `defaultModel`: 默认使用的AI模型
- `memoryLimit`: 记忆的最大对话条目数
- `memoryFormat`: 对话记忆的格式字符串
- `enableEncryption`: 启用/禁用端到端加密
- `encryptionKey`: 自定义加密密钥（留空则自动生成）

## 安全

模组包含所有聊天消息和AI响应的数据库加密。启用加密时：
- 所有消息在存储到数据库前都会被加密
- 消息仅在显示给用户时解密
- 每个服务器都会生成唯一的加密密钥
- 加密密钥可以在配置文件中自定义

注意：这是数据库加密（静态加密），而不是端到端加密。服务器可以解密所有消息。加密主要是为了防止数据库泄露，而不是防止服务器被攻破。

### 加密实现细节

加密系统使用AES加密，每条消息都使用随机初始化向量(IV)。加密消息会添加特殊前缀以便识别。如果解密失败（例如由于数据损坏或密钥不匹配），系统会优雅地回退到显示原始消息。