package com.dramatica.flow.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dramatica.flow.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LocalRepository(app)
    private val aiRepo = AiRepository(app)
    private val appContext = app

    /** 判断当前配置的模型是否为 MiMo（用于 MiMo 特殊处理） */
    private fun isMimoModel(): Boolean =
        appContext.getSharedPreferences("api_settings", 0)
            .getString("api_model", "")?.lowercase()?.contains("mimo") == true

    /**
     * 智能提取摘要上下文，优先保留近期章节。
     * 对于 100 万字+ 长篇小说，确保 AI 能看到最近的剧情发展。
     * 策略：总长度 ≤ 8000 时全取；超过时取前 2000 字（总览）+ 后 6000 字（近期）。
     */
    private fun buildSummaryContext(summaryHistory: String): String {
        if (summaryHistory.length <= 8000) return summaryHistory
        val intro = summaryHistory.take(2000).trimEnd()
        val recent = summaryHistory.takeLast(6000).trimStart()
        // 确保 intro 的结尾是完整行
        val introEnd = intro.lastIndexOf('\n').let { if (it > 0) it else intro.length }
        return intro.take(introEnd) + "\n\n...（中间章节摘要已省略，详见因果链和伏笔）...\n\n" + recent
    }

    // ---- Session 管理 ----
    // 所有书籍的创作会话 Map<bookId, BookSession>
    private val _sessions = MutableStateFlow<Map<String, BookSession>>(emptyMap())
    // 当前活跃的书籍 ID
    private val _activeBookId = MutableStateFlow("")

    /** 获取当前活跃会话的内部方法 */
    private fun activeSession(): BookSession {
        val id = _activeBookId.value
        return _sessions.value[id] ?: error("No active session for bookId=$id。请先调用 selectBook()")
    }

    /** 获取当前活跃会话，失败时toast提示并返回null */
    private fun activeSessionOrNull(): BookSession? {
        val id = _activeBookId.value
        if (id.isBlank()) {
            viewModelScope.launch { _toast.emit("请先在首页选择一本书籍") }
            return null
        }
        return _sessions.value[id] ?: run {
            viewModelScope.launch { _toast.emit("请先在首页选择一本书籍") }
            null
        }
    }

    /** 从活跃会话中派生出 StateFlow 的辅助方法 */
    @Suppress("UNCHECKED_CAST")
    private fun <T> sessionState(selector: (BookSession) -> StateFlow<T>, default: T): StateFlow<T> {
        return combine(_activeBookId, _sessions) { id, sessions ->
            sessions[id]?.let(selector) ?: MutableStateFlow(default)
        }.flatMapLatest { it as Flow<T> }
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)
    }

    // ---- 创作项目状态（全部委托给活跃会话） ----
    val currentProject: StateFlow<DramaticaProjectEntity?> = sessionState({ it.currentProject }, null)
    val currentStep: StateFlow<DramaticaStep> = sessionState({ it.currentStep }, DramaticaStep.BASIC_INFO)
    val uiState: StateFlow<DramaticaUiState> = sessionState({ it.uiState }, DramaticaUiState.Idle)
    val storyConfig: StateFlow<StoryConfig> = sessionState({ it.storyConfig }, StoryConfig())
    val outline: StateFlow<String> = sessionState({ it.outline }, "")
    val causalChain: StateFlow<String> = sessionState({ it.causalChain }, "")
    val emotionalArcs: StateFlow<String> = sessionState({ it.emotionalArcs }, "")
    val pendingHooks: StateFlow<String> = sessionState({ it.pendingHooks }, "")
    val summaryHistory: StateFlow<String> = sessionState({ it.summaryHistory }, "")
    val currentContent: StateFlow<String> = sessionState({ it.currentContent }, "")
    val isGenerating: StateFlow<Boolean> = sessionState({ it.isGenerating }, false)
    val stepProgress: StateFlow<Float> = sessionState({ it.stepProgress }, 0f)
    val stepLoadingMessage: StateFlow<String> = sessionState({ it.stepLoadingMessage }, "")
    val generationError: StateFlow<String?> = sessionState({ it.generationError }, null)
    val editingChapter: StateFlow<ChapterEntity?> = sessionState({ it.editingChapter }, null)
    val currentChapterNum: StateFlow<Int> = sessionState({ it.currentChapterNum }, 1)

    // ---- 书籍列表 ----
    val currentBookId: StateFlow<String> = _activeBookId

    val books: StateFlow<List<BookEntity>> = repo.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook

    private val _currentBookIsReference = MutableStateFlow(false)
    val currentBookIsReference: StateFlow<Boolean> = _currentBookIsReference

    val chapters: StateFlow<List<ChapterEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getChapters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characters: StateFlow<List<CharacterEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getCharacters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hooks: StateFlow<List<HookEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getHooks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val causalChainList: StateFlow<List<CausalLinkEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getCausalChain(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val relationships: StateFlow<List<RelationshipEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getRelationships(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emotions: StateFlow<List<EmotionEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getEmotions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeline: StateFlow<List<TimelineEntity>> = _activeBookId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repo.getTimeline(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Toast ----
    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast

    // ---- 书籍操作 ----
    fun selectBook(bookId: String) {
        if (bookId == _activeBookId.value) return // 同一本书，无需切换
        val previousBookId = _activeBookId.value      // 保存旧值，失败时回滚
        val previousIsRef = _currentBookIsReference.value
        // 确保 Session 存在（不存在则创建）
        ensureSession(bookId)
        _activeBookId.value = bookId
        // 同步设置参考状态：优先从已加载的 session 中读取，避免 FlowScreen 闪现
        val existingProject = _sessions.value[bookId]?.currentProject?.value
        _currentBookIsReference.value = existingProject?.referenceAnalysis?.isNotBlank() == true
        viewModelScope.launch {
            try {
                _currentBook.value = repo.getBook(bookId)
                // 加载写作技能
                val s = _sessions.value[bookId]
                val skill = repo.getWritingSkill(bookId)
                s?.writingSkill?.value = skill
                writingSkillResult.value = skill
                // 通过 bookId 精确查找项目，替代标题匹配（标题可能被修改）
                val project = repo.getProjectByBookId(bookId)
                if (project != null) {
                    loadProjectFromDb(project)
                    _currentBookIsReference.value = project.referenceAnalysis.isNotBlank()
                } else {
                    // 兼容旧数据：再尝试按标题匹配
                    val projects = repo.getAllProjects().first()
                    val fallback = projects.find { it.title == _currentBook.value?.title && it.bookId.isBlank() }
                    if (fallback != null) {
                        // 旧项目没有 bookId，回填 bookId
                        repo.updateProject(fallback.copy(bookId = bookId))
                        loadProjectFromDb(fallback)
                        _currentBookIsReference.value = fallback.referenceAnalysis.isNotBlank()
                    } else {
                        _currentBookIsReference.value = false
                        val newProject = DramaticaProjectEntity(
                            bookId = bookId,
                            title = _currentBook.value?.title ?: "",
                            genre = _currentBook.value?.genre ?: "玄幻",
                            targetChapters = _currentBook.value?.targetChapters ?: 30
                        )
                        val id = repo.insertProject(newProject)
                        val s2 = _sessions.value[bookId] ?: return@launch
                        s2.currentProject.value = repo.getProjectById(id)
                        if (s2.storyConfig.value.title.isBlank()) {
                            s2.storyConfig.value = StoryConfig(
                                title = newProject.title,
                                genre = newProject.genre,
                                targetChapters = newProject.targetChapters
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // DB 加载失败，回滚 activeBookId 避免 UI 指向不可用的书籍
                _activeBookId.value = previousBookId
                _currentBookIsReference.value = previousIsRef
                _toast.emit("加载书籍数据失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    /** 确保指定 bookId 的 Session 存在 */
    private fun ensureSession(bookId: String) {
        if (!_sessions.value.containsKey(bookId)) {
            _sessions.value = _sessions.value + (bookId to BookSession(bookId))
        }
    }

    private fun loadProjectFromDb(project: DramaticaProjectEntity) {
        val s = activeSession()
        s.currentProject.value = project
        s.storyConfig.value = StoryConfig(
            title = project.title, genre = project.genre, briefIdea = project.briefIdea,
            targetChapters = project.targetChapters, coreSetting = project.coreSetting,
            characters = project.characters, outline = project.outline,
            colloquialStyle = project.colloquialStyle, useMemes = project.useMemes,
            referenceAnalysis = project.referenceAnalysis
        )
        s.outline.value = project.outline
        s.causalChain.value = project.causalChainHistory
        s.emotionalArcs.value = project.emotionalArcs
        s.pendingHooks.value = project.pendingHooks
        s.summaryHistory.value = project.summaryHistory
        s.currentStep.value = DramaticaStep.entries.firstOrNull { it.number == project.currentStep }
            ?: DramaticaStep.BASIC_INFO
    }

    fun createBook(title: String, genre: String) {
        val id = UUID.randomUUID().toString().take(8)
        // 同步设置 session 和 activeBookId
        ensureSession(id)
        _activeBookId.value = id
        _currentBookIsReference.value = false
        _currentBook.value = BookEntity(id = id, title = title, genre = genre)
        viewModelScope.launch {
            try {
                repo.insertBook(BookEntity(id = id, title = title, genre = genre))
                _toast.emit("「$title」已创建")
            } catch (e: Exception) {
                // DB 持久化失败，回滚 session 状态，避免内存与 DB 不一致
                _activeBookId.value = ""
                _currentBookIsReference.value = false
                _currentBook.value = null
                _sessions.value = _sessions.value - id
                _toast.emit("创建书籍失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            try {
                repo.deleteBook(book)
                repo.deleteWritingSkill(book.id)
                // 移除对应 Session
                _sessions.value = _sessions.value - book.id
                if (_activeBookId.value == book.id) {
                    _activeBookId.value = ""
                    _currentBook.value = null
                    _currentBookIsReference.value = false
                }
            } catch (e: Exception) {
                _toast.emit("删除书籍失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun renameBook(book: BookEntity, newTitle: String) {
        viewModelScope.launch {
            try {
                repo.updateBookTitle(book.id, newTitle)
                // 同步更新内存中的书名
                _sessions.value[book.id]?.storyConfig?.value?.let { config ->
                    _sessions.value[book.id]?.storyConfig?.value = config.copy(title = newTitle)
                }
                _sessions.value[book.id]?.currentProject?.value?.let { project ->
                    _sessions.value[book.id]?.currentProject?.value = project.copy(title = newTitle)
                    // 同步更新数据库中的项目
                    repo.updateProject(project.copy(title = newTitle))
                }
                if (_currentBook.value?.id == book.id) {
                    _currentBook.value = _currentBook.value?.copy(title = newTitle)
                }
                _toast.emit("已重命名为「$newTitle」")
            } catch (e: Exception) {
                _toast.emit("重命名失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 功能A：参考小说分析 ----
    val novelAnalysisResult = MutableStateFlow<String?>(null)
    val novelAnalysisProgress = MutableStateFlow(0f)
    val novelAnalysisMessage = MutableStateFlow("")
    val isNovelAnalyzing = MutableStateFlow(false)
    private var novelAnalysisJob: Job? = null
    private var createBookFromAnalysisJob: Job? = null  // 用于 updateBookFromAnalysis 等待 DB 插入完成

    /** 清除分析结果，用于离开分析页面时清理 */
    fun clearNovelAnalysis() {
        novelAnalysisJob?.cancel()
        novelAnalysisJob = null
        novelAnalysisResult.value = null
        novelAnalysisProgress.value = 0f
        novelAnalysisMessage.value = ""
        isNovelAnalyzing.value = false
    }

    /**
     * 创建书籍并绑定参考小说分析结果。
     */
    fun createBookFromAnalysis(title: String, genre: String, analysis: String) {
        val id = UUID.randomUUID().toString().take(8)
        // 同步设置 session 和 activeBookId，确保 UI 立即拿到正确的 session
        ensureSession(id)
        _activeBookId.value = id
        _currentBookIsReference.value = true
        _currentBook.value = BookEntity(id = id, title = title, genre = genre)
        val s = _sessions.value[id] ?: return
        s.storyConfig.value = StoryConfig(
            title = title, genre = genre,
            referenceAnalysis = analysis
        )
        s.currentProject.value = DramaticaProjectEntity(
            bookId = id, title = title, genre = genre,
            referenceAnalysis = analysis
        )
        viewModelScope.launch {
            try {
                // 异步持久化到 DB
                repo.insertBook(BookEntity(id = id, title = title, genre = genre))
                val projectId = repo.insertProject(DramaticaProjectEntity(
                    bookId = id, title = title, genre = genre,
                    referenceAnalysis = analysis
                ))
                // 同步 DB 自增 ID 到 session，确保后续 updateProject 能找到正确的记录
                s.currentProject.value = s.currentProject.value?.copy(id = projectId)
                _toast.emit("「$title」已创建，已绑定参考分析")
            } catch (e: Exception) {
                // DB 持久化失败，回滚 session 状态，避免内存与 DB 不一致
                _activeBookId.value = ""
                _currentBookIsReference.value = false
                _currentBook.value = null
                _sessions.value = _sessions.value - id
                _toast.emit("创建书籍失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }.also { createBookFromAnalysisJob = it }
    }

    /**
     * 更新已有书籍的参考分析和书名（用于修改后创作确认）。
     */
    fun updateBookFromAnalysis(bookId: String, newTitle: String, genre: String, analysis: String) {
        val s = _sessions.value[bookId] ?: return
        // 保存旧值，DB 失败时回滚
        val oldStoryConfig = s.storyConfig.value
        val oldProject = s.currentProject.value
        val oldBook = _currentBook.value
        // 从格式化 analysis 字符串中解析各字段，同步写入 coreSetting / characters
        val parsedWorld = TextParser.extractFieldMulti(analysis, "世界观设定")
        val parsedTheme = TextParser.extractField(analysis, "核心主题")
        val parsedChars = TextParser.extractFieldMulti(analysis, "角色原型")
        val parsedNarrative = TextParser.extractFieldMulti(analysis, "叙事结构")
        val parsedStyle = TextParser.extractFieldMulti(analysis, "语言风格")
        val coreSetting = buildString {
            if (parsedWorld.isNotBlank()) append(parsedWorld)
            if (parsedTheme.isNotBlank()) {
                if (parsedWorld.isNotBlank()) append("\n")
                append("主题：$parsedTheme")
            }
            if (parsedNarrative.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("叙事：$parsedNarrative")
            }
            if (parsedStyle.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("风格：$parsedStyle")
            }
        }
        s.storyConfig.value = s.storyConfig.value.copy(
            title = newTitle, genre = genre,
            referenceAnalysis = analysis,
            coreSetting = coreSetting.ifBlank { s.storyConfig.value.coreSetting },
            characters = parsedChars.ifBlank { s.storyConfig.value.characters }
        )
        // 同步更新 session 中的 project，标记为已配置，避免后续操作使用旧数据
        s.currentProject.value = s.currentProject.value?.copy(
            title = newTitle, genre = genre,
            referenceAnalysis = analysis,
            coreSetting = coreSetting.ifBlank { s.currentProject.value?.coreSetting ?: "" },
            characters = parsedChars.ifBlank { s.currentProject.value?.characters ?: "" },
            referenceCreationConfigured = true
        )
        viewModelScope.launch {
            try {
                // 等待 createBookFromAnalysis 的 DB 插入完成，确保 project.id 已赋值
                createBookFromAnalysisJob?.join()
                repo.updateBookTitle(bookId, newTitle)
                _currentBook.value = _currentBook.value?.copy(title = newTitle)
                val project = s.currentProject.value
                if (project != null) {
                    repo.updateProject(project.copy(
                        title = newTitle, genre = genre,
                        referenceAnalysis = analysis,
                        coreSetting = coreSetting.ifBlank { project.coreSetting },
                        characters = parsedChars.ifBlank { project.characters },
                        referenceCreationConfigured = true
                    ))
                }
                _toast.emit("创作设定已更新")
            } catch (e: Exception) {
                // DB 持久化失败，回滚 session 状态，避免内存与 DB 不一致
                s.storyConfig.value = oldStoryConfig
                s.currentProject.value = oldProject
                _currentBook.value = oldBook
                _toast.emit("更新失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 功能A-2：AI 逐项重写（修改后创作） ----
    val fieldRewriteLoading = MutableStateFlow("")  // 当前正在重写的字段名，空=无
    val fieldRewriteResult = MutableStateFlow<String?>(null)
    val activeRewriteFieldKey = MutableStateFlow("")  // 持久化追踪当前重写的字段，供 LaunchedEffect 判断结果归属

    /** AI 重写单个字段，用于修改后创作流程。allFields 用于交叉上下文。 */
    fun aiRewriteField(
        fieldKey: String, fieldLabel: String, currentContent: String,
        referenceAnalysis: String, allFields: Map<String, String> = emptyMap()
    ) {
        if (fieldRewriteLoading.value.isNotBlank()) return  // 防止并发
        fieldRewriteLoading.value = fieldKey
        activeRewriteFieldKey.value = fieldKey  // 持久化记录，避免 finally 清空后 LaunchedEffect 无法判断来源
        fieldRewriteResult.value = null
        viewModelScope.launch {
            try {
                val prompt = buildRewritePrompt(fieldKey, fieldLabel, currentContent, referenceAnalysis, allFields)
                val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.7f)
                if (result is AiResult.Success) {
                    fieldRewriteResult.value = result.content.trim()
                } else {
                    _toast.emit("AI重写失败，请重试")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("AI重写出错：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                fieldRewriteLoading.value = ""
            }
        }
    }

    /** 一键AI重写所有字段（顺序执行，逐个更新） */
    fun aiRewriteAllFields(
        fields: List<Triple<String, String, String>>, // (fieldKey, fieldLabel, currentContent)
        referenceAnalysis: String,
        allFields: Map<String, String>
    ) {
        if (fieldRewriteLoading.value.isNotBlank()) return
        // 清除可能残留的旧结果（如上次重写被中断）
        fieldRewriteResult.value = null
        activeRewriteFieldKey.value = ""
        viewModelScope.launch {
            try {
                for ((fieldKey, fieldLabel, currentContent) in fields) {
                    fieldRewriteLoading.value = fieldKey
                    activeRewriteFieldKey.value = fieldKey
                    fieldRewriteResult.value = null
                    try {
                        val prompt = buildRewritePrompt(fieldKey, fieldLabel, currentContent, referenceAnalysis, allFields)
                        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.7f)
                        if (result is AiResult.Success) {
                            fieldRewriteResult.value = result.content.trim()
                        } else {
                            _toast.emit("「$fieldLabel」AI重写失败，跳过")
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _toast.emit("「$fieldLabel」重写出错：${e.message?.take(30) ?: "未知"}")
                    }
                    // 等待 UI 处理完当前结果再继续下一个
                    delay(300)
                }
            } finally {
                fieldRewriteLoading.value = ""
                fieldRewriteResult.value = null
                activeRewriteFieldKey.value = ""
            }
            _toast.emit("一键AI重写完成")
        }
    }

    /** 清除重写结果 */
    fun clearFieldRewriteResult() {
        fieldRewriteResult.value = null
        activeRewriteFieldKey.value = ""
    }

    private fun buildRewritePrompt(
        fieldKey: String, fieldLabel: String, currentContent: String,
        referenceAnalysis: String, allFields: Map<String, String>
    ): String {
        // 提取参考分析中的相关部分作为上下文
        val refExcerpt = extractRefContext(referenceAnalysis, fieldKey)
        return buildString {
            append("你是一位资深小说编辑，请对以下「$fieldLabel」进行创意重写。\n\n")
            if (refExcerpt.isNotBlank()) {
                append("【参考小说相关设定】\n$refExcerpt\n\n")
            }
            // 交叉上下文：包含所有其他已编辑字段，让AI重写时考虑全局一致性
            val otherFields = allFields.filter { it.key != fieldKey && it.value.isNotBlank() }
            if (otherFields.isNotEmpty()) {
                append("【其他已设定的创作元素（请保持一致性）】\n")
                otherFields.forEach { (k, v) ->
                    val label = fieldLabelMap[k] ?: k
                    append("$label：${v.take(200)}\n")
                }
                append("\n")
            }
            if (currentContent.isNotBlank()) {
                append("【当前设定】\n$currentContent\n\n")
            }
            append("请进行创意重写，要求：\n")
            when (fieldKey) {
                "title" -> append("- 保留参考小说的风格韵味\n- 起一个有吸引力的书名\n- 可直接使用，输出纯书名即可")
                "genre" -> append("- 基于参考小说特征，给出准确的题材分类\n- 可直接使用，输出纯题材名即可")
                "theme" -> append("- 深化核心思想，让主题更有层次感\n- 2-3句话即可\n- 输出纯文本，不要加标签")
                "world" -> append("- 扩展世界观细节，补充设定\n- 保持与参考小说风格一致\n- 150-300字，直接输出设定文本")
                "characters" -> append("- 丰富角色设定，包括外貌、性格、动机、关系\n- 可修改角色名，让角色更鲜活\n- 200-500字，直接输出角色描述")
                "narrative" -> append("- 优化叙事结构设计\n- 明确故事推进节奏\n- 100-200字，直接输出叙事方案")
                "style" -> append("- 细化语言风格描述\n- 包括对话、描写、节奏等\n- 100-200字，直接输出风格描述")
                else -> append("- 保持参考小说风格\n- 直接输出改写结果")
            }
            append("\n\n请直接输出改写结果，不要加「标签：」前缀，不要加解释。")
        }
    }

    /** 从参考分析中提取与指定字段相关的内容 */
    private fun extractRefContext(referenceAnalysis: String, fieldKey: String): String {
        val mapping = mapOf(
            "title" to "书名", "genre" to "题材",
            "theme" to "核心主题|主题",
            "world" to "世界观|世界设定",
            "characters" to "角色|角色原型|人物",
            "narrative" to "叙事|叙事结构|故事推进",
            "style" to "语言风格|风格|文风"
        )
        val pattern = mapping[fieldKey] ?: return ""
        val regex = Regex("""($pattern)[：:]\s*([\s\S]+?)(?=\n\n|\n\S+[：:]|\Z)""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(referenceAnalysis)?.groupValues?.get(2)?.trim()?.take(500) ?: ""
    }

    /**
     * 分析小说 TXT 文件，提取元素和主题。
     * 大文件自动分块分析，最终汇总。
     */
    fun analyzeNovel(fileName: String, content: String) {
        novelAnalysisJob?.cancel()  // 取消上一次分析，避免新旧协程并发更新进度
        novelAnalysisJob = viewModelScope.launch {
            isNovelAnalyzing.value = true
            novelAnalysisProgress.value = 0f
            novelAnalysisMessage.value = "准备分析..."
            try {
                val totalChars = content.length
                val chunkSize = 8000

                if (totalChars <= chunkSize * 2) {
                    // 小文件，全部分析
                    novelAnalysisMessage.value = "正在分析小说..."
                    novelAnalysisProgress.value = 0.5f
                    val result = analyzeNovelChunk(content, fileName, "全文")
                    novelAnalysisResult.value = result
                    novelAnalysisProgress.value = 1f
                    novelAnalysisMessage.value = "分析完成"
                    _toast.emit("分析完成")
                    // 短暂延迟，确保 Compose 先处理 analysisResult 再处理 isNovelAnalyzing
                    delay(50)
                } else {
                    // 大文件，分块分析
                    val chunks = mutableListOf<String>()
                    // 开头 3 块
                    chunks.add(content.take(chunkSize))
                    chunks.add(content.substring(chunkSize, (chunkSize * 2).coerceAtMost(totalChars)))
                    chunks.add(content.substring((chunkSize * 2).coerceAtMost(totalChars), (chunkSize * 3).coerceAtMost(totalChars)))
                    // 中间 2 块
                    val midStart = (totalChars / 2 - chunkSize).coerceAtLeast(0)
                    chunks.add(content.substring(midStart, (midStart + chunkSize).coerceAtMost(totalChars)))
                    chunks.add(content.substring((midStart + chunkSize).coerceAtMost(totalChars), (midStart + chunkSize * 2).coerceAtMost(totalChars)))
                    // 结尾 1 块
                    chunks.add(content.takeLast(chunkSize))

                    // 过滤空块
                    val validChunks = chunks.filter { it.length >= 100 }
                    val chunkResults = mutableListOf<String>()
                    for ((i, chunk) in validChunks.withIndex()) {
                        novelAnalysisMessage.value = "分析中 ${i + 1}/${validChunks.size} 块..."
                        novelAnalysisProgress.value = i.toFloat() / validChunks.size
                        val result = analyzeNovelChunk(chunk, fileName, "第${i + 1}块")
                        if (result.isNotBlank()) chunkResults.add(result)
                    }

                    // 汇总
                    novelAnalysisMessage.value = "正在汇总分析结果..."
                    novelAnalysisProgress.value = 0.9f
                    val merged = mergeNovelAnalysis(chunkResults, fileName)
                    novelAnalysisResult.value = merged
                    novelAnalysisProgress.value = 1f
                    novelAnalysisMessage.value = "分析完成"
                    _toast.emit("分析完成（已分析 ${chunks.size} 个片段）")
                    // 短暂延迟，确保 Compose 先处理 analysisResult 再处理 isNovelAnalyzing
                    delay(50)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                novelAnalysisMessage.value = "分析失败"
                _toast.emit("分析失败：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                isNovelAnalyzing.value = false
            }
        }
    }

    private suspend fun analyzeNovelChunk(chunk: String, fileName: String, label: String): String {
        val prompt = buildString {
            append("请分析以下小说片段（$label），提取以下信息，严格按指定格式输出（每行格式为「标签：内容」）：\n\n")
            append("书名：根据内容推断合适的小说名\n")
            append("题材：如玄幻、科幻、都市、悬疑、言情等\n")
            append("核心主题：小说想表达的核心思想或母题\n")
            append("世界观设定：时间背景、空间设定、规则体系\n")
            append("角色原型：主角、反派、配角的特点和关系模式\n")
            append("叙事结构：故事推进方式（线性/倒叙/多线等）\n")
            append("语言风格：口语化程度、描写密度、对话占比\n\n")
            append("=== 小说片段 ===\n")
            append(chunk.take(8000))
            append("\n\n请直接输出分析结果，严格按上述「标签：内容」格式逐条输出，不要加编号。")
        }
        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.5f)
        return if (result is AiResult.Success) result.content.trim() else ""
    }

    private suspend fun mergeNovelAnalysis(chunkResults: List<String>, fileName: String): String {
        if (chunkResults.size == 1) return chunkResults.first()
        val prompt = buildString {
            append("以下是同一部小说（$fileName）的多段分析结果，请合并去重，输出一份完整的分析报告：\n\n")
            chunkResults.forEachIndexed { i, r ->
                append("=== 第${i + 1}块分析 ===\n$r\n\n")
            }
            append("请合并为一份完整报告，去除重复内容，保留所有不重复的特征，按以下格式输出：\n")
            append("书名：\n题材：\n核心主题：\n世界观设定：\n角色原型：\n叙事结构：\n语言风格：")
        }
        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.3f)
        return if (result is AiResult.Success) result.content.trim() else chunkResults.joinToString("\n\n")
    }

    // ---- 功能B：写作技能蒸馏 ----
    val writingSkillResult = MutableStateFlow<WritingSkillEntity?>(null)
    val writingSkillProgress = MutableStateFlow(0f)
    val writingSkillMessage = MutableStateFlow("")

    /**
     * 蒸馏小说的写作风格，生成 WritingSkill 并绑定到当前书籍。
     */
    fun distillWritingSkill(fileName: String, content: String) {
        val bookId = _activeBookId.value
        if (bookId.isBlank()) {
            viewModelScope.launch { _toast.emit("请先选择一本书籍") }
            return
        }
        viewModelScope.launch {
            writingSkillProgress.value = 0f
            writingSkillMessage.value = "准备蒸馏..."
            try {
                val totalChars = content.length
                val chunkSize = 8000
                // 风格蒸馏需要均匀采样，确保块不重叠
                val sampleCount = 10.coerceAtMost((totalChars / chunkSize).coerceAtLeast(1))
                val chunks = mutableListOf<String>()

                if (sampleCount <= 1) {
                    chunks.add(content.take(chunkSize))
                } else {
                    val step = (totalChars - chunkSize) / (sampleCount - 1)
                    for (i in 0 until sampleCount) {
                        val start = i * step
                        val end = (start + chunkSize).coerceAtMost(totalChars)
                        chunks.add(content.substring(start, end))
                    }
                }

                val chunkResults = mutableListOf<String>()
                for ((i, chunk) in chunks.withIndex()) {
                    writingSkillMessage.value = "蒸馏中 ${i + 1}/${chunks.size} 块..."
                    writingSkillProgress.value = i.toFloat() / chunks.size
                    val result = distillChunk(chunk, fileName, i + 1)
                    if (result.isNotBlank()) chunkResults.add(result)
                }

                // 汇总蒸馏
                writingSkillMessage.value = "正在汇总风格特征..."
                writingSkillProgress.value = 0.9f
                val merged = mergeDistillResults(chunkResults, fileName)

                val skill = WritingSkillEntity(
                    bookId = bookId,
                    sourceNovel = fileName,
                    styleProfile = merged,
                    sentencePatterns = extractSection(merged, "句式模式"),
                    vocabularyFingerprint = extractSection(merged, "词汇指纹"),
                    narrativeStyle = extractSection(merged, "叙事手法"),
                    dialogueStyle = extractSection(merged, "对话风格"),
                    pacingStyle = extractSection(merged, "节奏特征")
                )
                repo.saveWritingSkill(skill)
                val s = _sessions.value[bookId]
                s?.writingSkill?.value = skill
                writingSkillResult.value = skill
                writingSkillProgress.value = 1f
                writingSkillMessage.value = "蒸馏完成"
                _toast.emit("写作技能蒸馏完成")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                writingSkillMessage.value = "蒸馏失败"
                _toast.emit("蒸馏失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    private suspend fun distillChunk(chunk: String, fileName: String, index: Int): String {
        val prompt = buildString {
            append("请深度分析以下小说片段的写作风格特征（第${index}块），提取：\n\n")
            append("1. 句式模式：长短句比例、断句习惯、标点偏好、段落长度\n")
            append("2. 词汇指纹：高频词汇、禁用词、惯用搭配、成语使用频率\n")
            append("3. 叙事手法：视角类型、白描/渲染倾向、对话占比、心理描写方式\n")
            append("4. 对话风格：语气词习惯、称呼习惯、交锋模式、对话节奏\n")
            append("5. 节奏特征：段落长度分布、场景切换频率、紧张/舒缓交替模式\n\n")
            append("=== 小说片段 ===\n")
            append(chunk.take(8000))
            append("\n\n请直接输出分析结果，按上述 5 项逐条输出。")
        }
        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.4f)
        return if (result is AiResult.Success) result.content.trim() else ""
    }

    private suspend fun mergeDistillResults(chunkResults: List<String>, fileName: String): String {
        if (chunkResults.size == 1) return chunkResults.first()
        val prompt = buildString {
            append("以下是同一部小说（$fileName）的多段风格分析结果，请合并去重，输出一份完整的风格特征报告：\n\n")
            chunkResults.forEachIndexed { i, r ->
                append("=== 第${i + 1}块 ===\n$r\n\n")
            }
            append("请合并为一份完整报告，去重，保留所有不重复的写作特征，按以下格式输出：\n")
            append("句式模式：\n词汇指纹：\n叙事手法：\n对话风格：\n节奏特征：")
        }
        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.3f)
        return if (result is AiResult.Success) result.content.trim() else chunkResults.joinToString("\n\n")
    }

    /**
     * 清除当前书籍的写作技能。
     */
    fun clearWritingSkill() {
        val bookId = _activeBookId.value
        if (bookId.isBlank()) return
        viewModelScope.launch {
            repo.deleteWritingSkill(bookId)
            val s = _sessions.value[bookId]
            s?.writingSkill?.value = null
            _toast.emit("写作技能已清除")
        }
    }

    private fun extractSection(text: String, label: String): String {
        val pattern = Regex("""$label[：:]\s*(.+?)(?=\n\S+[：:]|\n\n|\Z)""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.take(500) ?: ""
    }

    // ---- 上下文注入辅助方法 ----

    /**
     * 获取参考小说分析上下文（功能A），用于注入各步骤 prompt。
     */
    private fun getReferenceContext(config: StoryConfig): String {
        val ref = config.referenceAnalysis
        if (ref.isBlank()) return ""
        return buildString {
            append("【参考小说分析——请以此为依据进行创作】\n")
            append(ref.take(3000))
            append("\n\n")
        }
    }

    /**
     * 获取写作技能上下文（功能B），用于注入章节写作 prompt。
     */
    private fun getWritingSkillContext(): String {
        val skill = activeSessionOrNull()?.writingSkill?.value ?: return ""
        return buildString {
            append("【写作技能——请严格遵循以下风格写作】\n")
            if (skill.sentencePatterns.isNotBlank()) append("句式：${skill.sentencePatterns}\n")
            if (skill.vocabularyFingerprint.isNotBlank()) append("词汇：${skill.vocabularyFingerprint}\n")
            if (skill.narrativeStyle.isNotBlank()) append("叙事：${skill.narrativeStyle}\n")
            if (skill.dialogueStyle.isNotBlank()) append("对话：${skill.dialogueStyle}\n")
            if (skill.pacingStyle.isNotBlank()) append("节奏：${skill.pacingStyle}\n")
            if (skill.styleProfile.isNotBlank()) append("\n风格概要：${skill.styleProfile.take(1000)}\n")
            append("\n")
        }
    }

    // ---- 创作流程操作 ----
    fun goToStep(step: DramaticaStep) {
        val s = activeSessionOrNull() ?: return
        val config = s.storyConfig.value
        when (step) {
            DramaticaStep.WORLD_BUILDING -> {
                if (config.title.isBlank()) {
                    viewModelScope.launch { _toast.emit("请先填写标题") }
                    return
                }
            }
            DramaticaStep.CHARACTER_DESIGN -> {
                if (config.coreSetting.isBlank()) {
                    viewModelScope.launch { _toast.emit("请先生成世界观") }
                    return
                }
            }
            DramaticaStep.OUTLINE -> {
                if (config.coreSetting.isBlank() || config.characters.isBlank()) {
                    viewModelScope.launch { _toast.emit("请先完成世界观和角色设计") }
                    return
                }
            }
            DramaticaStep.WRITING -> {
                if (config.coreSetting.isBlank() || config.outline.isBlank()) {
                    viewModelScope.launch { _toast.emit("请先完成大纲规划") }
                    return
                }
            }
            else -> {}
        }
        s.currentStep.value = step
        saveProject()
    }

    fun nextStep() {
        val s = activeSessionOrNull() ?: return
        s.currentStep.value.next?.let { goToStep(it) }
    }

    fun prevStep() {
        val s = activeSessionOrNull() ?: return
        s.currentStep.value.prev?.let { goToStep(it) }
    }

    // ---- 取消生成 ----
    fun cancelGeneration() {
        val s = activeSessionOrNull() ?: return
        s.generationJob?.cancel()
        s.generationJob = null
        s.isGenerating.value = false
        s.uiState.value = DramaticaUiState.Idle
        s.stepProgress.value = 0f
        viewModelScope.launch { _toast.emit("已取消生成") }
    }

    // ---- 跳过自动生成步骤 ----
    fun skipCurrentStep() {
        val s = activeSessionOrNull() ?: return
        val step = s.currentStep.value
        if (step.number !in 2..4) return
        when (step) {
            DramaticaStep.WORLD_BUILDING -> s.storyConfig.value = s.storyConfig.value.copy(coreSetting = "（手动填写）")
            DramaticaStep.CHARACTER_DESIGN -> s.storyConfig.value = s.storyConfig.value.copy(characters = "（手动填写）")
            DramaticaStep.OUTLINE -> {
                s.storyConfig.value = s.storyConfig.value.copy(outline = "（手动填写）")
                s.outline.value = "（手动填写）"
            }
            else -> {}
        }
        saveProject()
        nextStep()
    }

    fun clearGenerationError() {
        activeSessionOrNull()?.generationError?.value = null
    }

    fun updateStoryConfig(config: StoryConfig) {
        val s = activeSessionOrNull() ?: return
        s.storyConfig.value = config
        viewModelScope.launch {
            val project = s.currentProject.value ?: return@launch
            repo.updateProject(project.copy(
                title = config.title, genre = config.genre, briefIdea = config.briefIdea,
                targetChapters = config.targetChapters, updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun updateCoreSetting(setting: String) {
        val s = activeSessionOrNull() ?: return
        s.storyConfig.value = s.storyConfig.value.copy(coreSetting = setting)
        viewModelScope.launch {
            val project = s.currentProject.value ?: return@launch
            repo.updateProject(project.copy(coreSetting = setting, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateCharacters(characters: String) {
        val s = activeSessionOrNull() ?: return
        s.storyConfig.value = s.storyConfig.value.copy(characters = characters)
        viewModelScope.launch {
            val project = s.currentProject.value ?: return@launch
            repo.updateProject(project.copy(characters = characters, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateOutline(text: String) {
        val s = activeSessionOrNull() ?: return
        s.outline.value = text
        s.storyConfig.value = s.storyConfig.value.copy(outline = text)
        viewModelScope.launch {
            val project = s.currentProject.value ?: return@launch
            repo.updateProject(project.copy(outline = text, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleColloquialStyle() {
        val s = activeSessionOrNull() ?: return
        s.storyConfig.value = s.storyConfig.value.copy(colloquialStyle = !s.storyConfig.value.colloquialStyle)
        saveProject()
    }

    fun toggleUseMemes() {
        val s = activeSessionOrNull() ?: return
        s.storyConfig.value = s.storyConfig.value.copy(useMemes = !s.storyConfig.value.useMemes)
        saveProject()
    }

    // ---- AI生成 ----
    fun generateWorldBuilding() {
        val s = activeSessionOrNull() ?: return
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        val config = s.storyConfig.value
        s.isGenerating.value = true
        s.generationError.value = null
        s.uiState.value = DramaticaUiState.AutoGenerating("core_setting", "AI正在构建世界观...")
        s.stepProgress.value = 0.1f; s.stepLoadingMessage.value = "AI正在构建世界观..."
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = getReferenceContext(config) +
                    "请为一部「${config.genre}」小说创建完整的世界观设定。\n" +
                    "小说名：${config.title}\n" +
                    (if (config.briefIdea.isNotBlank()) "核心创意：${config.briefIdea}\n" else "") +
                    "\n请包含：世界名称和背景、核心规则/力量体系、3个关键地点、社会结构、历史背景。\n" +
                    "要求1000字以上，直接输出内容。"
                val result = aiRepo.generateContent(prompt, maxTokens = 4096, temperature = 0.8f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        s.storyConfig.value = config.copy(coreSetting = result.content)
                        s.uiState.value = DramaticaUiState.WorldGenerated(result.content)
                        saveProjectToSession(s)
                        _toast.emit("世界观生成完成")
                    }
                    is AiResult.Error -> {
                        s.generationError.value = result.message
                        _toast.emit("世界观生成失败：${result.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                s.generationError.value = e.message?.take(100) ?: "未知错误"
                _toast.emit("世界观生成异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun generateCharacters() {
        val s = activeSessionOrNull() ?: return
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        val config = s.storyConfig.value
        val bookId = _activeBookId.value
        s.isGenerating.value = true
        s.generationError.value = null
        s.uiState.value = DramaticaUiState.AutoGenerating("characters", "AI正在设计角色...")
        s.stepProgress.value = 0.1f; s.stepLoadingMessage.value = "AI正在设计角色..."
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = getReferenceContext(config) +
                    "请为「${config.title}」设计角色，基于 Dramatica 叙事理论的角色职能体系。\n类型：${config.genre}\n世界观：${config.coreSetting.take(300)}\n" +
                    "\n设计以下角色（至少 4 个，最多 7 个）：\n" +
                    "• 主角（Protagonist）— 推动故事前进的核心力量\n" +
                    "• 反派（Antagonist）— 与主角目标对立的对抗者\n" +
                    "• 冲击者（Impact Character）— 改变主角认知的关键人物\n" +
                    "• 守护者（Guardian）— 导师/引导者\n" +
                    "• 阻碍者（Contagonist）— 表面帮助实则拖延\n" +
                    "• 伙伴（Sidekick）— 忠诚的支持者\n" +
                    "• 怀疑者（Skeptic）— 质疑与反面声音\n\n" +
                    "每个角色提供：名字、角色类型（标注上述中文名称）、性格特点、背景故事。\n" +
                    "用---分隔每个角色。直接输出内容。"
                val result = aiRepo.generateContent(prompt, maxTokens = 4096, temperature = 0.8f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        s.storyConfig.value = config.copy(characters = result.content)
                        if (bookId.isNotBlank()) {
                            val charBlocks = result.content.split("---").filter { it.isNotBlank() }
                            charBlocks.forEachIndexed { index, block ->
                                val lines = block.trim().lines().filter { it.isNotBlank() }
                                if (lines.isEmpty()) return@forEachIndexed

                                // 找到真正的名字行：跳过"角色一"、"角色1"等标签行
                                val nameLine = findNameLine(lines, index)
                                val name = extractCharacterName(nameLine, index)
                                val type = detectCharacterType(nameLine)

                                // 描述：名字行之后的所有行，但要排除名字行之前的标签行
                                val nameLineIdx = lines.indexOf(nameLine)
                                val desc = lines.drop(nameLineIdx + 1).joinToString(" ").take(200)
                                repo.insertCharacter(CharacterEntity(bookId = bookId, name = name.take(10),
                                    role = when(type) {
                                        "protagonist" -> "主角"
                                        "antagonist" -> "反派"
                                        "sidekick" -> "伙伴"
                                        "impact" -> "冲击者"
                                        "guardian" -> "守护者"
                                        "contagonist" -> "阻碍者"
                                        "skeptic" -> "怀疑者"
                                        else -> "影响者"
                                    },
                                    avatar = name.firstOrNull()?.toString() ?: "?", type = type, description = desc))
                            }
                        }
                        s.uiState.value = DramaticaUiState.CharactersGenerated(result.content)
                        // 自动生成角色关系网络
                        val generatedChars = repo.getCharacters(bookId).first()
                        if (generatedChars.size >= 2) {
                            autoGenerateRelationships(bookId, generatedChars)
                        }
                        saveProjectToSession(s)
                        _toast.emit("角色设计完成")
                    }
                    is AiResult.Error -> {
                        s.generationError.value = result.message
                        _toast.emit("角色设计失败：${result.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                s.generationError.value = e.message?.take(100) ?: "未知错误"
                _toast.emit("角色设计异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun generateOutline() {
        val s = activeSessionOrNull() ?: return
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        val config = s.storyConfig.value
        s.isGenerating.value = true
        s.generationError.value = null
        s.uiState.value = DramaticaUiState.AutoGenerating("outline", "AI正在生成大纲...")
        s.stepProgress.value = 0.1f; s.stepLoadingMessage.value = "AI正在生成大纲..."
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = getReferenceContext(config) +
                    "请为「${config.title}」生成完整的小说大纲。\n类型：${config.genre}\n目标章节数：${config.targetChapters}\n" +
                    "世界观：${config.coreSetting.take(300)}\n角色：${config.characters.take(300)}\n" +
                    "\n按卷划分（5卷），每卷列出关键章节和情节点。直接输出内容。"
                val result = aiRepo.generateContent(prompt, maxTokens = 4096, temperature = 0.6f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.8f; s.stepLoadingMessage.value = "正在生成章节标题..."
                        s.outline.value = result.content
                        s.storyConfig.value = config.copy(outline = result.content)
                        saveProjectToSession(s)
                        // 生成章节标题
                        generateChapterTitles(s, config)
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        s.uiState.value = DramaticaUiState.OutlineGenerated(result.content)
                        _toast.emit("大纲生成完成，已生成章节标题")
                    }
                    is AiResult.Error -> {
                        s.generationError.value = result.message
                        _toast.emit("大纲生成失败：${result.message}")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                s.generationError.value = e.message?.take(100) ?: "未知错误"
                _toast.emit("大纲生成异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    private suspend fun generateChapterTitles(s: BookSession, config: StoryConfig) {
        val bookId = _activeBookId.value
        if (bookId.isBlank()) return
        try {
            val prompt = "请根据以下小说大纲，为每个章节生成一个简洁的标题（4-10字）。\n" +
                "小说：${config.title}\n类型：${config.genre}\n目标章节数：${config.targetChapters}\n\n" +
                "大纲：\n${s.outline.value.take(2000)}\n\n" +
                "请严格按以下格式输出（每行一个章节，不要编号以外的内容）：\n" +
                "第1章：标题\n第2章：标题\n...\n第${config.targetChapters}章：标题"
            val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.5f)
            if (result is AiResult.Success) {
                val titles = parseChapterTitles(result.content, config.targetChapters)
                for ((num, title) in titles) {
                    val existing = repo.getChapter(bookId, num)
                    repo.saveChapter(ChapterEntity(
                        uid = existing?.uid ?: 0, bookId = bookId,
                        chapterNumber = num, title = title, kind = "draft"
                    ))
                }
            }
        } catch (_: Exception) {
            // 标题生成失败不影响大纲生成
        }
    }

    /**
     * 解析 AI 输出的章节标题，支持格式：
     * "第1章：初入江湖" / "1. 初入江湖" / "第一章 初入江湖"
     */
    private fun parseChapterTitles(text: String, maxChapters: Int): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val pattern = Regex("""(?:第\s*)?(\d+)\s*[章\.\s：:]\s*(.+?)(?:\s*$)""", RegexOption.MULTILINE)
        for (match in pattern.findAll(text)) {
            val num = match.groupValues[1].toIntOrNull() ?: continue
            val title = match.groupValues[2].trim().take(30)
            if (num in 1..maxChapters && title.isNotBlank()) {
                result.add(num to title)
            }
        }
        // 如果没解析到，尝试用中文数字
        if (result.isEmpty()) {
            val cnNums = mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10)
            val cnPattern = Regex("""第\s*([一二三四五六七八九十]+)\s*章\s*[：:]?\s*(.+?)(?:\s*$)""", RegexOption.MULTILINE)
            for (match in cnPattern.findAll(text)) {
                val cn = match.groupValues[1]
                val num = cnNums[cn] ?: continue
                val title = match.groupValues[2].trim().take(30)
                if (num in 1..maxChapters && title.isNotBlank()) {
                    result.add(num to title)
                }
            }
        }
        return result
    }

    private fun saveProject() {
        val s = activeSessionOrNull() ?: return
        saveProjectToSession(s)
    }

    private fun saveProjectToSession(s: BookSession) {
        viewModelScope.launch {
            try {
                val project = s.currentProject.value
                val config = s.storyConfig.value
                if (project != null) {
                    repo.updateProject(project.copy(
                        title = config.title, genre = config.genre, briefIdea = config.briefIdea,
                        targetChapters = config.targetChapters, coreSetting = config.coreSetting,
                        characters = config.characters, outline = config.outline,
                        colloquialStyle = config.colloquialStyle, useMemes = config.useMemes,
                        referenceAnalysis = config.referenceAnalysis,
                        causalChainHistory = s.causalChain.value,
                        summaryHistory = s.summaryHistory.value,
                        pendingHooks = s.pendingHooks.value,
                        currentStep = s.currentStep.value.number, updatedAt = System.currentTimeMillis()
                    ))
                }
            } catch (e: Exception) {
                _toast.emit("保存项目失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 章节操作 ----
    fun saveChapter(bookId: String, chapterNum: Int, content: String) {
        viewModelScope.launch {
            try {
                val wordCount = content.replace("\\s+".toRegex(), "").length
                val existing = repo.getChapter(bookId, chapterNum)
                repo.saveChapter(ChapterEntity(uid = existing?.uid ?: 0, bookId = bookId,
                    chapterNumber = chapterNum, content = content, wordCount = wordCount, kind = "draft"))
                _toast.emit("第${chapterNum}章已保存（${wordCount}字）")
            } catch (e: Exception) {
                _toast.emit("保存章节失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun updateCurrentContent(content: String) {
        activeSessionOrNull()?.currentContent?.value = content
    }

    fun aiWriteContent(config: StoryConfig, chapterNum: Int, chapterTitle: String) {
        val s = activeSessionOrNull() ?: return
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.currentChapterNum.value = chapterNum
        val bookId = _activeBookId.value
        s.uiState.value = DramaticaUiState.WritingChapter("ai_write", "加载上下文...", 0.05f)
        s.generationJob = viewModelScope.launch {
            try {
                // ===== 1. 加载跨章节上下文 =====
                s.stepProgress.value = 0.05f; s.stepLoadingMessage.value = "加载前情摘要..."
                val prevChapterContent = if (chapterNum > 1 && bookId.isNotBlank()) {
                    repo.getChapter(bookId, chapterNum - 1)?.content ?: ""
                } else ""
                val summaryHistory = s.summaryHistory.value
                // 构建角色名字清单（防止AI写错名字）
                val characterNames = if (bookId.isNotBlank()) {
                    val chars = repo.getCharacters(bookId).first()
                    chars.map { it.name }.filter { it.isNotBlank() }
                } else emptyList()
                val nameList = if (characterNames.isNotEmpty()) {
                    "【角色名字清单（必须严格使用，禁止写错）】\n" + characterNames.joinToString("、") + "\n\n"
                } else ""
                // 构建真实因果链
                val realCausalChain = if (bookId.isNotBlank()) {
                    val links = repo.getCausalChain(bookId).first()
                    if (links.isNotEmpty()) {
                        "【前文章节因果链】\n" + links.joinToString("\n") { link ->
                            "第${link.chapter}章：因「${link.cause}」→ 事「${link.event}」→ 果「${link.consequence}」${if (link.decision.isNotBlank()) "→ 决「${link.decision}」" else ""}"
                        }.take(2000) + "\n\n"
                    } else ""
                } else ""
                // 构建真实伏笔列表
                val realHooks = if (bookId.isNotBlank()) {
                    val hooks = repo.getHooks(bookId).first().filter { it.status == "open" }
                    if (hooks.isNotEmpty()) {
                        "【待回收伏笔】\n" + hooks.joinToString("\n") { hook ->
                            "• 第${hook.plantedChapter}章埋设：${hook.description}（类型：${hook.type}）${if (hook.resolvedChapter != null) " [已回收]" else " [待回收]"}"
                        }.take(1000) + "\n\n"
                    } else ""
                } else ""

                // ===== 2. 建筑师Agent：独立AI规划章节蓝图 =====
                s.stepProgress.value = 0.1f; s.stepLoadingMessage.value = "建筑师Agent规划中..."
                val writingSkillCtx = getWritingSkillContext()
                val referenceCtx = getReferenceContext(config)
                val architectPrompt = ArchitectAgent.buildBlueprintPrompt(
                    title = config.title,
                    genre = config.genre,
                    chapterNum = chapterNum,
                    chapterTitle = chapterTitle,
                    coreSetting = config.coreSetting,
                    characters = config.characters,
                    summaryHistory = summaryHistory,
                    prevChapterEnding = prevChapterContent,
                    causalChain = realCausalChain,
                    pendingHooks = realHooks,
                    referenceAnalysis = config.referenceAnalysis,
                    writingSkill = writingSkillCtx
                )
                val architectResult = aiRepo.generateContent(architectPrompt, maxTokens = 2048, temperature = 0.3f)
                val blueprint = if (architectResult is AiResult.Success) {
                    ArchitectAgent.parseBlueprint(architectResult.content)
                } else {
                    ArchitectAgent.Blueprint(
                        sceneStructure = "标准四段式：开场→发展→高潮→收尾",
                        keyBeats = "", hookStrategy = "", causalBridge = "",
                        characterSchedule = "", emotionalArc = "", wordBudget = "",
                        rawText = ""
                    )
                }
                // ===== 3. 构建增强 prompt（注入建筑师蓝图） =====
                s.stepProgress.value = 0.15f; s.stepLoadingMessage.value = "AI正在构思章节框架..."
                val basePrompt = buildString {
                    append(writingSkillCtx)
                    append(referenceCtx)
                    append("请为小说「${config.title}」创作第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}的内容。\n\n")
                    append("类型：${config.genre}\n世界观：${config.coreSetting.take(500)}\n角色设定：${config.characters.take(500)}\n\n")
                    // 前情摘要（最重要！优先保留近期章节）
                    if (summaryHistory.isNotBlank()) {
                        append("【前情摘要（必须衔接，严禁跳章）】\n${buildSummaryContext(summaryHistory)}\n\n")
                    }
                    // 上一章结尾（确保衔接）
                    if (prevChapterContent.isNotBlank()) {
                        append("【上一章结尾（必须从此处自然衔接）】\n${prevChapterContent.takeLast(500)}\n\n")
                    }
                    // 角色名字清单
                    append(nameList)
                    // 因果链
                    append(realCausalChain)
                    // 伏笔
                    append(realHooks)
                    // 章节结构
                    append("【章节结构】\n")
                    append("1. 开场（150-300字）：场景切入，用动作或对话开场，不要背景介绍\n")
                    append("2. 发展（800-1000字）：事件推进，对话与叙述交替，每段不超过200字\n")
                    append("3. 高潮（300-400字）：冲突爆发或转折，节奏加快，多用短句\n")
                    append("4. 收尾（150-200字）：留下悬念或情感余韵，为下一章铺垫\n")
                    // 口语化/玩梗风格指令
                    if (config.colloquialStyle) {
                        append("\n【口语化风格】\n")
                        append("1. 用口语化表达，像朋友聊天一样自然，不要书面语\n")
                        append("2. 对话中可使用\"嘛\"\"呗\"\"哈\"\"啦\"\"呀\"等语气词\n")
                        append("3. 适当使用省略句、倒装句、断句，模拟真实说话节奏\n")
                        append("4. 避免成语堆砌和华丽辞藻，用大白话替代\n")
                        append("5. 内心独白用碎碎念风格，不要完整工整的句子\n")
                        append("6. 限制：严肃场景（生离死别、重大抉择）保持克制，不要太过随意\n")
                    }
                    if (config.useMemes) {
                        append("\n【玩梗风格】\n")
                        append("1. 在角色对话中自然地引用网络流行梗、经典影视台词或热梗\n")
                        append("2. 梗要融入剧情，不要突兀插入或强行解释\n")
                        append("3. 限制：严肃场景（战斗高潮、死亡、情感爆发）禁止玩梗\n")
                        append("4. 限制：每章玩梗不超过2次，点到为止\n")
                        append("5. 梗的使用要符合角色性格，不让反派说逗比梗\n")
                    }
                    append("\n【拟人化写作——让读者看不出是AI写的】\n")
                    append("=== 反AI检测层 ===\n")
                    append("1. 句子长度随机化：每段的句子控制在3-5句，其中必含1句极短句（3-7字）+1句中长句（25-40字），避免匀速\n")
                    append("2. 段落节奏：段落长度3-5句话不等，相邻段落长度差至少30字，避免整齐划一\n")
                    append("3. 句式破坏：同段内禁止连续3句使用相同主谓宾结构，主动换主语或换语序\n")
                    append("4. 人类不完美：允许1-2处\"不严谨\"的表达——如角色说半句话被打断、回忆时突然跳转、用错词后自己纠正\n")
                    append("5. 情绪跳跃：同一段内角色情绪可以微波动（如从愤怒→自嘲→无奈），不要一直保持同一种情绪\n")
                    append("=== 真人风格层 ===\n")
                    append("6. 不规则断句：用省略号制造停顿，用破折号制造转折，用分号制造并列，避免句号通篇\n")
                    append("7. 口语化碎碎念：内心独白用不完整短句，像真实心声而非工整作文。如\"妈的。这也能搞砸。\"而非\"他感到非常沮丧，认为自己把事情搞砸了\"\n")
                    append("8. 动作替代心理：用\"他掐灭了烟\"替代\"他感到烦躁\"，用\"拳头攥紧又松开\"替代\"他内心挣扎\"\n")
                    append("9. 感官锚点：环境描写必须有\"人\"的感知——热、冷、吵、静、臭、香，通过角色感官进入，而非上帝视角描述\n")
                    append("10. 对话要有\"废话\"：真实对话中插入1-2句无意义但真实的回应（\"嗯\"\"行了行了\"\"怎么又是你\"），让对话更像真人\n")
                    append("=== 风格参考 ===\n")
                    append("11. 金庸式：对话推动剧情，招式描写有画面感，人物有侠气，善恶分明但有灰色地带\n")
                    append("12. 余华式：冷静克制，用平常语言写残酷现实，长句铺陈后突然短句收尾，制造冲击力\n")
                    append("13. 村上式：孤独感贯穿，细节描写音乐/食物/天气，第一人称内心独白夹杂隐喻\n")
                    append("14. 根据小说类型自动选择风格基调：玄幻偏金庸式，现实偏余华式，都市偏村上式\n")
                    append("\n【写作铁律】\n")
                    append("1. 总字数约2000字，不少于1500字\n")
                    append("2. 每300字至少包含一次对话——用对话推进剧情，不要纯叙述\n")
                    append("3. 展示而非讲述：用角色的动作、表情、对话来传递信息\n")
                    append("4. 感官描写：至少包含2种感官（视觉+听觉/触觉/嗅觉）\n")
                    append("5. 短句优先：对话3-15字，叙述15-30字，偶尔用长句制造节奏变化\n")
                    append("6. 禁止AI套话：仿佛、忽然、竟然、不禁、宛如、猛地、顿时、霎时\n")
                    append("7. 禁止上帝视角评论：不要总结人物心理，不要评价事件对错\n")
                    append("8. 禁止集体反应：全场震惊、众人哗然、所有人都……\n")
                    append("9. 禁止报告式语言：分析了……、从……角度、综合考虑\n")
                    append("10. 角色名字必须与角色清单一致，严禁写错或自创名字\n")
                    append("11. 直接输出正文，不要标题、不要章节号、不要解释、不要括号备注\n")
                }
                // 注入建筑师蓝图到写手 prompt
                val prompt = ArchitectAgent.injectBlueprintIntoPrompt(basePrompt, blueprint)
                kotlinx.coroutines.delay(200)
                s.stepProgress.value = 0.3f; s.stepLoadingMessage.value = "AI正在撰写正文..."
                val result = aiRepo.generateContent(prompt, maxTokens = 8192, temperature = 0.7f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.7f; s.stepLoadingMessage.value = "MiMo自动优化中..."
                        var content = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                        // 自动验证并修订
                        val validation = PostWriteValidator.validateForMimo(content)
                        if (validation.issues.isNotEmpty()) {
                            s.stepProgress.value = 0.8f; s.stepLoadingMessage.value = "自动修订中..."
                            content = if (isMimoModel()) autoReviseForMimo(content, validation) else content
                        }
                        // 叙事审计 + 自动修订闭环（最多2轮）
                        s.stepProgress.value = 0.85f; s.stepLoadingMessage.value = "叙事审计中..."
                        var auditResult = narrativeAudit(content, config, chapterNum, chapterTitle)
                        var revisionRound = 0
                        while (revisionRound < 2 && auditResult.issues.any { it.severity == "critical" }) {
                            revisionRound++
                            s.stepProgress.value = 0.85f + 0.05f * revisionRound
                            s.stepLoadingMessage.value = "叙事修订中（第${revisionRound}轮）..."
                            val criticalIssues = auditResult.issues.filter { it.severity == "critical" }
                            val revisionPrompt = buildString {
                                append("请根据以下审计意见修订章节内容。\n\n")
                                append("小说：${config.title}\n类型：${config.genre}\n")
                                append("章节：第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n\n")
                                append("【审计问题】（必须修正）：\n")
                                criticalIssues.forEach { issue ->
                                    append("- [${issue.dimension}] ${issue.description}\n")
                                    if (issue.suggestion.isNotBlank()) append("  建议：${issue.suggestion}\n")
                                }
                                append("\n【修订要求】\n")
                                append("1. 只在有问题的地方修改，不要重写整章\n")
                                append("2. 保持原有风格和节奏\n")
                                append("3. 直接输出修订后的完整章节内容\n")
                                append("\n【原始内容】\n${content.take(3000)}")
                            }
                            val revisionResult = aiRepo.generateContent(revisionPrompt, maxTokens = 8192, temperature = 0.3f)
                            if (revisionResult is AiResult.Success) {
                                content = revisionResult.content
                                // 重新审计
                                auditResult = narrativeAudit(content, config, chapterNum, chapterTitle)
                            } else {
                                break // 修订失败，停止循环
                            }
                        }
                        // 编辑整改（拟人化润色）
                        s.stepProgress.value = 0.92f; s.stepLoadingMessage.value = "编辑整改中（拟人化润色）..."
                        val rectifyPrompt = ContentRectifier.buildRectifyPrompt(
                            content, config.title, config.genre, chapterNum, chapterTitle,
                            config.colloquialStyle, config.useMemes
                        )
                        val rectifyResult = aiRepo.generateContent(rectifyPrompt, maxTokens = 8192, temperature = 0.4f)
                        if (rectifyResult is AiResult.Success) {
                            val rectified = rectifyResult.content
                            // 检查整改后的内容是否有效（不能太短）
                            if (rectified.length >= content.length * 0.6) {
                                content = rectified
                            }
                        }
                        // 再次验证
                        s.stepProgress.value = 0.95f; s.stepLoadingMessage.value = "最终验证..."
                        content = if (isMimoModel()) postProcessForMimo(content) else content
                        val finalValidation = PostWriteValidator.validateForMimo(content)
                        if (finalValidation.issues.isNotEmpty()) {
                            content = if (isMimoModel()) autoReviseForMimo(content, finalValidation) else content
                        }
                        // 审计结果提示
                        val criticalCount = auditResult.issues.count { it.severity == "critical" }
                        val warningCount = auditResult.issues.count { it.severity == "warning" }
                        if (revisionRound > 0) {
                            _toast.emit("叙事审计：${auditResult.summary}（${revisionRound}轮修订，剩余${criticalCount}严重/${warningCount}警告）")
                        }
                        s.currentContent.value = content
                        if (bookId.isNotBlank()) {
                            val wordCount = content.replace("\\s+".toRegex(), "").length
                            val existing = repo.getChapter(bookId, chapterNum)
                            repo.saveChapter(ChapterEntity(uid = existing?.uid ?: 0, bookId = bookId,
                                chapterNumber = chapterNum, content = content, wordCount = wordCount, title = chapterTitle, kind = "draft"))
                            // 后处理操作：每个独立 try-catch，单个失败不影响章节保存和其他元数据提取
                            s.stepProgress.value = 0.9f; s.stepLoadingMessage.value = "提取时间线事件..."
                            try { extractAndSaveTimelineEvents(bookId, chapterNum, content) } catch (_: Exception) {}
                            s.stepProgress.value = 0.92f; s.stepLoadingMessage.value = "提取因果链..."
                            try { extractCausalChain(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.94f; s.stepLoadingMessage.value = "提取伏笔..."
                            try { extractHooks(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.95f; s.stepLoadingMessage.value = "提取情感..."
                            try { extractEmotions(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.955f; s.stepLoadingMessage.value = "更新关系网络..."
                            try { extractRelationships(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.97f; s.stepLoadingMessage.value = "更新信息边界..."
                            try { extractInfoBoundary(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.98f; s.stepLoadingMessage.value = "标注戏剧节拍..."
                            try { extractNarrativeBeats(bookId, chapterNum, content, config) } catch (_: Exception) {}
                            s.stepProgress.value = 0.99f; s.stepLoadingMessage.value = "生成章节摘要..."
                            try { generateChapterSummary(bookId, chapterNum, chapterTitle, content, config) } catch (_: Exception) {}
                        }
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        _toast.emit("AI创作完成（${content.length}字，已自动优化）")
                        saveProjectToSession(s)
                    }
                    is AiResult.Error -> { _toast.emit("创作失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("创作异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun polishContent(config: StoryConfig) {
        val s = activeSessionOrNull() ?: return
        val content = s.currentContent.value
        if (content.isBlank()) { viewModelScope.launch { _toast.emit("请先输入内容") }; return }
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("polish", "AI正在润色...", 0.3f)
        s.stepProgress.value = 0.3f; s.stepLoadingMessage.value = "AI正在润色..."
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = buildString {
                    append("请润色以下小说片段，保持原意和情节不变：\n")
                    append("1. 删除AI套话（仿佛、忽然、竟然、不禁、宛如、猛地、顿时）\n")
                    append("2. 减少形容词堆砌，每句不超过2个\u201C的\u201D字修饰\n")
                    append("3. 将超过50字的长句拆分为短句\n")
                    append("4. 将纯叙述段落改为对话+叙述交替\n")
                    append("5. 删除\u201C他心想/她觉得/他感到\u201D等报告式心理描写，改为动作暗示\n")
                    if (config.colloquialStyle) {
                        append("6. 口语化增强：将书面语改为大白话，添加语气词（嘛、呗、哈、啦），让对话更像真人聊天\n")
                        append("7. 碎碎念式独白：内心戏用不完整的短句，像真实的心声而非工整作文\n")
                    }
                    if (config.useMemes) {
                        append("8. 玩梗增强：在合适的对话中自然融入网络流行梗或经典台词，但不超过2处\n")
                        append("9. 玩梗要自然，融入语境，不要括号解释或强行插入\n")
                    }
                    append("\n原文：\n$content")
                }
                val result = aiRepo.generateContent(prompt, maxTokens = 8192, temperature = 0.5f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.9f; s.stepLoadingMessage.value = "MiMo自动优化中..."
                        val polished = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                        s.currentContent.value = polished
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        _toast.emit("润色完成（${polished.length}字，已自动优化）")
                    }
                    is AiResult.Error -> { _toast.emit("润色失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("润色异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun continueWriting(config: StoryConfig) {
        val s = activeSessionOrNull() ?: return
        val content = s.currentContent.value
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("continue", "AI正在续写...", 0.3f)
        s.stepProgress.value = 0.3f; s.stepLoadingMessage.value = "AI正在续写..."
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = "请继续创作以下小说，保持风格、人物性格和叙事节奏一致：\n" +
                    "前文末尾：\n${content.takeLast(1000)}\n\n" +
                    "要求：继续推进剧情，保持对话和动作的节奏。约1000字，直接输出正文。"
                val result = aiRepo.generateContent(prompt, maxTokens = 4096, temperature = 0.7f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.9f; s.stepLoadingMessage.value = "MiMo自动优化中..."
                        val polished = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                        s.currentContent.value = content + "\n\n" + polished
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        _toast.emit("续写完成（+${polished.length}字，已自动优化）")
                    }
                    is AiResult.Error -> { _toast.emit("续写失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("续写异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun reviseContent(config: StoryConfig) {
        val s = activeSessionOrNull() ?: return
        val content = s.currentContent.value
        if (content.isBlank()) { viewModelScope.launch { _toast.emit("请先输入内容") }; return }
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("revise", "AI正在修订...", 0.3f)
        s.stepProgress.value = 0.3f; s.stepLoadingMessage.value = "AI正在修订..."
        s.generationJob = viewModelScope.launch {
            try {
                val validation = PostWriteValidator.validateForMimo(content)
                val issuesText = validation.issues.joinToString("\n") { "- ${it.description}" }
                val prompt = "请修订以下小说片段，逐一解决列出的问题，保持原意和情节不变：\n$issuesText\n\n" +
                    "原文：\n$content\n\n直接输出修订后的完整正文："
                val result = aiRepo.generateContent(prompt, maxTokens = 8192, temperature = 0.5f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.9f; s.stepLoadingMessage.value = "MiMo自动优化中..."
                        val revised = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                        s.currentContent.value = revised
                        s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                        _toast.emit("修订完成（${revised.length}字，已自动优化）")
                    }
                    is AiResult.Error -> { _toast.emit("修订失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("修订异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    /**
     * 叙事审计 Agent：使用 AI 对章节内容进行叙事质量审计。
     * 检查因果一致性、角色 OOC、伏笔遗漏、节奏问题等。
     * @return AuditResult 包含问题列表和严重程度
     */
    data class AuditResult(
        val passed: Boolean,
        val issues: List<AuditIssue>,
        val summary: String
    )
    data class AuditIssue(
        val dimension: String,    // causal_consistency / character_ooc / hook_missing / pacing / info_boundary
        val severity: String,     // critical / warning / info
        val description: String,
        val suggestion: String = ""
    )

    private suspend fun narrativeAudit(
        content: String, config: StoryConfig, chapterNum: Int, chapterTitle: String
    ): AuditResult {
        try {
            val prompt = buildString {
                append("你是一位资深小说编辑，请对以下章节进行叙事质量审计。\n\n")
                append("小说：${config.title}\n类型：${config.genre}\n")
                append("章节：第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n\n")
                append("【审计维度】\n")
                append("1. causal_consistency — 因果一致性：事件是否有前因后果？是否与已有因果链矛盾？\n")
                append("2. character_ooc — 角色一致性：角色行为是否与设定一致？是否有OOC（性格崩塌）？\n")
                append("3. hook_missing — 伏笔遗漏：是否应该埋设新伏笔？已有伏笔是否有推进？\n")
                append("4. pacing — 节奏问题：是否拖沓或跳跃？对话与叙述比例是否合理？\n")
                append("5. info_boundary — 信息边界：角色是否知道了不该知道的信息？\n\n")
                append("【章节内容】\n${content.take(3000)}\n\n")
                append("严格按以下JSON格式输出（不要输出其他内容）：\n")
                append("""{"passed": true/false, "issues": [{"dimension": "...", "severity": "critical/warning/info", "description": "...", "suggestion": "..."}], "summary": "一句话总结"}""")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 1024, temperature = 0.0f)
            if (result is AiResult.Success) {
                // 解析 JSON 响应
                val json = result.content.trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                return try {
                    val obj = org.json.JSONObject(json)
                    val issuesList = mutableListOf<AuditIssue>()
                    val issuesArr = obj.optJSONArray("issues") ?: org.json.JSONArray()
                    for (i in 0 until issuesArr.length()) {
                        val issue = issuesArr.getJSONObject(i)
                        issuesList.add(AuditIssue(
                            dimension = issue.optString("dimension", "unknown"),
                            severity = issue.optString("severity", "info"),
                            description = issue.optString("description", ""),
                            suggestion = issue.optString("suggestion", "")
                        ))
                    }
                    AuditResult(
                        passed = obj.optBoolean("passed", true),
                        issues = issuesList,
                        summary = obj.optString("summary", "审计完成")
                    )
                } catch (_: Exception) {
                    AuditResult(passed = true, issues = emptyList(), summary = "审计解析失败，跳过")
                }
            }
        } catch (_: Exception) {
            // 审计失败不影响写作
        }
        return AuditResult(passed = true, issues = emptyList(), summary = "审计跳过")
    }

    /**
     * 生成章节摘要并累加到真相文件（summaryHistory）。
     * 每章写完后调用，为后续章节提供上下文。
     */
    private suspend fun generateChapterSummary(
        bookId: String, chapterNum: Int, chapterTitle: String, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请为以下章节生成一个简洁的摘要（100-150字），包含：\n")
                append("1. 本章核心事件\n2. 角色状态变化\n3. 新出现的伏笔\n4. 结尾悬念\n\n")
                append("第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n")
                append("${content.take(2000)}\n\n")
                append("请直接输出摘要，不要加任何前缀或标签。")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 256, temperature = 0.3f)
            if (result is AiResult.Success) {
                val summary = result.content.trim().take(200)
                val s = _sessions.value[bookId] ?: return
                val history = s.summaryHistory.value
                val entry = "第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}：${summary}\n"
                s.summaryHistory.value = if (history.isBlank()) "【前情摘要】\n$entry" else history + entry
                saveProject()
                // 超长篇小说（100万字+）：摘要历史超过阈值时触发渐进压缩
                if (s.summaryHistory.value.length > 15000) {
                    compressSummaryHistory(bookId, s)
                }
            }
        } catch (_: Exception) {
            // 摘要生成失败不影响主流程
        }
    }

    /**
     * 渐进压缩摘要历史，用于 100 万字+ 长篇小说。
     * 将旧章节摘要压缩为密集的"故事脉络"，保留近期章节的详细摘要。
     * 策略：保留后 5000 字的近期详细摘要，将前面的内容压缩为全局概要。
     */
    private suspend fun compressSummaryHistory(bookId: String, s: BookSession) {
        try {
            val fullHistory = s.summaryHistory.value
            if (fullHistory.length <= 15000) return

            val recentPart = fullHistory.takeLast(5000)
            val oldPart = fullHistory.dropLast(5000)

            val compressPrompt = buildString {
                append("请将以下小说的章节摘要压缩为一份密集的「全局故事脉络」（800-1200字），保留：\n")
                append("1. 主线剧情的关键转折点\n2. 主要角色的重大变化（死亡、背叛、觉醒等）\n")
                append("3. 已揭示的核心秘密和世界观\n4. 尚未解决的主要冲突\n\n")
                append("=== 待压缩的章节摘要 ===\n")
                append(oldPart.take(20000))
                append("\n\n请直接输出压缩后的「全局故事脉络」，不要加任何前缀。")
            }
            val result = aiRepo.generateContent(compressPrompt, maxTokens = 2048, temperature = 0.3f)
            if (result is AiResult.Success) {
                val compressed = result.content.trim().take(2000)
                s.summaryHistory.value = "【全局故事脉络】\n${compressed}\n\n【近期章节摘要】\n${recentPart.trimStart()}"
                saveProject()
            }
        } catch (_: Exception) {
            // 压缩失败不影响主流程，下次写章节时会重试
        }
    }

    /**
     * 从章节内容中提取真实因果链。
     * 替代原来的占位符"前文事件→核心事件→推动后续剧情"。
     */
    private suspend fun extractCausalChain(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请从以下章节中提取2-3条因果链，每条格式：\n")
                append("因（是什么引发了事件）→ 事（核心事件是什么）→ 果（事件导致了什么结果）\n\n")
                append("第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("请直接输出因果链，每条一行，不要加任何前缀或标签。")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 512, temperature = 0.3f)
            if (result is AiResult.Success) {
                val chains = result.content.trim().lines().filter { it.isNotBlank() && it.contains("→") }
                for (chain in chains.take(3)) {
                    val parts = chain.split("→").map { it.trim() }
                    if (parts.size >= 3) {
                        repo.insertCausalLink(CausalLinkEntity(
                            bookId = bookId, chapter = chapterNum,
                            cause = parts[0].take(100), event = parts[1].take(100),
                            consequence = parts[2].take(100),
                            decision = if (parts.size >= 4) parts[3].take(100) else ""
                        ))
                    }
                }
                // 更新因果链历史
                val s = _sessions.value[bookId] ?: return
                val current = s.causalChain.value
                s.causalChain.value = if (current.isBlank()) chains.joinToString("\n") else "$current\n${chains.joinToString("\n")}"
                saveProject()
            }
        } catch (_: Exception) {
            // 因果链提取失败不影响主流程
        }
    }

    /**
     * 根据角色列表自动生成角色关系网络。
     * 主角与每个角色建立关系，角色之间也建立基本关系。
     */
    private suspend fun autoGenerateRelationships(bookId: String, characters: List<CharacterEntity>) {
        try {
            val protagonist = characters.find { it.type == "protagonist" } ?: characters.first()
            val others = characters.filter { it.name != protagonist.name }
            for (other in others) {
                val strength = when (other.type) {
                    "antagonist" -> -80
                    "sidekick" -> 80
                    "guardian" -> 60
                    "impact" -> 40
                    "contagonist" -> -30
                    "skeptic" -> -10
                    else -> 30
                }
                repo.insertRelationship(RelationshipEntity(
                    bookId = bookId, characterA = protagonist.name,
                    characterB = other.name, type = other.type, strength = strength
                ))
            }
            // 伙伴和反派之间也建立关系
            val sidekick = characters.find { it.type == "sidekick" }
            val antagonist = characters.find { it.type == "antagonist" }
            if (sidekick != null && antagonist != null && sidekick.name != antagonist.name) {
                repo.insertRelationship(RelationshipEntity(
                    bookId = bookId, characterA = sidekick.name,
                    characterB = antagonist.name, type = "对立", strength = -60
                ))
            }
        } catch (_: Exception) {
            // 关系生成失败不影响主流程
        }
    }

    /**
     * 从章节内容中提取新伏笔。
     */
    private suspend fun extractHooks(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请从以下章节中找出1-2个新埋设的伏笔（如果有的话）。\n")
                append("伏笔类型：foreshadow（悬疑伏笔）、promise（承诺伏笔）、mystery（神秘伏笔）、conflict（冲突伏笔）\n\n")
                append("第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("请直接输出，每条一行，格式：类型|描述\n")
                append("如果没有新伏笔，输出\"无\"。")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 256, temperature = 0.3f)
            if (result is AiResult.Success) {
                val lines = result.content.trim().lines().filter { it.isNotBlank() && it != "无" && it.contains("|") }
                for (line in lines.take(2)) {
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 2) {
                        val type = parts[0].take(20)
                        val desc = parts[1].take(200)
                        repo.insertHook(HookEntity(
                            id = UUID.randomUUID().toString().take(8),
                            bookId = bookId, description = desc,
                            plantedChapter = chapterNum, type = type
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            // 伏笔提取失败不影响主流程
        }
    }

    /**
     * 从章节内容中提取角色情感状态。
     */
    private suspend fun extractEmotions(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请从以下章节中分析每个角色的情感状态。\n")
                append("角色设定：${config.characters.take(300)}\n\n")
                append("第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("请直接输出，每行一个角色，格式：角色名|情感标签|强度(1-10)\n")
                append("情感标签示例：愤怒、喜悦、悲伤、恐惧、希望、紧张、释然、困惑、坚定、失望")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 256, temperature = 0.3f)
            if (result is AiResult.Success) {
                val lines = result.content.trim().lines().filter { it.isNotBlank() && it.contains("|") }
                for (line in lines.take(3)) {
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 3) {
                        val charName = parts[0].take(20)
                        val emotion = parts[1].take(20)
                        val intensity = parts[2].toIntOrNull() ?: 5
                        repo.insertEmotion(EmotionEntity(
                            bookId = bookId, characterId = charName,
                            emotion = emotion, intensity = intensity.coerceIn(1, 10),
                            chapter = chapterNum
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            // 情感提取失败不影响主流程
        }
    }

    private suspend fun extractRelationships(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val existingRelations = repo.getRelationships(bookId)
            val existingSnapshot = existingRelations.firstOrNull() ?: emptyList()
            val existingMap = existingSnapshot.associateBy { "${it.characterA}|${it.characterB}" }

            val prompt = buildString {
                append("请分析以下章节中角色之间的关系变化，用数值表示关系强度（-100=极度敌对，0=中立，100=极度亲密）。\n")
                append("角色设定：${config.characters.take(500)}\n\n")
                append("已有关系状态：\n")
                existingSnapshot.forEach { rel ->
                    append("- ${rel.characterA} ↔ ${rel.characterB}: ${rel.strength} (${rel.type})\n")
                }
                append("\n第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("请输出每对角色关系的变化，格式：角色A|角色B|新强度(-100~100)|关系类型|变化原因\n")
                append("关系类型：enemy/neutral/ally/friend/lover/family/rival\n")
                append("如果关系没有变化，不需要输出该对")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 512, temperature = 0.3f)
            if (result is AiResult.Success) {
                val lines = result.content.trim().lines().filter { it.isNotBlank() && it.contains("|") }
                for (line in lines.take(5)) {
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 4) {
                        val charA = parts[0].take(20)
                        val charB = parts[1].take(20)
                        val strength = parts[2].toIntOrNull()?.coerceIn(-100, 100) ?: continue
                        val type = parts[3].take(20)
                        val reason = parts.getOrElse(4) { "" }.take(100)
                        val key = "$charA|$charB"
                        val existing = existingMap[key] ?: existingMap["$charB|$charA"]
                        if (existing != null) {
                            repo.updateRelationshipStrength(existing.uid, strength, reason)
                        } else {
                            repo.insertRelationship(RelationshipEntity(
                                bookId = bookId, characterA = charA, characterB = charB,
                                type = type, strength = strength, reason = reason
                            ))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 关系提取失败不影响主流程
        }
    }

    private suspend fun extractInfoBoundary(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请分析以下章节中，每个角色知道了什么新信息。\n")
                append("角色设定：${config.characters.take(500)}\n\n")
                append("第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("请输出每行一条，格式：角色名|信息关键词|信息内容|获取方式\n")
                append("获取方式：witnessed(目睹)/hearsay(听说)/deduced(推断)/document(文书)\n")
                append("只输出角色在本章中首次知道的信息")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 512, temperature = 0.3f)
            if (result is AiResult.Success) {
                val lines = result.content.trim().lines().filter { it.isNotBlank() && it.contains("|") }
                for (line in lines.take(10)) {
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 4) {
                        val charId = parts[0].take(20)
                        val infoKey = parts[1].take(30)
                        val infoContent = parts[2].take(200)
                        val source = parts[3].take(20)
                        repo.insertKnownInfo(KnownInfoEntity(
                            bookId = bookId, characterId = charId,
                            infoKey = infoKey, content = infoContent,
                            learnedInChapter = chapterNum, source = source
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            // 信息边界提取失败不影响主流程
        }
    }

    private suspend fun extractNarrativeBeats(
        bookId: String, chapterNum: Int, content: String, config: StoryConfig
    ) {
        try {
            val prompt = buildString {
                append("请分析以下章节的关键戏剧节拍（Dramatica理论）。\n")
                append("小说类型：${config.genre}\n\n")
                append("第${chapterNum}章：\n${content.take(2000)}\n\n")
                append("可选节拍类型：setup(建立)、inciting_incident(激励事件)、turning_point(转折点)、midpoint(中点)、crisis(危机)、climax(高潮)、revelation(揭示)、decision(决策)、consequence(后果)、transition(过渡)\n")
                append("请输出本章出现的节拍，格式：节拍类型|描述|涉及角色|情感目标\n")
                append("一章通常有1-3个节拍，不要过度标注")
            }
            val result = aiRepo.generateContent(prompt, maxTokens = 256, temperature = 0.3f)
            if (result is AiResult.Success) {
                val lines = result.content.trim().lines().filter { it.isNotBlank() && it.contains("|") }
                for (line in lines.take(3)) {
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 2) {
                        val beatType = parts[0].take(30)
                        val description = parts.getOrElse(1) { "" }.take(200)
                        val characterId = parts.getOrElse(2) { "" }.take(20)
                        val emotionalTarget = parts.getOrElse(3) { "" }.take(50)
                        repo.insertChapterBeat(ChapterBeatEntity(
                            bookId = bookId, chapter = chapterNum,
                            beatType = beatType, description = description,
                            characterId = characterId, emotionalTarget = emotionalTarget
                        ))
                    }
                }
            }
        } catch (_: Exception) {
            // 节拍提取失败不影响主流程
        }
    }

    // ===== Step6 人工+AI 二次修改 =====

    /**
     * 选中文字重写：用户选中一段文字，AI仅重写该部分
     */
    fun rewriteSelectedContent(config: StoryConfig, selectedText: String, instruction: String = "") {
        val s = activeSessionOrNull() ?: return
        val content = s.currentContent.value
        if (selectedText.isBlank() || content.isBlank()) {
            viewModelScope.launch { _toast.emit("请先选中要重写的文字") }
            return
        }
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("rewrite_selected", "AI正在重写选中文字...", 0.3f)
        s.generationJob = viewModelScope.launch {
            try {
                // 找到选中文字在全文中的位置，提取上下文
                val idx = content.indexOf(selectedText)
                val contextStart = (idx - 200).coerceAtLeast(0)
                val contextEnd = (idx + selectedText.length + 200).coerceAtMost(content.length)
                val fullContext = content.substring(contextStart, contextEnd)

                val prompt = ContentRectifier.buildSelectedRewritePrompt(selectedText, fullContext, instruction)
                s.stepProgress.value = 0.5f; s.stepLoadingMessage.value = "AI正在重写..."
                val result = aiRepo.generateContent(prompt, maxTokens = 4096, temperature = 0.6f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.9f; s.stepLoadingMessage.value = "完成"
                        val rewritten = result.content.trim()
                        // 替换选中文字
                        if (idx >= 0 && rewritten.isNotBlank() && rewritten.length < selectedText.length * 3) {
                            s.currentContent.value = content.replaceRange(idx, idx + selectedText.length, rewritten)
                            _toast.emit("重写完成")
                        } else {
                            _toast.emit("重写结果异常，请手动调整")
                        }
                    }
                    is AiResult.Error -> { _toast.emit("重写失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("重写异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    /**
     * 按用户指令修改：用户在输入框中输入指令，AI根据指令修改全文
     */
    fun aiRewriteWithInstruction(config: StoryConfig, instruction: String) {
        val s = activeSessionOrNull() ?: return
        val content = s.currentContent.value
        if (content.isBlank()) { viewModelScope.launch { _toast.emit("请先输入内容") }; return }
        if (instruction.isBlank()) { viewModelScope.launch { _toast.emit("请输入修改指令") }; return }
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("rewrite_instruct", "AI正在根据指令修改...", 0.3f)
        s.generationJob = viewModelScope.launch {
            try {
                val prompt = buildString {
                    append("你是一位小说编辑，请根据用户指令修改以下章节内容。\n\n")
                    append("小说：《${config.title}》 类型：${config.genre}\n")
                    append("用户指令：$instruction\n\n")
                    append("【修改原则】\n")
                    append("1. 只修改与指令相关的部分，不要改动其他内容\n")
                    append("2. 保持原文风格和节奏\n")
                    append("3. 用拟人化写作方式：短句、动作描写、对话推进\n")
                    append("4. 禁止AI套话：仿佛、忽然、竟然、不禁、宛如、猛地、顿时、霎时\n")
                    append("5. 直接输出修改后的完整章节内容，不要加任何解释\n\n")
                    append("【原文】\n${content.take(3000)}")
                }
                s.stepProgress.value = 0.5f; s.stepLoadingMessage.value = "AI正在修改..."
                val result = aiRepo.generateContent(prompt, maxTokens = 8192, temperature = 0.5f)
                when (result) {
                    is AiResult.Success -> {
                        s.stepProgress.value = 0.8f; s.stepLoadingMessage.value = "MiMo自动优化中..."
                        val modified = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                        if (modified.length >= content.length * 0.6) {
                            s.currentContent.value = modified
                            s.stepProgress.value = 1f; s.stepLoadingMessage.value = "完成"
                            _toast.emit("按指令修改完成（${modified.length}字）")
                        } else {
                            _toast.emit("修改结果过短，请重新尝试")
                        }
                    }
                    is AiResult.Error -> { _toast.emit("修改失败：${result.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("修改异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    /**
     * 批量连续写作：一次写多章，章间自动注入上下文
     */
    fun aiBatchWrite(config: StoryConfig, count: Int = 3) {
        val s = activeSessionOrNull() ?: return
        val bookId = _activeBookId.value
        if (s.isGenerating.value) { viewModelScope.launch { _toast.emit("AI正在生成中，请等待...") }; return }
        val startChapter = s.currentChapterNum.value
        if (count < 1 || count > 5) {
            viewModelScope.launch { _toast.emit("批量写作章节数必须在1-5之间") }
            return
        }
        s.isGenerating.value = true
        s.uiState.value = DramaticaUiState.WritingChapter("batch", "开始批量写作...", 0f)
        s.generationJob = viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            try {
                for (i in 0 until count) {
                    val chapterNum = startChapter + i
                    val chapterTitle = chapters.value.getOrNull(chapterNum - 1)?.title ?: ""
                    val chapterBase = i.toFloat() / count
                    val chapterRange = 1f / count
                    fun updateProgress(ratio: Float, msg: String) {
                        s.stepProgress.value = chapterBase + ratio * chapterRange
                        s.stepLoadingMessage.value = "${msg}（第${chapterNum}章 ${i + 1}/$count）"
                    }
                    updateProgress(0f, "加载上下文")

                    // 构建上下文（使用正式摘要，与单章写作一致）
                    val prevContent = repo.getChapter(bookId, chapterNum - 1)?.content ?: ""
                    val summaryHistory = s.summaryHistory.value

                    val nameList = buildString {
                        try {
                            val chars = repo.getCharacters(bookId)
                            val snapshot = chars.firstOrNull() ?: emptyList()
                            if (snapshot.isNotEmpty()) {
                                append("【角色名字清单，严禁写错】\n")
                                snapshot.forEach { c -> append("- ${c.name}（${c.role}）：${c.tags.take(80)}\n") }
                                append("\n")
                            }
                        } catch (_: Exception) {}
                    }

                    // ===== 建筑师Agent：独立AI规划 =====
                    updateProgress(0.05f, "建筑师Agent规划中")
                    val writingSkillCtx = getWritingSkillContext()
                    val referenceCtx = getReferenceContext(config)
                    val architectPrompt = ArchitectAgent.buildBlueprintPrompt(
                        title = config.title, genre = config.genre,
                        chapterNum = chapterNum, chapterTitle = chapterTitle,
                        coreSetting = config.coreSetting, characters = config.characters,
                        summaryHistory = summaryHistory, prevChapterEnding = prevContent,
                        causalChain = "", pendingHooks = "",
                        referenceAnalysis = config.referenceAnalysis,
                        writingSkill = writingSkillCtx
                    )
                    val architectResult = aiRepo.generateContent(architectPrompt, maxTokens = 2048, temperature = 0.3f)
                    val blueprint = if (architectResult is AiResult.Success) {
                        ArchitectAgent.parseBlueprint(architectResult.content)
                    } else {
                        ArchitectAgent.Blueprint(
                            sceneStructure = "标准四段式：开场→发展→高潮→收尾",
                            keyBeats = "", hookStrategy = "", causalBridge = "",
                            characterSchedule = "", emotionalArc = "", wordBudget = "",
                            rawText = ""
                        )
                    }

                    val basePrompt = buildString {
                        append(writingSkillCtx)
                        append(referenceCtx)
                        append("请为小说「${config.title}」创作第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}的内容。\n\n")
                        append("类型：${config.genre}\n世界观：${config.coreSetting.take(300)}\n角色设定：${config.characters.take(300)}\n\n")
                        if (summaryHistory.isNotBlank()) {
                            append("【前情摘要】\n${buildSummaryContext(summaryHistory)}\n\n")
                        }
                        if (prevContent.isNotBlank()) {
                            append("【上一章结尾】\n${prevContent.takeLast(500)}\n\n")
                        }
                        append(nameList)
                        append("【写作要求】\n")
                        append("1. 总字数约2000字，用对话推进剧情\n")
                        append("2. 展示而非讲述，用动作和感官描写\n")
                        append("3. 禁止AI套话：仿佛、忽然、竟然、不禁、宛如、猛地、顿时\n")
                        append("4. 直接输出正文，不要标题、章节号、解释\n")
                        if (config.colloquialStyle) {
                            append("5. 口语化风格：对话中用大白话，加入语气词\n")
                        }
                    }
                    // 注入建筑师蓝图
                    val prompt = ArchitectAgent.injectBlueprintIntoPrompt(basePrompt, blueprint)

                    updateProgress(0.15f, "AI正在撰写正文")
                    val result = aiRepo.generateContent(prompt, maxTokens = 8192, temperature = 0.7f)
                    when (result) {
                        is AiResult.Success -> {
                            var content = if (isMimoModel()) postProcessForMimo(result.content) else result.content
                            // 自动验证并修订
                            updateProgress(0.25f, "写后验证中")
                            val validation = PostWriteValidator.validateForMimo(content)
                            if (validation.issues.isNotEmpty()) {
                                updateProgress(0.28f, "自动修订中")
                                content = if (isMimoModel()) autoReviseForMimo(content, validation) else content
                            }
                            // 叙事审计 + 自动修订闭环（最多2轮）
                            updateProgress(0.35f, "叙事审计中")
                            var auditResult = narrativeAudit(content, config, chapterNum, chapterTitle)
                            var revisionRound = 0
                            while (revisionRound < 2 && auditResult.issues.any { it.severity == "critical" }) {
                                revisionRound++
                                updateProgress(0.35f + 0.05f * revisionRound, "叙事修订中（第${revisionRound}轮）")
                                val criticalIssues = auditResult.issues.filter { it.severity == "critical" }
                                val revisionPrompt = buildString {
                                    append("请根据以下审计意见修订章节内容。\n\n")
                                    append("小说：${config.title}\n类型：${config.genre}\n")
                                    append("章节：第${chapterNum}章${if (chapterTitle.isNotBlank()) "「$chapterTitle」" else ""}\n\n")
                                    append("【审计问题】（必须修正）：\n")
                                    criticalIssues.forEach { issue ->
                                        append("- [${issue.dimension}] ${issue.description}\n")
                                        if (issue.suggestion.isNotBlank()) append("  建议：${issue.suggestion}\n")
                                    }
                                    append("\n【修订要求】\n")
                                    append("1. 只在有问题的地方修改，不要重写整章\n")
                                    append("2. 保持原有风格和节奏\n")
                                    append("3. 直接输出修订后的完整章节内容\n")
                                    append("\n【原始内容】\n${content.take(3000)}")
                                }
                                val revisionResult = aiRepo.generateContent(revisionPrompt, maxTokens = 8192, temperature = 0.3f)
                                if (revisionResult is AiResult.Success) {
                                    content = revisionResult.content
                                    auditResult = narrativeAudit(content, config, chapterNum, chapterTitle)
                                } else {
                                    break
                                }
                            }
                            // 编辑整改（拟人化润色）
                            updateProgress(0.55f, "编辑整改中")
                            val rectifyPrompt = ContentRectifier.buildRectifyPrompt(
                                content, config.title, config.genre, chapterNum, chapterTitle,
                                config.colloquialStyle, config.useMemes
                            )
                            val rectifyResult = aiRepo.generateContent(rectifyPrompt, maxTokens = 8192, temperature = 0.4f)
                            if (rectifyResult is AiResult.Success) {
                                val rectified = rectifyResult.content
                                if (rectified.length >= content.length * 0.6) {
                                    content = rectified
                                }
                            }
                            // 最终验证
                            updateProgress(0.65f, "最终验证中")
                            content = if (isMimoModel()) postProcessForMimo(content) else content
                            val finalValidation = PostWriteValidator.validateForMimo(content)
                            if (finalValidation.issues.isNotEmpty()) {
                                content = if (isMimoModel()) autoReviseForMimo(content, finalValidation) else content
                            }
                            s.currentContent.value = content
                            val wordCount = content.replace("\\s+".toRegex(), "").length
                            val existing = repo.getChapter(bookId, chapterNum)
                            repo.saveChapter(ChapterEntity(uid = existing?.uid ?: 0, bookId = bookId,
                                chapterNumber = chapterNum, content = content, wordCount = wordCount,
                                title = chapterTitle, kind = "draft"))
                            // 写后结算：完整提取
                            updateProgress(0.70f, "提取时间线事件")
                            extractAndSaveTimelineEvents(bookId, chapterNum, content)
                            updateProgress(0.75f, "提取因果链")
                            extractCausalChain(bookId, chapterNum, content, config)
                            updateProgress(0.80f, "提取伏笔")
                            extractHooks(bookId, chapterNum, content, config)
                            updateProgress(0.83f, "提取情感")
                            extractEmotions(bookId, chapterNum, content, config)
                            updateProgress(0.86f, "更新关系网络")
                            extractRelationships(bookId, chapterNum, content, config)
                            updateProgress(0.89f, "更新信息边界")
                            extractInfoBoundary(bookId, chapterNum, content, config)
                            updateProgress(0.92f, "标注戏剧节拍")
                            extractNarrativeBeats(bookId, chapterNum, content, config)
                            updateProgress(0.95f, "生成章节摘要")
                            generateChapterSummary(bookId, chapterNum, chapterTitle, content, config)
                            updateProgress(0.99f, "完成")
                            successCount++
                            // 审计结果提示
                            val criticalCount = auditResult.issues.count { it.severity == "critical" }
                            val warningCount = auditResult.issues.count { it.severity == "warning" }
                            if (revisionRound > 0) {
                                _toast.emit("第${chapterNum}章审计：${auditResult.summary}（${revisionRound}轮修订，剩余${criticalCount}严重/${warningCount}警告）")
                            }
                        }
                        is AiResult.Error -> {
                            failCount++
                        }
                    }
                }
                s.stepProgress.value = 1f
                s.stepLoadingMessage.value = "完成"
                saveProjectToSession(s)
                _toast.emit("批量写作完成：成功${successCount}章，失败${failCount}章")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toast.emit("批量写作异常：${e.message?.take(50) ?: "未知错误"}")
            } finally {
                s.uiState.value = DramaticaUiState.Idle; s.isGenerating.value = false
                s.generationJob = null
            }
        }
    }

    fun postProcessForMimo(content: String): String {
        var processed = content

        // ===== 1. 修复重复字 =====
        processed = processed.replace("的的", "的")
        processed = processed.replace("地地", "地")
        processed = processed.replace("得得", "得")
        processed = processed.replace("了了", "了")
        processed = processed.replace("是是", "是")
        processed = processed.replace("在在", "在")

        // ===== 2. 删除AI口头禅/套话（按长度降序，避免短词先匹配导致碎片） =====
        val aiFillerPhrases = listOf(
            "伸手不见五指", "映入眼帘的是", "空气中弥漫", "空气中飘散", "空气中充满",
            "不由得倒吸", "不由得深吸", "不由自主", "情不自禁", "身不由己",
            "鬼使神差地", "身不由己地", "如释重负地",
            "暗自在心中", "在心中暗暗", "在心里默默",
            "一种说不清", "一种难以名状",
            "仿佛整个世界", "仿佛时间",
            "让人窒息的", "令人窒息的",
            "脑海中浮现", "脑海中闪过",
            "眼神中闪过", "眼中闪过一丝", "眸光一闪",
            "嘴角泛起", "嘴角扬起", "嘴角浮现", "嘴角微扬", "嘴角微翘", "嘴角微勾",
            "眼里闪过", "眸中闪过", "眼底闪过",
            "目光落在", "视线落在", "眼神落在",
            "目光扫过", "视线扫过", "眼神扫过",
            "目光深邃", "眼神深邃", "眼眸深邃",
            "目光复杂", "眼神复杂", "神情复杂",
            "目光坚定", "眼神坚定", "语气坚定",
            "神色复杂", "表情复杂", "面色复杂",
            "心中涌起一股", "心中充满了", "心里涌起",
            "内心深处", "灵魂深处", "骨子里",
            "心中一紧", "心头一紧", "心下一惊", "心下一沉",
            "心中一动", "心头一颤", "心念一动", "心神一动", "心思一动",
            "一股莫名的", "一阵莫名的", "一丝莫名的",
            "一股暖流", "一阵暖意", "一丝温暖",
            "一股寒意", "一阵寒意", "一丝凉意",
            "深吸一口气", "倒吸一口凉气", "屏住呼吸",
            "深吸口气", "长长地吐",
            "深吸一口", "倒吸一口",
            "触目惊心", "惊心动魄",
            "耳边响起", "耳边传来", "耳边回荡",
            "浑身一震", "全身一震", "身体一震",
            "一阵眩晕", "一阵恍惚", "一阵失神",
            "微微一笑", "淡淡一笑", "轻轻一笑",
            "莞尔一笑", "嫣然一笑", "回眸一笑",
            "摇了摇头", "点了点头", "摆了摆手",
            "叹了口气", "轻叹一声",
            "沉默了片刻", "沉默了一会儿", "顿了顿",
            "欲言又止", "张了张嘴", "想说些什么",
            "沉声道", "低声道", "轻声说道",
            "缓缓开口", "轻声开口", "淡淡开口",
            "缓缓地", "轻轻地", "慢慢地", "深深地", "久久地",
            "无声地", "无声无息", "悄然",
            "不知不觉", "鬼使神差", "身不由己",
            "下意识地", "忍不住地",
            "不由得想起", "不禁想起", "忽然想起",
            "不由得握紧", "不由得攥紧",
            "握紧拳头", "攥紧拳头", "捏紧拳头",
            "咬紧牙关", "咬紧牙", "咬了咬牙",
            "内心挣扎", "内心纠结", "内心矛盾",
            "天人交战", "思想斗争",
            "如释重负", "不可置信", "难以置信", "不可思议",
            "眼前一黑", "眼前一花",
            "映入眼帘", "印入眼帘",
            "环顾四周", "四下张望", "四处张望",
            "转身离去", "转身离开", "头也不回",
            "透着一股", "带着几分", "带着一丝",
            "带有一丝", "有一种", "有种",
            "说不出的", "难以言喻的", "无法形容的",
            "足以让", "足以令",
            "让人感到", "令人感到", "使人感到",
            "令人", "让人", "使人",
            "显得格外", "显得十分",
            "呈现出", "展现出", "流露出",
            "回荡着", "萦绕着", "充斥着",
            "充满了", "洋溢着", "弥漫着",
            "不知不觉", "鬼使神差", "身不由己",
            "仿佛在说", "似乎在说", "好像在说",
            "与此同时", "另一方面", "不仅如此", "除此之外",
            "突然间", "忽然间", "霎时间", "瞬息间",
            "一刹那", "一瞬间", "一眨眼",
            "转眼间", "一转眼", "转瞬间",
            "突然之间", "忽然之间", "猛然之间",
            "就在这时", "正在这时", "恰在此时",
            "此时此刻", "此情此景", "此时此地",
            "在那一刻", "在这一刻", "在这一瞬间",
            "片刻之后", "过了一会儿", "不久之后",
            "过了许久", "很久以后", "许久之后",
            "某种意义上", "某种程度上", "从某种角度",
            "可以想见", "不难想象", "可想而知",
            "众所周知", "不言而喻", "毫无疑问",
            "值得注意的是", "值得一提的是",
            "很明显的", "总而言之", "总的来说", "由此可见",
            "不知为何", "说不清", "道不明",
            "前所未有", "史无前例", "空前绝后",
            "灵机一动", "计上心来", "眉头一皱",
            "惊天动地", "震天动地", "铺天盖地",
            "一望无际", "无边无际", "漫无边际",
            "电光火石", "千钧一发", "间不容发",
            "心潮澎湃", "热血沸腾", "心绪激荡",
            "思绪万千", "百感交集", "感慨万千",
            "震耳欲聋", "响彻云霄", "声震四野",
            "璀璨夺目", "光彩夺目", "耀眼夺目",
            "漆黑一片", "黑暗笼罩",
            "热泪盈眶", "泪流满面", "泪如雨下",
            "欣喜若狂", "欢天喜地", "喜出望外",
            "怒不可遏", "怒火中烧", "勃然大怒",
            "惊恐万状", "魂飞魄散", "心惊胆战",
            "悲痛欲绝", "痛不欲生", "肝肠寸断",
            "恋恋不舍", "依依不舍", "难舍难分",
            "迫不及待", "急不可耐", "心急如焚",
            "眼花缭乱", "目不暇接", "应接不暇",
            "络绎不绝", "川流不息", "源源不断",
            "水泄不通", "人山人海", "摩肩接踵",
            "鸦雀无声", "万籁俱寂", "针落可闻",
            "异口同声", "不约而同",
            "心照不宣", "心有灵犀", "默契十足",
            "猝不及防", "措手不及", "始料未及",
            "居高临下", "高高在上",
            "不寒而栗", "毛骨悚然", "胆战心惊",
            "势不可挡", "势如破竹", "锐不可当",
            "其实，", "说实话，", "坦白说，",
            "忽然，", "突然，", "顿时，",
            "随即，", "随后，", "紧接着，",
            "不由得", "下意识", "不经意",
            "万万没想到",
        ).distinct().sortedByDescending { it.length }
        for (phrase in aiFillerPhrases) {
            processed = processed.replace(phrase, "")
        }

        // ===== 3. 修复AI标记词（替换而非删除） =====
        processed = processed.replace("他心想", "他想")
        processed = processed.replace("她心想", "她想")
        processed = processed.replace("他心想：", "他想：")
        processed = processed.replace("她心想：", "她想：")
        processed = processed.replace("他感到", "他觉得")
        processed = processed.replace("她感到", "她觉得")
        // 清理"他觉得"后面的冗余修饰
        processed = processed.replace(Regex("他觉得(?:自己)?(?:似乎|好像|仿佛|有点|有些)"), "他觉得")

        // ===== 4. 修复AI句式 =====
        // 逗号连接独立句子 → 句号
        processed = processed.replace("，然后", "。然后")
        processed = processed.replace("，接着", "。接着")
        processed = processed.replace("，突然", "。突然")
        processed = processed.replace("，这时", "。这时")
        processed = processed.replace("，此刻", "。此刻")
        processed = processed.replace("，此时", "。此时")
        processed = processed.replace("，只见", "。只见")
        processed = processed.replace("，只听", "。只听")
        processed = processed.replace("，紧接着", "。紧接着")

        // ===== 5. 拆分超长句（50字以上的逗号分隔改为句号） =====
        processed = processed.replace(Regex("(?<=[^，。！？]{50,})，"), "。")

        // ===== 6. 修复标点 =====
        processed = processed.replace("。。", "。")
        processed = processed.replace("！！", "！")
        processed = processed.replace("……", "…")
        processed = processed.replace("，，", "，")
        processed = processed.replace("；；", "；")
        processed = processed.replace("，。", "。")
        processed = processed.replace("。，", "。")
        processed = processed.replace("！。", "！")
        processed = processed.replace("？。", "？")
        processed = processed.replace(Regex("。{3,}"), "…")
        processed = processed.replace(Regex("！{3,}"), "！")
        processed = processed.replace(Regex("，{3,}"), "，")

        // ===== 7. 删除开头的章节标题 =====
        processed = processed.replace(Regex("^第[一二三四五六七八九十百千\\d]+章\\s*[：:]?\\s*.*?\n"), "")
        processed = processed.replace(Regex("^第[一二三四五六七八九十百千\\d]+节\\s*[：:]?\\s*.*?\n"), "")

        // ===== 8. 删除多余空行 =====
        processed = processed.replace(Regex("\n{3,}"), "\n\n")

        // ===== 9. 删除开头结尾空白 =====
        processed = processed.trim()

        return processed
    }

    /**
     * 基于 validateForMimo 的结果，对内容进行自动修订。
     * 使用规则引擎直接修复，不依赖二次 AI 调用（速度更快）。
     */
    private fun autoReviseForMimo(content: String, validation: ValidationResult): String {
        var revised = content
        for (issue in validation.issues) {
            when (issue.rule) {
                "HIGH_ADJECTIVE_DENSITY" -> {
                    // 简化形容词密集的句子：删除多余的"的"字修饰
                    revised = revised.replace(Regex("的([^，。！？]{1,5})的"), "的$1")
                }
                "OVER_DESCRIPTIVE" -> {
                    // 在长段落中插入换行，改善可读性
                    revised = revised.replace(Regex("(?<=[。！？])(?=[^\\n])"), "\n")
                }
                "CONSECUTIVE_LONG_SENTENCES" -> {
                    // 将长句中的逗号改为句号
                    revised = revised.replace(Regex("(?<=[^，。！？]{50,})，"), "。")
                }
            }
        }
        return revised
    }

    fun loadChapter(bookId: String, chapterNum: Int) {
        viewModelScope.launch {
            try {
                val ch = repo.getChapter(bookId, chapterNum)
                val s = activeSessionOrNull() ?: return@launch
                if (ch != null) {
                    s.editingChapter.value = ch
                } else {
                    s.editingChapter.value = ChapterEntity(bookId = bookId, chapterNumber = chapterNum)
                }
            } catch (e: Exception) {
                _toast.emit("加载章节失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 角色操作 ----
    fun addCharacter(bookId: String, name: String, role: String, type: String, desc: String) {
        viewModelScope.launch {
            try {
                val chineseRole = when(type) {
                    "protagonist" -> "主角"
                    "antagonist" -> "反派"
                    "sidekick" -> "伙伴"
                    "impact" -> "冲击者"
                    "guardian" -> "守护者"
                    "contagonist" -> "阻碍者"
                    "skeptic" -> "怀疑者"
                    else -> "影响者"
                }
                repo.insertCharacter(CharacterEntity(bookId = bookId, name = name, role = chineseRole,
                    avatar = name.firstOrNull()?.toString() ?: "?", type = type, description = desc))
            } catch (e: Exception) {
                _toast.emit("添加角色失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun updateCharacter(uid: Long, bookId: String, name: String, role: String, type: String, desc: String) {
        viewModelScope.launch {
            try {
                repo.insertCharacter(CharacterEntity(uid = uid, bookId = bookId, name = name, role = role,
                    avatar = name.firstOrNull()?.toString() ?: "?", type = type, description = desc))
            } catch (e: Exception) {
                _toast.emit("更新角色失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    /**
     * 导出小说为合并 TXT 文本，返回完整内容。
     */
    suspend fun exportNovel(bookId: String): String {
        val chapters = repo.getChapters(bookId).first().sortedBy { it.chapterNumber }
        val config = _sessions.value[bookId]?.storyConfig?.value ?: StoryConfig()
        return buildString {
            appendLine("《${config.title}》")
            if (config.genre.isNotBlank()) appendLine("类型：${config.genre}")
            appendLine("=" .repeat(40))
            appendLine()
            for (ch in chapters) {
                val title = if (ch.title.isNotBlank()) "第${ch.chapterNumber}章 ${ch.title}" else "第${ch.chapterNumber}章"
                appendLine(title)
                appendLine("-".repeat(title.length))
                appendLine()
                appendLine(ch.content)
                appendLine()
                appendLine()
            }
        }
    }

    // ---- 伏笔操作 ----
    fun addHook(bookId: String, description: String, chapter: Int) {
        viewModelScope.launch {
            try {
                repo.insertHook(HookEntity(id = UUID.randomUUID().toString().take(8), bookId = bookId,
                    description = description, plantedChapter = chapter))
            } catch (e: Exception) {
                _toast.emit("添加伏笔失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun resolveHook(hookId: String, chapter: Int) {
        viewModelScope.launch {
            try {
                repo.resolveHook(hookId, chapter)
            } catch (e: Exception) {
                _toast.emit("标记伏笔失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 因果链操作 ----
    fun addCausalLink(bookId: String, chapter: Int, cause: String, event: String, consequence: String) {
        viewModelScope.launch {
            try {
                repo.insertCausalLink(CausalLinkEntity(bookId = bookId, chapter = chapter,
                    cause = cause, event = event, consequence = consequence))
            } catch (e: Exception) {
                _toast.emit("添加因果链失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 关系操作 ----
    fun addRelationship(bookId: String, charA: String, charB: String, type: String, strength: Int) {
        viewModelScope.launch {
            try {
                repo.insertRelationship(RelationshipEntity(bookId = bookId, characterA = charA,
                    characterB = charB, type = type, strength = strength))
            } catch (e: Exception) {
                _toast.emit("添加关系失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 情感操作 ----
    fun addEmotion(bookId: String, charId: String, emotion: String, intensity: Int, chapter: Int) {
        viewModelScope.launch {
            try {
                repo.insertEmotion(EmotionEntity(bookId = bookId, characterId = charId,
                    emotion = emotion, intensity = intensity, chapter = chapter))
            } catch (e: Exception) {
                _toast.emit("添加情感失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 时间轴操作 ----
    fun addTimelineEvent(bookId: String, chapter: Int, action: String, type: String) {
        viewModelScope.launch {
            try {
                repo.insertTimelineEvent(TimelineEntity(bookId = bookId, chapter = chapter,
                    action = action, type = type))
            } catch (e: Exception) {
                _toast.emit("添加时间线事件失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    /**
     * 从章节内容中提取关键事件并保存到时间线。
     * 使用 AI 识别冲突、揭示、情感转折等关键事件。
     */
    private suspend fun extractAndSaveTimelineEvents(bookId: String, chapterNum: Int, content: String) {
        try {
            val prompt = "请从以下小说章节中提取2-3个关键事件，每个事件标注类型和简短描述。\n\n" +
                "类型选项：conflict（冲突）、reveal（揭示/发现）、emotion（情感转折）、foreshadow（伏笔）\n\n" +
                "严格按以下格式输出（每行一个事件）：\n" +
                "类型|描述（15字以内）\n\n" +
                "章节内容：\n${content.take(1500)}"
            val result = aiRepo.generateContent(prompt, maxTokens = 512, temperature = 0.3f)
            if (result is AiResult.Success) {
                for (line in result.content.lines()) {
                    val parts = line.trim().split("|", limit = 2)
                    if (parts.size == 2) {
                        val type = parts[0].trim()
                        val action = parts[1].trim().take(30)
                        if (action.isNotBlank() && type in listOf("conflict", "reveal", "emotion", "foreshadow")) {
                            // 删除该章节已有的时间线事件，避免重复
                            repo.insertTimelineEvent(TimelineEntity(
                                bookId = bookId, chapter = chapterNum,
                                action = action, type = type
                            ))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 时间线提取失败不影响写作
        }
    }

    /**
     * AI 对话：根据用户消息和当前编辑器内容，返回 AI 回复。
     * 用于写作页面的 AI 助手对话功能。
     */
    suspend fun aiChatMessage(userMessage: String, editorContent: String): String {
        val config = storyConfig.value
        val prompt = buildString {
            append("你是小说「${config.title}」的AI写作助手。\n")
            if (config.genre.isNotBlank()) append("类型：${config.genre}\n")
            if (config.coreSetting.isNotBlank()) append("世界观：${config.coreSetting.take(300)}\n")
            if (config.characters.isNotBlank()) append("角色：${config.characters.take(300)}\n")
            if (editorContent.isNotBlank()) {
                append("\n当前章节内容：\n${editorContent.takeLast(1000)}\n")
            }
            append("\n用户问题：$userMessage\n\n")
            append("请直接回答，简洁实用。如果是写作建议，给出具体可操作的方案。")
        }
        val result = aiRepo.generateContent(prompt, maxTokens = 2048, temperature = 0.7f)
        return when (result) {
            is AiResult.Success -> result.content
            is AiResult.Error -> "抱歉，AI暂时无法回复：${result.message}"
        }
    }

    // ---- 角色名提取工具 ----
    // ---- 信息边界操作 ----
    fun addKnownInfo(bookId: String, characterId: String, infoKey: String, content: String, chapter: Int, source: String) {
        viewModelScope.launch {
            try {
                repo.insertKnownInfo(KnownInfoEntity(bookId = bookId, characterId = characterId,
                    infoKey = infoKey, content = content, learnedInChapter = chapter, source = source))
            } catch (e: Exception) {
                _toast.emit("添加信息边界失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    // ---- 多线叙事操作 ----
    fun addNarrativeThread(bookId: String, name: String, type: String, povCharacterId: String, goalArc: String) {
        viewModelScope.launch {
            try {
                repo.insertNarrativeThread(NarrativeThreadEntity(
                    id = UUID.randomUUID().toString().take(8), bookId = bookId,
                    name = name, type = type, povCharacterId = povCharacterId, goalArc = goalArc))
            } catch (e: Exception) {
                _toast.emit("添加叙事线程失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun updateThreadStatus(id: String, chapter: Int, status: String) {
        viewModelScope.launch {
            try { repo.updateThreadStatus(id, chapter, status) }
            catch (e: Exception) { _toast.emit("更新线程状态失败：${e.message?.take(50) ?: "未知错误"}") }
        }
    }

    fun deleteNarrativeThread(thread: NarrativeThreadEntity) {
        viewModelScope.launch {
            try { repo.deleteNarrativeThread(thread) }
            catch (e: Exception) { _toast.emit("删除叙事线程失败：${e.message?.take(50) ?: "未知错误"}") }
        }
    }

    // ---- 戏剧节拍操作 ----
    fun addChapterBeat(bookId: String, chapter: Int, beatType: String, description: String, characterId: String, emotionalTarget: String) {
        viewModelScope.launch {
            try {
                repo.insertChapterBeat(ChapterBeatEntity(bookId = bookId, chapter = chapter,
                    beatType = beatType, description = description, characterId = characterId, emotionalTarget = emotionalTarget))
            } catch (e: Exception) {
                _toast.emit("添加节拍失败：${e.message?.take(50) ?: "未知错误"}")
            }
        }
    }

    fun deleteChapterBeat(beat: ChapterBeatEntity) {
        viewModelScope.launch {
            try { repo.deleteChapterBeat(beat) }
            catch (e: Exception) { _toast.emit("删除节拍失败：${e.message?.take(50) ?: "未知错误"}") }
        }
    }

    companion object {
        // 字段标签映射（用于AI重写交叉上下文）
        private val fieldLabelMap = mapOf(
            "title" to "书名", "genre" to "题材",
            "theme" to "核心主题", "world" to "世界观设定",
            "characters" to "角色原型", "narrative" to "叙事结构", "style" to "语言风格"
        )
        // 角色类型关键词（用于检测冒号分隔格式中哪边是名字）
        private val TYPE_KEYWORDS = Regex(
            "主角|反派|伙伴|对立|影响|守护|配角|龙套|英雄|魔王|敌人|对手|助手|师傅|导师|角色|名字|姓名|名称|冲击|阻碍|拖延|怀疑|质疑|引导|搭档"
        )
        // 角色标签行（纯标签，不是名字）：匹配"角色一"、"角色1"、"角色一："、"**角色一**"等
        private val LABEL_LINE = Regex("^\\s*(?:\\*{1,2})?角色\\s*[一二三四五六七八九十\\dA-Za-z]?(?:\\*{1,2})?\\s*[：:]?\\s*$")
        // 角色类型行（纯角色类型，不是名字）：匹配"主角"、"反派"、"**主角**"、"主角："等
        private val ROLE_LINE = Regex("^\\s*(?:\\*{1,2})?(?:主角|反派|冲击者|守护者|阻碍者|伙伴|怀疑者|英雄|魔王|敌人|对手|导师|师傅|配角|龙套|助手|队友|搭档|影响者)(?:\\*{1,2})?\\s*[：:]?\\s*$")

        /**
         * 在多行文本中找到真正的名字行。
         * 跳过纯标签行（如"角色一"、"角色1"、"角色一："），跳过纯角色类型行（如"主角"、"**反派**"），返回第一个有意义的内容行。
         */
        private fun findNameLine(lines: List<String>, index: Int): String {
            for (line in lines) {
                val trimmed = line.trim()
                // 去掉 markdown 标题标记（### 等）后再检查
                val stripped = trimmed.replace(Regex("^#{1,3}\\s*"), "")
                // 跳过纯标签行（如"角色一"、"角色1"、"角色一："、"【角色】"、"**角色一**"、"### 角色一"）
                if (LABEL_LINE.matches(trimmed) || LABEL_LINE.matches(stripped)) continue
                if (trimmed == "角色" || trimmed.startsWith("【角色") || trimmed.startsWith("[角色")) continue
                if (stripped == "角色" || stripped.startsWith("【角色") || stripped.startsWith("[角色")) continue
                // 跳过纯角色类型行（如"主角"、"**反派**"、"反派："、"主角："）
                if (ROLE_LINE.matches(trimmed) || ROLE_LINE.matches(stripped)) continue
                // 跳过纯分隔符行
                if (trimmed.matches(Regex("^[-=*#~]+$"))) continue
                return trimmed
            }
            // 全是标签行，返回第一个
            return lines.first().trim()
        }

        /**
         * 智能提取角色名，支持多种 AI 输出格式：
         * - "张三" → "张三"
         * - "主角：张三" → "张三"（类型在左，名字在右）
         * - "张三：主角" → "张三"（名字在左，类型在右）
         * - "角色一：张三" → "张三"（标签在左，名字在右）
         * - "名字：张三，角色类型：主角" → "张三"
         */
        private fun extractCharacterName(firstLine: String, index: Int): String {
            // 辅助函数：清理名字中的 markdown 标记和多余符号
            fun clean(s: String) = s.replace(Regex("[*_#]+"), "").replace(Regex("[（(].*?[）)]"), "").trim()

            // 模式1：包含"名字"或"姓名"关键词，提取后面的值
            val namePattern = Regex("(?:名字|姓名|名称)[：:]\\s*(\\S+)")
            namePattern.find(firstLine)?.groupValues?.get(1)?.trim()?.let { return clean(it) }

            // 模式2：冒号分隔，检测哪边是名字
            val colonIdx = firstLine.indexOfFirst { it == '：' || it == ':' }
            if (colonIdx > 0 && colonIdx < firstLine.length - 1) {
                val left = firstLine.substring(0, colonIdx).trim()
                val right = firstLine.substring(colonIdx + 1).trim()

                // 左边是类型/标签关键词 → 取右边（名字）
                if (TYPE_KEYWORDS.containsMatchIn(left)) {
                    return clean(right).ifBlank { "角色${index + 1}" }
                }
                // 右边是类型关键词 → 取左边（名字）
                if (TYPE_KEYWORDS.containsMatchIn(right)) {
                    return clean(left).ifBlank { "角色${index + 1}" }
                }
                // 两边都不是类型关键词，取较短的那边（名字通常较短）
                val shorter = if (left.length <= right.length) left else right
                return clean(shorter)
            }

            // 模式3：无冒号，直接取首行（去掉可能的括号注释和 markdown）
            return clean(firstLine).ifBlank { "角色${index + 1}" }
        }

        /**
         * 从首行检测角色类型。
         */
        private fun detectCharacterType(firstLine: String): String {
            return when {
                firstLine.contains("主角") || firstLine.contains("英雄") -> "protagonist"
                firstLine.contains("反派") || firstLine.contains("魔王") || firstLine.contains("敌人") || firstLine.contains("对手") || firstLine.contains("对立") -> "antagonist"
                firstLine.contains("伙伴") || firstLine.contains("助手") || firstLine.contains("队友") || firstLine.contains("搭档") -> "sidekick"
                firstLine.contains("守护") || firstLine.contains("导师") || firstLine.contains("师傅") || firstLine.contains("引导") -> "guardian"
                firstLine.contains("影响") || firstLine.contains("冲击") -> "impact"
                firstLine.contains("阻碍") || firstLine.contains("拖延") -> "contagonist"
                firstLine.contains("怀疑") || firstLine.contains("质疑") -> "skeptic"
                else -> "protagonist"
            }
        }
    }
}