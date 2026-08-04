# Dramatica Flow Android 项目记录

## 项目概述
基于Dramatica叙事理论的AI小说创作Android应用，支持7步创作流程和MiMo/DeepSeek双AI引擎。

## 技术栈
- Kotlin + Jetpack Compose + Material3
- Room数据库 + SharedPreferences
- Hilt未使用（直接在ViewModel中实例化Repository）
- compileSdk=35, minSdk=26, targetSdk=35

## 项目结构
```
app/src/main/java/com/dramatica/flow/
├── MainActivity.kt          # 入口
├── data/
│   ├── AppDatabase.kt       # Room数据库（8个Entity + DramaticaProjectEntity）
│   ├── LocalRepository.kt   # 本地数据仓库
│   ├── AiRepository.kt      # AI API调用（MiMo/DeepSeek）
│   └── PostWriteValidator.kt # 写后验证器（MiMo优化）
├── ui/
│   ├── DramaticaFlowApp.kt  # 主框架（侧边栏+页面路由）
│   ├── components/
│   │   ├── AppSidebar.kt    # 侧边栏导航
│   │   └── Components.kt    # 通用组件（InkCard/InkButton等）
│   ├── screens/
│   │   ├── HomeScreen.kt          # 书架首页
│   │   ├── FlowScreen.kt          # 创作流程（7步）
│   │   ├── FlowStepDetailScreen.kt # 步骤详情（7个子页面）
│   │   ├── CharactersScreen.kt    # 角色档案
│   │   ├── WritingScreen.kt       # 写作编辑器
│   │   ├── TrackingScreen.kt      # 一致性追踪
│   │   ├── TimelineScreen.kt      # 时间线
│   │   └── SettingsScreen.kt      # API设置
│   ├── theme/
│   │   ├── Color.kt          # 配色方案
│   │   └── Theme.kt          # 主题定义
│   └── viewmodel/
│       └── MainViewModel.kt  # 核心状态管理
```

## 7步创作流程
```
BASIC_INFO(1) → WORLD_BUILDING(2) → CHARACTER_DESIGN(3) → OUTLINE(4) → WRITING(5) → AI_RESULT(6) → TIMELINE(7)
```

## 核心架构

### 数据流
1. 用户填写基础信息 → 保存StoryConfig
2. 自动生成世界观 → AI调用 → 保存coreSetting
3. 自动生成角色 → AI调用 → 保存characters + CharacterEntity表
4. 自动生成大纲 → AI调用 → 保存outline
5. AI写章节 → 架构师→写手→校验→审计→因果链+摘要
6. 所有操作自动保存到DramaticaProjectEntity

### ViewModel状态机
- `_currentStep: DramaticaStep` - 当前步骤
- `_isGenerating: Boolean` - 防重复生成锁
- `_stepProgress/stepLoadingMessage` - 进度条
- `_storyConfig: StoryConfig` - 中心配置
- `_currentContent` - 当前章节内容

### 数据库表
| 表名 | 用途 |
|------|------|
| books | 书籍列表 |
| chapters | 章节内容 |
| characters | 角色档案 |
| hooks | 伏笔追踪 |
| causal_links | 因果链 |
| relationships | 角色关系 |
| emotions | 情感状态 |
| timeline_events | 时间线 |
| dramatica_projects | 创作项目状态 |

## API集成

### 支持的AI模型
- **MiMo v2.5** - 小米大模型
- **DeepSeek v4 Flash** - 快速推理

### API调用流程
```
AiRepository.generateContent(prompt, useMimo, maxTokens)
→ HttpURLConnection POST /chat/completions
→ JSONObject解析响应
→ AiResult.Success/Error
```

### MiMo后处理（postProcessForMimo）
- 删除重复"的/得/地"
- 删除废话开头（其实/说实话/坦白说）
- 心理陈述改动作（他心想→他想到）
- 拆分长句（，然后→。然后）

### 写后验证（PostWriteValidator）
- 通用8项：AI标记词、禁止句式、元叙事、报告式、集体反应、连续"了"字、超长段落、字数偏差
- MiMo专用3项：形容词密度、描述比例、连续长句

## 已修复的Bug
| Bug | 修复 |
|-----|------|
| HTTP连接泄漏 | AiRepository添加finally { conn?.disconnect() } |
| _isGenerating卡死 | 所有AI函数加try-catch-finally |
| JSON注入 | 用JSONObject构建请求体 |
| JSON解析截断 | 用org.json.JSONObject替代正则 |
| 章节导航偏移 | 移除多余的+1/-1 |
| 无readTimeout | SettingsScreen添加conn.readTimeout |
| !!强制解包 | 改为?.title ?: "" |
| completedSteps重组重建 | 用remember(storyConfig)缓存 |
| FlowScreen步骤无法点击 | 使用selectedStep状态控制 |
| 创建新作品FilterChip溢出 | 添加horizontalScroll |
| 章节号/标题不显示 | 修复GlassInput文字颜色 |
| 进度不保存 | selectBook时加载loadProject |
| 各界面数据不同步 | 生成时同步写入数据库表 |

## 关键设计决策
- StoryConfig作为中心数据结构，所有步骤读写
- DramaticaProjectEntity持久化所有状态
- _isGenerating锁防止重复AI调用
- 每个AI函数都有progress+loadingMessage
- 自动跳转：世界观→角色→大纲→章节创作（延迟1.5秒）
- 所有AI调用走AiRepository.generateContent()

## 构建信息
- APK路径：D:\xs-xm3\dramatica-local\app\build\outputs\apk\debug\app-debug.apk
- 依赖：androidx.compose:compose-bom:2025.05.00, Room 2.6.1, KSP 2.1.0-1.0.29
- 网络：INTERNET权限已添加

## 参考项目
- D:\mi-ai (NovelWriter) - 完整AI写作应用，Hilt依赖注入，12个数据库表
- GitHub: https://github.com/ydsgangge-ux/dramatica-flow - Python后端
- HTML原型：D:\code (4).html - UI设计参考
