[README.md](https://github.com/user-attachments/files/30478861/README.md)
# Dramatica Flow

> 基于 Dramatica 叙事理论的 AI 小说创作 Android 应用，支持双引擎（MiMo / DeepSeek）驱动，7 步向导式创作流程 + 参考小说创作双模式。
本项目基于 ydsgangge-ux/dramatica-flow 进行二次开发，在原项目基础上新增了参考小说创作模式、五层 Agent 写作管线、一致性追踪工具等功能增强。
---

## 功能概览

### 两种创作模式

| 模式 | 说明 |
|------|------|
| **全新创作** | 7 步向导式流程：基础信息 → 世界观构建 → 角色设计 → 大纲规划 → 章节创作 → AI 创作结果 → 完稿审校 |
| **参考小说创作** | 上传 TXT 分析参考小说 → 提取书名/题材/主题/世界观/角色/叙事/风格 → 使用分析结果或修改后创作 |

### 五层 Agent 写作管线

```
建筑师(Architect) → 写手(Writer) → 验证(Validator) → 审计(Auditor) → 因果提取(Causal Extractor)
```

- 审计发现问题自动修订（最多 2 轮）
- 写后结算：摘要生成 + 因果链/伏笔/情感/关系/信息边界状态同步

### 一致性工具

- **因果链**：事件因果关系网络，自动追踪剧情逻辑
- **伏笔追踪**：标记 open/resolved 状态，防止遗漏
- **角色关系网络**：角色间关系强度可视化
- **情感弧线**：角色情感变化追踪
- **时间线**：章节事件时间轴

### 写作工作台

- 章节编辑器（撤销/重做、自动保存）
- AI 写作助手（续写、润色、扩写、建议）
- 批量写 3 章
- 写作技能蒸馏：从参考小说中提取句式/词汇/叙事/对话/节奏风格

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM（ViewModel + StateFlow） |
| 数据库 | Room（13 张表，13 个 DAO） |
| 构建 | Gradle 8.7.3 + Kotlin 2.1.0 + KSP |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 |

---

## 快速开始

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35

### 构建运行

```bash
# 克隆仓库
git clone https://github.com/32145687/dramatica-flow.git
cd dramatica-flow

# 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 配置 AI 接口

1. 启动应用后进入「设置」页面
2. 填入 API 地址和密钥
3. 选择模型（支持 MiMo / DeepSeek 等兼容 OpenAI 接口的模型）
4. 点击「测试连接」验证

---

## 项目结构

```
app/src/main/java/com/dramatica/flow/
├── MainActivity.kt                    # 应用入口
├── data/
│   ├── AppDatabase.kt                 # Room 数据库定义 + 实体 + StoryConfig
│   ├── LocalRepository.kt             # 本地数据仓库
│   ├── AiRepository.kt                # AI 接口调用（支持 MiMo/DeepSeek）
│   ├── ArchitectAgent.kt              # 建筑师 Agent - 大纲规划
│   ├── PostWriteValidator.kt          # 写后验证器
│   ├── ContentRectifier.kt            # 内容纠错器
│   └── TextParser.kt                  # 文本解析工具
├── ui/
│   ├── DramaticaFlowApp.kt            # 主界面 + 导航
│   ├── components/
│   │   ├── AppSidebar.kt              # 侧边栏导航
│   │   └── Components.kt              # 通用 UI 组件
│   ├── screens/
│   │   ├── HomeScreen.kt              # 书架首页
│   │   ├── FlowScreen.kt              # 7 步创作流程主页
│   │   ├── FlowStepDetailScreen.kt    # 7 步详情页
│   │   ├── ReferenceFlowScreen.kt     # 参考创作流程
│   │   ├── NovelAnalysisScreen.kt     # 参考小说分析
│   │   ├── WritingScreen.kt           # 写作工作台
│   │   ├── WritingSkillScreen.kt      # 写作技能蒸馏
│   │   ├── CharactersScreen.kt        # 角色档案
│   │   ├── TrackingScreen.kt          # 一致性追踪
│   │   ├── TimelineScreen.kt          # 时间线
│   │   ├── TxtSplitScreen.kt          # TXT 拆分
│   │   └── SettingsScreen.kt          # 设置
│   ├── theme/
│   │   ├── Color.kt                   # 颜色定义
│   │   └── Theme.kt                   # 主题配置
│   └── viewmodel/
│       ├── MainViewModel.kt           # 核心业务逻辑
│       └── BookSession.kt             # 书籍会话管理
```

---

## 数据库设计

13 张 Room 表，通过 `bookId` 外键关联：

| 表名 | 说明 |
|------|------|
| `books` | 书籍信息 |
| `dramatica_projects` | 创作项目配置 |
| `chapters` | 章节内容 |
| `characters` | 角色档案 |
| `hooks` | 伏笔 |
| `causal_chain` | 因果链 |
| `relationships` | 角色关系 |
| `emotions` | 情感快照 |
| `timeline` | 时间线事件 |
| `writing_skills` | 写作技能 |
| `chapter_summaries` | 章节摘要 |
| `info_boundaries` | 信息边界 |
| `outline_nodes` | 大纲节点 |

---

## 会话管理

每本书拥有独立的 `BookSession`，包含：
- 创作状态（StoryConfig、大纲、摘要历史）
- 因果链、情感弧线、伏笔状态
- 写作技能蒸馏结果
- 生成任务 Job 引用（支持取消）

切换书籍时自动切换 Session，确保状态隔离。

---

## 许可证

MIT License
