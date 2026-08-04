# Dramatica Flow - Code Wiki

> 基于Dramatica叙事理论的AI小说创作Android应用，支持7步创作流程和MiMo/DeepSeek双AI引擎。

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [项目结构](#3-项目结构)
4. [架构设计](#4-架构设计)
5. [数据层 (Data Layer)](#5-数据层-data-layer)
6. [UI层 (UI Layer)](#6-ui层-ui-layer)
7. [ViewModel层](#7-viewmodel层)
8. [7步创作流程](#8-7步创作流程)
9. [AI集成](#9-ai集成)
10. [数据库Schema](#10-数据库schema)
11. [依赖关系](#11-依赖关系)
12. [构建与运行](#12-构建与运行)
13. [关键设计决策](#13-关键设计决策)

---

## 1. 项目概述

| 属性 | 值 |
|------|-----|
| **项目名称** | Dramatica Flow |
| **应用ID** | `com.dramatica.flow` |
| **版本** | 1.0.0 (versionCode 1) |
| **语言** | Kotlin |
| **UI框架** | Jetpack Compose + Material3 |
| **最低SDK** | API 26 (Android 8.0) |
| **目标SDK** | API 35 |
| **编译SDK** | 35 |
| **JVM目标** | 17 |
| **架构模式** | MVVM (无DI框架，ViewModel直接实例化Repository) |

**核心功能**：用户输入小说基础信息后，AI自动完成世界观构建、角色设计、大纲规划，并支持逐章AI写作、润色、续写、修订，同时提供伏笔追踪、因果链、角色关系网络、情感弧线、时间线等一致性工具。

---

## 2. 技术栈

### 构建工具
| 工具 | 版本 |
|------|------|
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.1.0 |
| KSP (Kotlin Symbol Processing) | 2.1.0-1.0.29 |

### 核心依赖
| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.compose:compose-bom` | 2025.05.00 | Compose UI组件统一版本管理 |
| `androidx.core:core-ktx` | 1.16.0 | Android核心扩展 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.9.1 | 生命周期管理 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.1 | ViewModel Compose集成 |
| `androidx.activity:activity-compose` | 1.10.1 | Activity Compose支持 |
| `androidx.compose.material3:material3` | (BOM) | Material3设计 |
| `androidx.compose.material:material-icons-extended` | (BOM) | 扩展图标库 |
| `androidx.compose.animation:animation` | (BOM) | 动画支持 |
| `androidx.room:room-runtime` | 2.6.1 | 本地数据库 |
| `androidx.room:room-ktx` | 2.6.1 | Room协程扩展 |
| `androidx.room:room-compiler` | 2.6.1 | Room注解处理器(KSP) |
| `kotlinx-serialization-json` | 1.7.3 | JSON序列化 |
| `androidx.datastore:datastore-preferences` | 1.1.4 | 键值对存储（未使用，API配置用SharedPreferences） |

### 插件
- `com.android.application` - Android应用插件
- `org.jetbrains.kotlin.android` - Kotlin Android插件
- `org.jetbrains.kotlin.plugin.compose` - Kotlin Compose编译器插件
- `org.jetbrains.kotlin.plugin.serialization` - Kotlin序列化插件
- `com.google.devtools.ksp` - KSP注解处理器

---

## 3. 项目结构

```
dramatica-local/
├── build.gradle.kts                          # 根构建脚本（插件声明）
├── settings.gradle.kts                       # 项目设置
├── gradle.properties                         # Gradle属性
├── CHANGELOG.md                              # 变更日志
├── MEMORY.md                                 # 项目开发记录
├── CODE_WIKI.md                              # 本文档
├── app/
│   ├── build.gradle.kts                      # 应用模块构建脚本
│   ├── proguard-rules.pro                    # 混淆规则
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml           # 应用清单
│           ├── res/
│           │   ├── values/
│           │   │   └── strings.xml           # 字符串资源
│           │   └── mipmap-*/                 # 启动图标
│           └── java/com/dramatica/flow/
│               ├── MainActivity.kt           # 应用入口
│               ├── data/                     # 数据层
│               │   ├── AppDatabase.kt        # Room数据库 + Entity + DAO + 枚举/数据类
│               │   ├── LocalRepository.kt    # 本地数据仓库
│               │   ├── AiRepository.kt       # AI API调用仓库
│               │   └── PostWriteValidator.kt # 写后验证器
│               └── ui/                       # UI层
│                   ├── DramaticaFlowApp.kt   # 主框架Composable
│                   ├── theme/
│                   │   ├── Color.kt          # 配色方案
│                   │   └── Theme.kt          # 主题定义
│                   ├── components/
│                   │   ├── AppSidebar.kt     # 侧边栏导航
│                   │   └── Components.kt     # 通用可复用组件
│                   ├── screens/
│                   │   ├── HomeScreen.kt          # 书架首页
│                   │   ├── FlowScreen.kt          # 创作流程总览
│                   │   ├── FlowStepDetailScreen.kt # 7步详情页
│                   │   ├── CharactersScreen.kt    # 角色档案
│                   │   ├── WritingScreen.kt       # 写作编辑器
│                   │   ├── TrackingScreen.kt      # 一致性追踪
│                   │   ├── TimelineScreen.kt      # 时间线
│                   │   └── SettingsScreen.kt      # API设置
│                   └── viewmodel/
│                       └── MainViewModel.kt  # 核心ViewModel
```

---

## 4. 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │HomeScreen│ │FlowScreen│ │WritingScr│ │CharactersScreen...│  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────────┬──────────┘  │
│       │             │            │                 │             │
│       └─────────────┴────────────┴─────────────────┘             │
│                           │                                      │
│                    DramaticaFlowApp                              │
│                    (侧边栏 + 页面路由 + Toast)                      │
├───────────────────────────┼──────────────────────────────────────┤
│                    ViewModel Layer                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                     MainViewModel                          │  │
│  │  - StateFlow: storyConfig, currentStep, isGenerating...   │  │
│  │  - 创作流程: generateWorldBuilding/Characters/Outline()   │  │
│  │  - 写作操作: aiWriteContent/polish/continue/revise()      │  │
│  │  - 数据操作: saveChapter/addCharacter/addHook...          │  │
│  └──────────────┬────────────────────┬───────────────────────┘  │
│                 │                    │                           │
├─────────────────┼────────────────────┼───────────────────────────┤
│            Data Layer              Network                      │
│  ┌──────────────────────┐  ┌──────────────┐                    │
│  │   LocalRepository     │  │ AiRepository │                    │
│  │  ┌────────────────┐   │  │              │                    │
│  │  │  Room Database  │   │  │ HttpURLConn  │                    │
│  │  │  (9 tables)     │   │  │ → MiMo API   │                    │
│  │  └────────────────┘   │  │ → DeepSeekAPI│                    │
│  │  ┌────────────────┐   │  └──────────────┘                    │
│  │  │ SharedPrefs    │   │                                      │
│  │  │ (API配置)       │   │  ┌──────────────────┐               │
│  │  └────────────────┘   │  │PostWriteValidator │               │
│  └──────────────────────┘  └──────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
```

### 数据流

```
用户输入基础信息 → updateStoryConfig() → StoryConfig
       ↓
AI自动生成世界观 → generateWorldBuilding() → AiRepository → coreSetting
       ↓ (自动跳转)
AI自动生成角色   → generateCharacters()  → AiRepository → characters + CharacterEntity表
       ↓ (自动跳转)
AI自动生成大纲   → generateOutline()     → AiRepository → outline
       ↓ (自动跳转)
AI写章节         → aiWriteContent()      → AiRepository → 内容 + HookEntity + CausalLinkEntity
       ↓
MiMo后处理       → postProcessForMimo()
       ↓
自动保存         → saveProject() → DramaticaProjectEntity
```

### 状态管理

- 所有状态使用 `MutableStateFlow` / `StateFlow`，UI层通过 `collectAsState()` 订阅
- `_isGenerating: MutableStateFlow<Boolean>` - 防重复生成锁，所有AI函数首先检查此标志
- `_uiState: MutableStateFlow<DramaticaUiState>` - UI状态机，控制加载/错误/结果显示
- `_currentStep: MutableStateFlow<DramaticaStep>` - 当前创作步骤
- `_storyConfig: MutableStateFlow<StoryConfig>` - 中心数据结构，所有步骤读写

---

## 5. 数据层 (Data Layer)

### 5.1 AppDatabase.kt

**路径**: `data/AppDatabase.kt`

包含以下核心数据类型：

#### 枚举

##### `DramaticaStep` — 创作步骤枚举
| 枚举值 | number | label | 说明 |
|--------|--------|-------|------|
| `BASIC_INFO` | 1 | 基础信息 | 填写书名、类型、核心创意 |
| `WORLD_BUILDING` | 2 | 世界观构建 | AI生成世界观设定 |
| `CHARACTER_DESIGN` | 3 | 角色设计 | AI生成角色档案 |
| `OUTLINE` | 4 | 大纲规划 | AI生成分卷大纲 |
| `WRITING` | 5 | 章节创作 | AI辅助写作 |
| `AI_RESULT` | 6 | AI创作结果 | 查看AI创作内容 |
| `TIMELINE` | 7 | 时间线 | 完稿审校与一致性检查 |

- 提供 `next` / `prev` 属性实现步骤链表导航

#### 数据类

##### `StoryConfig` — 故事配置核心数据结构
```kotlin
data class StoryConfig(
    val title: String,          // 书名
    val genre: String,          // 类型（玄幻/科幻/都市等）
    val briefIdea: String,      // 核心创意简述
    val targetChapters: Int,    // 目标章节数
    val coreSetting: String,    // 世界观设定
    val characters: String,     // 角色设定文本
    val outline: String         // 大纲文本
)
```

##### `DramaticaUiState` — UI状态密封类
```kotlin
sealed class DramaticaUiState {
    object Idle                                          // 空闲
    data class AutoGenerating(step, message, progress)   // 自动生成中
    data class WorldGenerated(worldSetting)              // 世界观已生成
    data class CharactersGenerated(characters)           // 角色已生成
    data class OutlineGenerated(outline)                 // 大纲已生成
    data class WritingChapter(step, message, progress)   // 章节写作中
    data class Error(message)                            // 错误
}
```

#### 5.1.1 Room实体 (Entity)

##### `BookEntity` — 书籍
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String (PK) | UUID前8位 |
| `title` | String | 书名 |
| `genre` | String | 类型，默认"玄幻" |
| `targetChapters` | Int | 目标章节数，默认90 |
| `targetWords` | Int | 目标字数/章，默认4000 |
| `currentChapter` | Int | 当前章节 |
| `createdAt` | Long | 创建时间戳 |

##### `ChapterEntity` — 章节
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍ID |
| `chapterNumber` | Int | 章节编号 |
| `title` | String | 章节标题 |
| `content` | String | 章节内容 |
| `wordCount` | Int | 字数 |
| `kind` | String | 类型(draft/final) |

##### `CharacterEntity` — 角色
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍 |
| `name` | String | 角色名 |
| `role` | String | 角色类型(主角/对立者/影响者/守护者/伙伴) |
| `avatar` | String | 头像字符(首字) |
| `type` | String | 英文类型(protagonist/antagonist/impact/guardian/sidekick) |
| `description` | String | 角色描述 |
| `tags` | String | 标签(逗号分隔) |

##### `HookEntity` — 伏笔
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String (PK) | UUID前8位 |
| `bookId` | String | 所属书籍 |
| `type` | String | 类型(foreshadow/promise/mystery/conflict) |
| `description` | String | 描述 |
| `plantedChapter` | Int | 埋设章节 |
| `resolvedChapter` | Int? | 回收章节 |
| `status` | String | 状态(open/resolved/warning) |

##### `CausalLinkEntity` — 因果链
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍 |
| `chapter` | Int | 章节 |
| `cause` | String | 原因 |
| `event` | String | 事件 |
| `consequence` | String | 后果 |
| `decision` | String | 决策 |

##### `RelationshipEntity` — 角色关系
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍 |
| `characterA` | String | 角色A |
| `characterB` | String | 角色B |
| `type` | String | 关系类型 |
| `strength` | Int | 关系强度(-100~100) |
| `reason` | String | 原因 |

##### `EmotionEntity` — 情感状态
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍 |
| `characterId` | String | 角色ID |
| `emotion` | String | 情感 |
| `intensity` | Int | 强度(1-10) |
| `chapter` | Int | 章节 |
| `trigger` | String | 触发因素 |

##### `TimelineEntity` — 时间线事件
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | Long (PK, auto) | 自增ID |
| `bookId` | String | 所属书籍 |
| `chapter` | Int | 章节 |
| `action` | String | 事件描述 |
| `type` | String | 类型(conflict/reveal/emotion/foreshadow/other) |
| `characterId` | String | 角色ID |
| `location` | String | 地点 |

##### `DramaticaProjectEntity` — 创作项目
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long (PK, auto) | 自增ID |
| `title` | String | 项目标题 |
| `genre` | String | 类型 |
| `briefIdea` | String | 核心创意 |
| `targetChapters` | Int | 目标章节数 |
| `coreSetting` | String | 世界观 |
| `characters` | String | 角色设定 |
| `outline` | String | 大纲 |
| `causalChainHistory` | String | 因果链历史 |
| `summaryHistory` | String | 摘要历史 |
| `pendingHooks` | String | 待回收伏笔 |
| `emotionalArcs` | String | 情感弧线 |
| `currentStep` | Int | 当前步骤编号 |
| `createdAt` | Long | 创建时间 |
| `updatedAt` | Long | 更新时间 |

#### 5.1.2 DAO接口

| DAO | 主要方法 | 说明 |
|-----|---------|------|
| `BookDao` | `getAllBooks()`, `getBook()`, `insertBook()`, `deleteBook()`, `updateCurrentChapter()` | 书籍CRUD |
| `ChapterDao` | `getChapters()`, `getChapter()`, `insertChapter()`, `deleteChapter()` | 章节CRUD |
| `CharacterDao` | `getCharacters()`, `insertCharacter()`, `deleteCharacter()` | 角色CRUD |
| `HookDao` | `getHooks()`, `getHooksByStatus()`, `insertHook()`, `resolveHook()` | 伏笔管理 |
| `CausalDao` | `getCausalChain()`, `insertLink()` | 因果链 |
| `RelationshipDao` | `getRelationships()`, `insertRelationship()`, `updateStrength()` | 关系管理 |
| `EmotionDao` | `getEmotions()`, `getCharacterEmotions()`, `insertEmotion()` | 情感管理 |
| `TimelineDao` | `getTimeline()`, `insertEvent()` | 时间线 |
| `DramaticaProjectDao` | `getAllProjects()`, `getProjectById()`, `insertProject()`, `updateProject()`, `deleteProject()` | 项目CRUD |

#### 5.1.3 AppDatabase

```kotlin
@Database(entities = [BookEntity, ChapterEntity, CharacterEntity, HookEntity,
    CausalLinkEntity, RelationshipEntity, EmotionEntity, TimelineEntity,
    DramaticaProjectEntity], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    // 9个抽象DAO方法
    companion object {
        // 单例模式：双重检查锁定
        fun getInstance(context: Context): AppDatabase
    }
}
```

数据库名：`dramatica_flow.db`，版本1。

### 5.2 LocalRepository.kt

**路径**: `data/LocalRepository.kt`

本地数据仓库，封装所有Room DAO调用，是ViewModel与数据库之间的中间层。

**关键方法分类**：

| 类别 | 方法 | 返回类型 |
|------|------|---------|
| 书籍 | `getAllBooks()`, `getBook()`, `insertBook()`, `deleteBook()`, `updateCurrentChapter()` | Flow / suspend |
| 章节 | `getChapters()`, `getChapter()`, `saveChapter()` | Flow / suspend |
| 角色 | `getCharacters()`, `insertCharacter()`, `deleteCharacter()` | Flow / suspend |
| 伏笔 | `getHooks()`, `insertHook()`, `resolveHook()` | Flow / suspend |
| 因果链 | `getCausalChain()`, `insertCausalLink()` | Flow / suspend |
| 关系 | `getRelationships()`, `insertRelationship()`, `updateRelationshipStrength()` | Flow / suspend |
| 情感 | `getEmotions()`, `insertEmotion()` | Flow / suspend |
| 时间轴 | `getTimeline()`, `insertTimelineEvent()` | Flow / suspend |
| 项目 | `getAllProjects()`, `getProjectById()`, `insertProject()`, `updateProject()`, `deleteProject()` | Flow / suspend |

### 5.3 AiRepository.kt

**路径**: `data/AiRepository.kt`

AI API调用仓库，负责与MiMo和DeepSeek大模型通信。

**关键类**：

#### `ApiSettings` — API配置数据类
```kotlin
data class ApiSettings(
    val mimoUrl: String,      // MiMo API地址
    val mimoKey: String,      // MiMo API密钥
    val mimoModel: String,    // MiMo模型名
    val dsUrl: String,        // DeepSeek API地址
    val dsKey: String,        // DeepSeek API密钥
    val dsModel: String,      // DeepSeek模型名
    val temperature: Float,   // 温度参数
    val maxTokens: Int,       // 最大token数
    val systemPrompt: String  // 系统提示词
)
```

#### `AiResult` — AI结果密封类
```kotlin
sealed class AiResult {
    data class Success(val content: String) : AiResult()
    data class Error(val message: String) : AiResult()
}
```

#### 核心方法

##### `generateContent(prompt, useMimo, maxTokens): AiResult`
- **参数**: `prompt: String` - 提示词, `useMimo: Boolean` - 是否使用MiMo, `maxTokens: Int` - 最大token数
- **返回**: `AiResult`
- **实现**:
  1. 从SharedPreferences读取API配置
  2. 构建 OpenAI 兼容的 `/chat/completions` POST请求
  3. 使用 `HttpURLConnection` 发送请求
  4. 解析JSON响应中的 `choices[0].message.content`
  5. 连接超时30秒，读取超时60秒
  6. finally块中确保 `conn.disconnect()` 防止连接泄漏

#### `extractContent(response): String`
- 从OpenAI格式的JSON响应中提取内容文本
- 使用 `org.json.JSONObject` 解析（避免正则截断问题）

### 5.4 PostWriteValidator.kt

**路径**: `data/PostWriteValidator.kt`

写后验证器，用于检测AI生成文本的质量问题。

#### 通用验证项 (8项)
| 规则 | 严重级别 | 说明 |
|------|---------|------|
| `AI_MARKER_DENSITY` | warning | AI标记词密度（仿佛/忽然/竟然/不禁/宛如/猛地/顿时/霎时/不由得），每3000字>1次 |
| `FORBIDDEN_PHRASE` | error | 禁止句式（不是……而是……/全场震惊/众人哗然/所有人都/不言而喻） |
| `META_NARRATIVE` | warning | 元叙事（核心动机/信息落差/叙事节奏/情节推进/人物弧线）和作者说教 |
| `REPORT_STYLE` | warning | 报告式语言（分析了……情况/从……角度/综合考虑） |
| `COLLECTIVE_REACTION` | warning | 集体反应套话（在场之人皆/众人齐声/一时间哗然） |
| `CONSECUTIVE_LE` | warning | 连续"了"字句（≥6句） |
| `LONG_PARAGRAPH` | warning | 超长段落（≥2段>300字） |
| `WORD_COUNT_DEVIATION` | warning | 字数偏差（>20%） |

#### MiMo专用验证项 (3项)
| 规则 | 严重级别 | 说明 |
|------|---------|------|
| `HIGH_ADJECTIVE_DENSITY` | warning | 形容词密度过高（>2个句子含>2个的/得/地） |
| `OVER_DESCRIPTIVE` | warning | 描述过多（>100字段落无对话） |
| `CONSECUTIVE_LONG_SENTENCES` | warning | 连续长句（≥2个>50字句子） |

#### 关键方法
- `validate(content, targetWords): ValidationResult` — 通用验证
- `validateForMimo(content): ValidationResult` — MiMo专用验证

---

## 6. UI层 (UI Layer)

### 6.1 MainActivity.kt

**路径**: `MainActivity.kt`

```kotlin
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { InkWritingTheme { DramaticaFlowApp(viewModel) } }
    }
}
```

- 使用 `by viewModels()` 获取ViewModel（无DI框架）
- `enableEdgeToEdge()` 启用边到边显示
- 主题：`InkWritingTheme`，主框架：`DramaticaFlowApp`

### 6.2 DramaticaFlowApp.kt — 主框架

**路径**: `ui/DramaticaFlowApp.kt`

应用主Composable组件，负责：

- **侧边栏导航**：`AppSidebar` 组件，可折叠宽度（56dp/220dp），带弹性动画
- **页面路由**：通过 `NavPage` 枚举切换7个页面
- **Toast通知**：从ViewModel的 `SharedFlow` 收集Toast消息，2秒自动消失
- **状态订阅**：从ViewModel收集所有StateFlow数据并传递给子页面

**页面路由映射**：
| NavPage | 页面组件 | 说明 |
|---------|---------|------|
| `Home` | `HomeScreen` | 书架首页 |
| `Flow` | `FlowScreen` | 创作流程 |
| `Characters` | `CharactersScreen` | 角色档案 |
| `Writing` | `WritingScreen` | 写作编辑器 |
| `Tracking` | `TrackingScreen` | 一致性追踪 |
| `Timeline` | `TimelineScreen` | 时间线 |
| `Settings` | `SettingsScreen` | API设置 |

### 6.3 主题 (Theme)

#### Color.kt — 配色方案
设计风格：暖色纸质背景，模拟墨水书写体验。

| 色组 | 颜色 | 色值 | 用途 |
|------|------|------|------|
| 背景 | `BgPrimary` | `#FAF8F5` | 主背景 |
| 背景 | `BgWarm` | `#F5F0EA` | 暖色背景 |
| 背景 | `BgCard` | `#FFFFFF` | 卡片背景 |
| 背景 | `BgSidebar` | `#F0EBE3` | 侧边栏背景 |
| 文字 | `TextPrimary` | `#2C2418` | 主文字 |
| 文字 | `TextSecondary` | `#6B5D4F` | 次文字 |
| 文字 | `TextTertiary` | `#9C8E7E` | 辅助文字 |
| 强调 | `Accent` | `#C47D3B` | 主强调色（琥珀/铜色） |
| 强调 | `AccentLight` | `#E8C9A0` | 浅强调 |
| 强调 | `AccentBg` | `#FDF6EE` | 强调背景 |
| 语义 | `Success` | `#5A8C5A` | 成功/完成 |
| 语义 | `Danger` | `#B85450` | 危险/警告 |
| 语义 | `Info` | `#5A7A8C` | 信息 |
| 角色 | `ProtagonistColor` | 同Accent | 主角 |
| 角色 | `AntagonistColor` | 同Danger | 对立者 |
| 角色 | `SidekickColor` | `#8B7EC8` | 伙伴 |
| AI | `MiMoColor` | `#6C5CE7` | MiMo标识 |
| AI | `DeepSeekColor` | `#00B894` | DeepSeek标识 |

#### Theme.kt — 主题定义
- `InkWritingTheme`：基于 `MaterialTheme` 的Light主题
- 字体：`SerifFamily`（衬线，用于标题）+ `SansFamily`（无衬线，用于正文）
- 自定义 `Typography` 覆盖9个文本样式层级

### 6.4 通用组件 (Components)

#### AppSidebar.kt — 侧边栏导航

**枚举 `NavPage`**：7个导航页面，分为"创作空间"和"工具面板"两个分组。

**`AppSidebar` Composable**：
- 可折叠设计：展开220dp，收起56dp
- 弹性动画：`CubicBezierEasing(0.32f, 0.72f, 0f, 1f)`，320ms
- 标题：展开时显示"墨迹 · 创作"，收起时显示"墨"
- 导航项：带选中指示条、图标、标签、Badge

#### Components.kt — 通用组件库

| 组件 | 说明 |
|------|------|
| `InkRadius` | 圆角常量对象 (sm=8dp, md=12dp, lg=16dp, xl=20dp) |
| `InkShadow` | 阴影工具对象 (sm/md/lg三级) |
| `InkCard` | 可点击卡片，带阴影和边框 |
| `InkButton` | 主按钮/轮廓按钮，支持图标 |
| `CharacterAvatar` | 圆形角色头像 |
| `InkTag` | 标签组件 |
| `StatChip` | 统计芯片（含进度条） |
| `EmptyState` | 空状态占位组件 |
| `TopbarButton` | 顶部栏图标按钮 |
| `InkProgressBar` | 进度条 |
| `InkDivider` / `InkDividerLight` | 分割线 |
| `SectionHeader` | 区域标题（带操作链接） |
| `InkToast` | Toast通知 |
| `BookCoverBlock` | 书籍封面色块 |
| `BookBanner` | 书籍信息渐变横幅 |

### 6.5 页面详情 (Screens)

#### HomeScreen.kt — 书架首页
- 显示问候语和日期
- 作品统计：作品数、总章节
- 书籍列表（带封面、进度条）
- 创建新作品对话框（书名+题材选择）

#### FlowScreen.kt — 创作流程总览
- 书籍信息横幅
- 横向进度指示器（7个步骤圆点+连接线）
- 7个步骤卡片（状态：待开始/进行中/已完成）
- 已完成步骤判断：基于 `storyConfig` 各字段是否为空
- 伏笔列表和因果链列表（最近8条）
- 点击步骤卡片进入 `FlowStepDetailScreen`

#### FlowStepDetailScreen.kt — 7步详情页（核心页面）
包含7个步骤子页面，每个步骤有独立的Composable函数：

| 步骤 | 函数 | 关键功能 |
|------|------|---------|
| Step1 | `Step1BasicInfo` | 书名/类型/创意/目标章节数输入 |
| Step2 | `Step2WorldBuilding` | 自动生成世界观，可编辑、重新生成 |
| Step3 | `Step3CharacterDesign` | 自动生成角色，显示角色列表 |
| Step4 | `Step4Outline` | 自动生成大纲，5卷章节分配 |
| Step5 | `Step5Writing` | AI写作/润色/续写/修订，字数目标 |
| Step6 | `Step6AIResult` | 查看AI创作结果，可重写/润色 |
| Step7 | `Step7Timeline` | 完稿审校：一致性检查、因果链、伏笔状态 |

辅助组件：
- `GlassCard` — 半透明卡片容器
- `GlassInput` — 统一样式的输入框
- `StepTopBar` — 步骤顶部栏（返回按钮+标题）
- `TrackingCard` — 追踪卡片组件

#### WritingScreen.kt — 写作编辑器
- 章节导航（左右箭头切换章节）
- `BasicTextField` 编辑器，衬线字体，30sp行高
- 底部栏：字数统计+进度条+保存按钮

#### CharactersScreen.kt — 角色档案
- 角色列表（头像+名称+角色类型+描述+标签）
- 添加角色对话框（名字/角色类型/简介）
- 空状态引导

#### TrackingScreen.kt — 一致性追踪
- 因果链列表（因-事-果三栏）
- 伏笔追踪（状态颜色标识）
- 关系网络（强度进度条，正负色彩区分）
- 情感弧线（柱状图，按角色分组）

#### TimelineScreen.kt — 时间线
- 按章节分组的事件卡片
- 事件类型颜色标识（冲突/揭示/情感/伏笔）
- 横向滚动卡片布局

#### SettingsScreen.kt — API设置
- MiMo配置：API URL、Key（密码输入）、模型名、测试连接
- DeepSeek配置：同上
- 通用设置：Temperature、Max Tokens、System Prompt
- 保存到SharedPreferences
- 测试连接：独立HTTP请求，10秒连接超时，15秒读取超时

---

## 7. ViewModel层

### MainViewModel.kt

**路径**: `ui/viewmodel/MainViewModel.kt`

继承 `AndroidViewModel`，是应用的核心状态管理和业务逻辑中心。

#### 状态属性 (StateFlow)

| 属性 | 类型 | 说明 |
|------|------|------|
| `currentProject` | `DramaticaProjectEntity?` | 当前创作项目 |
| `currentStep` | `DramaticaStep` | 当前步骤 |
| `uiState` | `DramaticaUiState` | UI状态机 |
| `storyConfig` | `StoryConfig` | 故事配置（中心数据） |
| `outline` | `String` | 大纲文本 |
| `causalChain` | `String` | 因果链文本 |
| `emotionalArcs` | `String` | 情感弧线文本 |
| `pendingHooks` | `String` | 待回收伏笔文本 |
| `currentContent` | `String` | 当前章节内容 |
| `isGenerating` | `Boolean` | 防重复生成锁 |
| `stepProgress` | `Float` | 步骤进度(0~1) |
| `stepLoadingMessage` | `String` | 步骤加载消息 |
| `currentBookId` | `String` | 当前书籍ID |
| `books` | `List<BookEntity>` | 书籍列表 |
| `currentBook` | `BookEntity?` | 当前书籍 |
| `chapters` | `List<ChapterEntity>` | 章节列表 |
| `editingChapter` | `ChapterEntity?` | 正在编辑的章节 |
| `characters` | `List<CharacterEntity>` | 角色列表 |
| `hooks` | `List<HookEntity>` | 伏笔列表 |
| `causalChainList` | `List<CausalLinkEntity>` | 因果链列表 |
| `relationships` | `List<RelationshipEntity>` | 关系列表 |
| `emotions` | `List<EmotionEntity>` | 情感列表 |
| `timeline` | `List<TimelineEntity>` | 时间线列表 |
| `toast` | `SharedFlow<String>` | Toast消息流 |

#### 关键方法

##### 书籍操作
| 方法 | 说明 |
|------|------|
| `selectBook(bookId)` | 选择书籍，加载或创建项目 |
| `createBook(title, genre)` | 创建新书籍 |
| `deleteBook(book)` | 删除书籍 |

##### 创作流程
| 方法 | 说明 |
|------|------|
| `goToStep(step)` | 跳转到指定步骤（带前置条件校验） |
| `nextStep()` / `prevStep()` | 上/下一步 |
| `updateStoryConfig(config)` | 更新故事配置 |
| `updateCoreSetting(setting)` | 更新世界观 |
| `updateCharacters(characters)` | 更新角色 |
| `updateOutline(outline)` | 更新大纲 |

##### AI生成
| 方法 | 说明 |
|------|------|
| `generateWorldBuilding()` | AI生成世界观 |
| `generateCharacters()` | AI生成角色 |
| `generateOutline()` | AI生成大纲 |
| `aiWriteContent(config, chapterNum, chapterTitle)` | AI写章节 |
| `polishContent(config)` | AI润色 |
| `continueWriting(config)` | AI续写 |
| `reviseContent(config)` | AI修订（基于验证结果） |
| `postProcessForMimo(content)` | MiMo后处理 |

##### 数据操作
| 方法 | 说明 |
|------|------|
| `saveChapter(bookId, chapterNum, content)` | 保存章节 |
| `addCharacter(...)` | 添加角色 |
| `addHook(...)` | 添加伏笔 |
| `resolveHook(hookId, chapter)` | 回收伏笔 |
| `addCausalLink(...)` | 添加因果链 |
| `addRelationship(...)` | 添加关系 |
| `addEmotion(...)` | 添加情感 |
| `addTimelineEvent(...)` | 添加时间线事件 |

##### 内部方法
| 方法 | 说明 |
|------|------|
| `loadProject(project)` | 从DramaticaProjectEntity恢复状态 |
| `saveProject()` | 保存当前状态到DramaticaProjectEntity |

#### 防重复生成机制
所有AI生成函数遵循统一模式：
```kotlin
fun generateXxx() {
    if (_isGenerating.value) { toast("AI正在生成中..."); return }
    _isGenerating.value = true
    _uiState.value = DramaticaUiState.AutoGenerating(...)
    viewModelScope.launch {
        try {
            // AI调用
            // 更新状态
            // 保存到数据库
            // 延迟1.5秒后自动跳转
        } catch (e: Exception) {
            // 错误处理
        } finally {
            _uiState.value = DramaticaUiState.Idle
            _isGenerating.value = false
        }
    }
}
```

#### MiMo后处理 (`postProcessForMimo`)
针对MiMo模型输出特点的文本优化：
1. 删除重复的"的/得/地"
2. 删除废话开头（其实/说实话/坦白说/毫无疑问）
3. 心理陈述改为动作（他心想→他想到，她觉得→她认为）
4. 拆分长句（，然后→。然后，接着→。接着）

---

## 8. 7步创作流程

```
BASIC_INFO(1) → WORLD_BUILDING(2) → CHARACTER_DESIGN(3) → OUTLINE(4)
     → WRITING(5) → AI_RESULT(6) → TIMELINE(7)
```

### 步骤详解

| 步骤 | 名称 | 用户操作 | AI行为 | 自动跳转 |
|------|------|---------|--------|---------|
| 1 | 基础信息 | 填写书名/类型/创意/章节数 | 无 | 点击"开始生成"→步骤2 |
| 2 | 世界观构建 | 查看/编辑生成结果 | 自动生成世界观(4096 tokens) | 1.5秒后→步骤3 |
| 3 | 角色设计 | 查看角色列表 | 自动生成3个核心角色(4096 tokens) | 1.5秒后→步骤4 |
| 4 | 大纲规划 | 查看/编辑大纲 | 自动生成5卷大纲(4096 tokens) | 1.5秒后→步骤5 |
| 5 | 章节创作 | 输入章节号/标题/写作 | AI写一段/润色/续写/修订(8192 tokens) | 手动 |
| 6 | AI创作结果 | 查看创作内容 | 同上 | 手动 |
| 7 | 完稿审校 | 查看一致性检查 | 无 | 终点 |

### 前置条件校验 (`goToStep`)
- 步骤2：需要 `title` 非空
- 步骤3：需要 `coreSetting` 非空
- 步骤4：需要 `coreSetting` 和 `characters` 非空
- 步骤5：需要 `coreSetting` 和 `outline` 非空

---

## 9. AI集成

### 支持的模型

| 模型 | 标识 | 默认模型名 | 特点 |
|------|------|-----------|------|
| MiMo v2.5 | 小米大模型 | `mimo-v2.5` | 创意写作优化，需后处理 |
| DeepSeek v4 Flash | DeepSeek | `deepseek-v4-flash` | 快速推理，高性价比 |

### API调用流程

```
1. 构建Prompt（含小说类型、世界观、角色等上下文）
2. AiRepository.generateContent(prompt, useMimo=true, maxTokens)
3. POST {baseUrl}/chat/completions
   Headers: Authorization: Bearer {apiKey}
   Body: { model, messages: [{system, user}], max_tokens, temperature }
4. 解析响应: choices[0].message.content
5. 返回 AiResult.Success / AiResult.Error
```

### 章节写作Prompt结构

```
请为小说「{title}」创作第{chapterNum}章「{chapterTitle}」的内容。

类型：{genre}
世界观：{coreSetting.take(500)}
角色设定：{characters.take(500)}
已有因果链：{causalChain.take(300)}
已有伏笔：{pendingHooks.take(300)}

【写作要求】
1. 字数约2000字，不少于1500字
2. 保持与前文连贯
3. 对话自然，短句为主（3-15字）
4. 禁止：仿佛、忽然、竟然、不禁、宛如、猛地、顿时、全场震惊、众人哗然
5. 用具体动作代替抽象描述
6. 直接输出正文，不要解释
```

### AI写作操作矩阵

| 操作 | 方法 | Prompt | maxTokens |
|------|------|--------|-----------|
| 写新章节 | `aiWriteContent` | 完整章节创作 | 8192 |
| 润色 | `polishContent` | 删除AI标记词+减少形容词+拆分长句 | 8192 |
| 续写 | `continueWriting` | 基于前文末尾1000字继续 | 4096 |
| 修订 | `reviseContent` | 基于PostWriteValidator发现的问题 | 8192 |

### 章节生成时的自动操作
每次AI完成章节创作后，自动执行：
1. MiMo后处理 (`postProcessForMimo`)
2. 保存章节到数据库
3. 自动插入一条伏笔记录 (`HookEntity`)
4. 自动插入一条因果链记录 (`CausalLinkEntity`)
5. 保存项目状态 (`saveProject`)

---

## 10. 数据库Schema

```
┌──────────────────────────────────────────────────────────────┐
│                     dramatica_flow.db                        │
├──────────────────────────────────────────────────────────────┤
│  books               章节列表                                 │
│  ├─ id (PK)          书籍ID                                   │
│  ├─ title            书名                                     │
│  ├─ genre            类型                                     │
│  ├─ targetChapters   目标章节数                                │
│  ├─ targetWords      目标字数/章                              │
│  ├─ currentChapter   当前章节                                  │
│  └─ createdAt        创建时间                                  │
├──────────────────────────────────────────────────────────────┤
│  chapters            章节内容                                 │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ chapterNumber    章节编号                                  │
│  ├─ title            章节标题                                  │
│  ├─ content          章节内容                                  │
│  ├─ wordCount        字数                                     │
│  └─ kind             类型(draft/final)                        │
├──────────────────────────────────────────────────────────────┤
│  characters          角色档案                                 │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ name             角色名                                    │
│  ├─ role             角色类型(中文)                            │
│  ├─ avatar           头像字符                                  │
│  ├─ type             角色类型(英文)                            │
│  ├─ description      描述                                     │
│  └─ tags             标签                                     │
├──────────────────────────────────────────────────────────────┤
│  hooks               伏笔追踪                                 │
│  ├─ id (PK)          伏笔ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ type             类型                                     │
│  ├─ description      描述                                     │
│  ├─ plantedChapter   埋设章节                                  │
│  ├─ resolvedChapter  回收章节                                  │
│  └─ status           状态(open/resolved/warning)              │
├──────────────────────────────────────────────────────────────┤
│  causal_links        因果链                                   │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ chapter          章节                                     │
│  ├─ cause            原因                                     │
│  ├─ event            事件                                     │
│  ├─ consequence      后果                                     │
│  └─ decision         决策                                     │
├──────────────────────────────────────────────────────────────┤
│  relationships       角色关系                                 │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ characterA       角色A                                    │
│  ├─ characterB       角色B                                    │
│  ├─ type             关系类型                                  │
│  ├─ strength         强度(-100~100)                           │
│  └─ reason           原因                                     │
├──────────────────────────────────────────────────────────────┤
│  emotions            情感状态                                 │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ characterId      角色ID                                   │
│  ├─ emotion          情感                                     │
│  ├─ intensity        强度(1-10)                               │
│  ├─ chapter          章节                                     │
│  └─ trigger          触发因素                                  │
├──────────────────────────────────────────────────────────────┤
│  timeline_events     时间线                                   │
│  ├─ uid (PK)         自增ID                                   │
│  ├─ bookId (FK)      所属书籍                                  │
│  ├─ chapter          章节                                     │
│  ├─ action           事件描述                                  │
│  ├─ type             类型                                     │
│  ├─ characterId      角色ID                                   │
│  └─ location         地点                                     │
├──────────────────────────────────────────────────────────────┤
│  dramatica_projects  创作项目状态                              │
│  ├─ id (PK)          自增ID                                   │
│  ├─ title            标题                                     │
│  ├─ genre            类型                                     │
│  ├─ briefIdea        核心创意                                  │
│  ├─ targetChapters   目标章节数                                │
│  ├─ coreSetting      世界观                                    │
│  ├─ characters       角色设定                                  │
│  ├─ outline          大纲                                     │
│  ├─ causalChainHistory 因果链历史                              │
│  ├─ summaryHistory   摘要历史                                  │
│  ├─ pendingHooks     待回收伏笔                                │
│  ├─ emotionalArcs    情感弧线                                  │
│  ├─ currentStep      当前步骤编号                              │
│  ├─ createdAt        创建时间                                  │
│  └─ updatedAt        更新时间                                  │
└──────────────────────────────────────────────────────────────┘
```

---

## 11. 依赖关系

### 模块依赖图

```
MainActivity
  └── DramaticaFlowApp
        ├── AppSidebar (NavPage枚举)
        ├── HomeScreen
        ├── FlowScreen
        │     └── FlowStepDetailScreen (7个子步骤)
        ├── CharactersScreen
        ├── WritingScreen
        ├── TrackingScreen
        ├── TimelineScreen
        └── SettingsScreen
              └── SharedPreferences (api_settings)

MainViewModel
  ├── LocalRepository
  │     └── AppDatabase (Room)
  │           ├── BookDao
  │           ├── ChapterDao
  │           ├── CharacterDao
  │           ├── HookDao
  │           ├── CausalDao
  │           ├── RelationshipDao
  │           ├── EmotionDao
  │           ├── TimelineDao
  │           └── DramaticaProjectDao
  ├── AiRepository
  │     └── SharedPreferences (api_settings)
  └── PostWriteValidator (static)
```

### 数据依赖关系

```
StoryConfig (中心数据结构)
  ├── 被 MainViewModel 持有和修改
  ├── 被所有 FlowStepDetailScreen 子步骤读取
  ├── 持久化到 DramaticaProjectEntity
  └── 驱动 AI Prompt 构建

DramaticaProjectEntity (持久化状态)
  ├── 与 BookEntity 通过 title 关联
  ├── selectBook 时加载 → loadProject 恢复所有状态
  └── 每次 AI 生成后 saveProject 保存

SharedPreferences (api_settings)
  ├── AiRepository 读取：mimo_url, mimo_key, mimo_model, ds_url, ds_key, ds_model
  ├── SettingsScreen 读写：temperature, max_tokens, system_prompt
  └── 键名：mimo_url, mimo_key, mimo_model, ds_url, ds_key, ds_model, temperature, max_tokens, system_prompt
```

---

## 12. 构建与运行

### 环境要求

- Android Studio (推荐最新版)
- JDK 17
- Android SDK 35
- Gradle 8.x

### 构建命令

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建（启用混淆）
./gradlew assembleRelease

# 清理构建
./gradlew clean
```

### APK输出路径

```
app/build/outputs/apk/debug/app-debug.apk
```

### 运行配置

| 配置项 | 值 |
|--------|-----|
| 应用ID | `com.dramatica.flow` |
| 最低API | 26 (Android 8.0) |
| 目标API | 35 |
| 编译API | 35 |
| 权限 | `INTERNET` |
| 混淆 | Release模式启用 |

### 权限说明

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

仅需网络权限，用于调用AI API。

### 首次运行配置

1. 安装APK
2. 打开应用 → 侧边栏 → 设置
3. 配置MiMo和/或DeepSeek的API URL和Key
4. 点击"测试"验证连接
5. 点击"保存设置"
6. 返回书架 → 创建新作品 → 开始创作

---

## 13. 关键设计决策

### 架构决策

| 决策 | 原因 |
|------|------|
| 不使用Hilt/DI框架 | 减少复杂度，ViewModel直接实例化Repository |
| 单一ViewModel | 应用规模适中，所有状态集中管理，避免跨ViewModel通信 |
| AndroidViewModel | 需要Application Context用于Room和SharedPreferences |
| Room + Flow | 数据库变更自动推送到UI，响应式数据流 |
| SharedPreferences存储API配置 | 简单配置，无需DataStore的异步特性 |

### 数据结构决策

| 决策 | 原因 |
|------|------|
| StoryConfig作为中心数据结构 | 所有步骤共享同一份配置，避免数据分散 |
| DramaticaProjectEntity持久化所有状态 | 应用重启后恢复创作进度 |
| 角色设计同时保存文本和结构化数据 | 文本用于AI prompt上下文，结构化数据用于UI展示 |
| 章节内容用String存储 | 文本为主，无需复杂数据模型 |

### 流程决策

| 决策 | 原因 |
|------|------|
| 自动跳转（1.5秒延迟） | 减少用户操作步骤，提升创作流畅度 |
| _isGenerating防重复锁 | 避免用户快速点击导致多次API调用 |
| AI生成后自动同步写入数据库 | 保证各界面数据一致性 |
| 章节创作时自动插入伏笔和因果链 | 渐进式构建一致性追踪数据 |

### UI决策

| 决策 | 原因 |
|------|------|
| 暖色纸质背景主题 | 模拟墨水书写体验，降低视觉疲劳 |
| 衬线字体用于标题 | 传递文学感和传统美学 |
| 可折叠侧边栏 | 最大化内容区域，同时保持导航可达 |
| 7步进度指示器（圆点+连接线） | 直观展示创作进度 |
| 步骤详情页用when分支 | 7个步骤差异大，独立页面更清晰 |

### 安全决策

| 决策 | 原因 |
|------|------|
| HttpURLConnection而非OkHttp | 减少依赖，功能足够 |
| JSONObject构建请求体 | 防止JSON注入 |
| org.json.JSONObject解析响应 | 避免正则解析截断问题 |
| API Key密码输入框 | 防止窥屏 |
| finally块disconnect | 防止HTTP连接泄漏 |

---

## 附录A：已修复Bug汇总

| 类别 | Bug | 修复方案 |
|------|-----|---------|
| 网络 | HTTP连接泄漏 | AiRepository添加`finally { conn?.disconnect() }` |
| 并发 | _isGenerating卡死 | 所有AI函数加`try-catch-finally` |
| 安全 | JSON注入 | 用`JSONObject`构建请求体 |
| 解析 | JSON解析截断 | 用`org.json.JSONObject`替代正则 |
| 逻辑 | 章节导航偏移 | 移除多余的`+1/-1` |
| 网络 | 无readTimeout | SettingsScreen添加`conn.readTimeout` |
| 空安全 | `!!`强制解包 | 改为`?.title ?: ""` |
| UI | `completedSteps`重组重建 | 用`remember(storyConfig)`缓存 |
| UI | FlowScreen步骤无法点击 | 使用`selectedStep`状态控制 |
| UI | 创建新作品FilterChip溢出 | 添加`horizontalScroll` |
| UI | 章节号/标题不显示 | 修复`GlassInput`文字颜色 |
| 数据 | 进度不保存 | `selectBook`时加载`loadProject` |
| 数据 | 各界面数据不同步 | 生成时同步写入数据库表 |

---

## 附录B：参考项目

- **NovelWriter** (`D:\mi-ai`) — 完整AI写作应用，Hilt依赖注入，12个数据库表
- **Dramatica Flow Python后端** — [GitHub: ydsgangge-ux/dramatica-flow](https://github.com/ydsgangge-ux/dramatica-flow)
- **HTML原型** (`D:\code (4).html`) — UI设计参考

---

*文档生成时间：2026-06-30*
*项目版本：v1.0.0*