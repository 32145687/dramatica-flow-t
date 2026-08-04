package com.dramatica.flow.ui.viewmodel

import com.dramatica.flow.data.DramaticaProjectEntity
import com.dramatica.flow.data.DramaticaStep
import com.dramatica.flow.data.DramaticaUiState
import com.dramatica.flow.data.StoryConfig
import com.dramatica.flow.data.WritingSkillEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 单本书的独立创作会话，持有所有按书隔离的状态。
 * 每本书一个 Session，互不干扰，支持并行写作。
 */
class BookSession(
    val bookId: String,
    initialConfig: StoryConfig = StoryConfig(),
    initialStep: DramaticaStep = DramaticaStep.BASIC_INFO
) {
    // 创作项目
    val currentProject = MutableStateFlow<DramaticaProjectEntity?>(null)

    // 当前步骤
    val currentStep = MutableStateFlow(initialStep)

    // UI 状态机
    val uiState = MutableStateFlow<DramaticaUiState>(DramaticaUiState.Idle)

    // 故事配置
    val storyConfig = MutableStateFlow(initialConfig)

    // 大纲
    val outline = MutableStateFlow("")

    // 因果链
    val causalChain = MutableStateFlow("")

    // 情感弧线
    val emotionalArcs = MutableStateFlow("")

    // 待回收伏笔
    val pendingHooks = MutableStateFlow("")

    // 前情摘要（真相文件，每章自动累加）
    val summaryHistory = MutableStateFlow("")

    // 当前章节内容
    val currentContent = MutableStateFlow("")

    // 是否正在生成
    val isGenerating = MutableStateFlow(false)

    // AI 生成 Job（用于取消）
    var generationJob: Job? = null

    // 生成进度
    val stepProgress = MutableStateFlow(0f)

    // 生成提示消息
    val stepLoadingMessage = MutableStateFlow("")

    // 生成错误
    val generationError = MutableStateFlow<String?>(null)

    // 当前编辑的章节
    val editingChapter = MutableStateFlow<com.dramatica.flow.data.ChapterEntity?>(null)

    // 当前章节号
    val currentChapterNum = MutableStateFlow(1)

    // 写作技能（功能B：风格蒸馏）
    val writingSkill = MutableStateFlow<WritingSkillEntity?>(null)

    // 快照：保存当前状态到 BookSession（用于内存持久化）
    fun snapshot(): SessionSnapshot = SessionSnapshot(
        bookId = bookId,
        currentProject = currentProject.value,
        currentStep = currentStep.value,
        currentChapterNum = currentChapterNum.value,
        uiState = uiState.value,
        storyConfig = storyConfig.value,
        outline = outline.value,
        causalChain = causalChain.value,
        emotionalArcs = emotionalArcs.value,
        pendingHooks = pendingHooks.value,
        summaryHistory = summaryHistory.value,
        currentContent = currentContent.value,
        isGenerating = isGenerating.value,
        stepProgress = stepProgress.value,
        stepLoadingMessage = stepLoadingMessage.value,
        generationError = generationError.value,
        editingChapter = editingChapter.value,
        writingSkill = writingSkill.value
    )

    // 从快照恢复
    fun restore(snapshot: SessionSnapshot) {
        currentProject.value = snapshot.currentProject
        currentStep.value = snapshot.currentStep
        currentChapterNum.value = snapshot.currentChapterNum
        uiState.value = snapshot.uiState
        storyConfig.value = snapshot.storyConfig
        outline.value = snapshot.outline
        causalChain.value = snapshot.causalChain
        emotionalArcs.value = snapshot.emotionalArcs
        pendingHooks.value = snapshot.pendingHooks
        summaryHistory.value = snapshot.summaryHistory
        currentContent.value = snapshot.currentContent
        isGenerating.value = snapshot.isGenerating
        stepProgress.value = snapshot.stepProgress
        stepLoadingMessage.value = snapshot.stepLoadingMessage
        generationError.value = snapshot.generationError
        editingChapter.value = snapshot.editingChapter
        writingSkill.value = snapshot.writingSkill
    }
}

/**
 * 会话快照，用于在切换书籍时保存/恢复状态。
 */
data class SessionSnapshot(
    val bookId: String,
    val currentProject: DramaticaProjectEntity?,
    val currentStep: DramaticaStep,
    val currentChapterNum: Int,
    val uiState: DramaticaUiState,
    val storyConfig: StoryConfig,
    val outline: String,
    val causalChain: String,
    val emotionalArcs: String,
    val pendingHooks: String,
    val summaryHistory: String,
    val currentContent: String,
    val isGenerating: Boolean,
    val stepProgress: Float,
    val stepLoadingMessage: String,
    val generationError: String?,
    val editingChapter: com.dramatica.flow.data.ChapterEntity?,
    val writingSkill: com.dramatica.flow.data.WritingSkillEntity?
)