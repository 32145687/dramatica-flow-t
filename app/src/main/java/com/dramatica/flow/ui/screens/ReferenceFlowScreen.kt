package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.*
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel

/**
 * 参考小说创作 — 全新工作台界面
 *
 * 与 FlowScreen（7步向导）完全不同：
 * - 阶段1：选择创作方式（使用分析结果 / 修改后创作）
 * - 阶段2a：修改模式 — 编辑角色名、故事设定、世界观
 * - 阶段2b：直接使用 — 进入写作工作台
 * - 阶段3：写作工作台 — 左侧参考面板 + 右侧写作区
 */

enum class RefCreationMode { DECIDE, EDIT, WRITING }

@Composable
fun ReferenceFlowScreen(
    bookId: String, book: BookEntity?,
    characters: List<CharacterEntity>, hooks: List<HookEntity>,
    causalChainList: List<CausalLinkEntity>, emotions: List<EmotionEntity>,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val storyConfig by vm.storyConfig.collectAsState()
    val currentProject by vm.currentProject.collectAsState()
    val refAnalysis = storyConfig.referenceAnalysis

    // 解析分析结果
    val refTitle = remember(refAnalysis) { TextParser.extractField(refAnalysis, "书名") }
    val refGenre = remember(refAnalysis) { TextParser.extractField(refAnalysis, "题材") }
    val refTheme = remember(refAnalysis) { TextParser.extractField(refAnalysis, "核心主题|主题") }
    val refWorld = remember(refAnalysis) { TextParser.extractFieldMulti(refAnalysis, "世界观|世界设定") }
    val refCharacters = remember(refAnalysis) { TextParser.extractFieldMulti(refAnalysis, "角色|角色原型|人物") }
    val refNarrative = remember(refAnalysis) { TextParser.extractFieldMulti(refAnalysis, "叙事|叙事结构|故事推进") }
    val refStyle = remember(refAnalysis) { TextParser.extractFieldMulti(refAnalysis, "语言风格|风格|文风") }

    // 页面状态：如果已确认过设定，直接进入写作模式
    val initialMode = if (currentProject?.referenceCreationConfigured == true) {
        RefCreationMode.WRITING
    } else {
        RefCreationMode.DECIDE
    }
    var mode by remember { mutableStateOf(initialMode) }

    // 编辑字段
    var editTitle by remember(refTitle) { mutableStateOf(refTitle) }
    var editGenre by remember(refGenre) { mutableStateOf(refGenre) }
    var editTheme by remember(refTheme) { mutableStateOf(refTheme) }
    var editWorld by remember(refWorld) { mutableStateOf(refWorld) }
    var editChars by remember(refCharacters) { mutableStateOf(refCharacters) }
    var editNarrative by remember(refNarrative) { mutableStateOf(refNarrative) }
    var editStyle by remember(refStyle) { mutableStateOf(refStyle) }

    // 确认创建
    fun confirmCreate() {
        // 仅保存编辑后的字段，不重复追加原始分析（原始分析已在 session 中保留）
        val finalAnalysis = buildString {
            appendLine("书名：${editTitle.ifBlank { refTitle }}")
            appendLine("题材：${editGenre.ifBlank { refGenre }}")
            appendLine("核心主题：${editTheme.ifBlank { refTheme }}")
            appendLine("世界观设定：${editWorld.ifBlank { refWorld }}")
            appendLine("角色原型：${editChars.ifBlank { refCharacters }}")
            appendLine("叙事结构：${editNarrative.ifBlank { refNarrative }}")
            appendLine("语言风格：${editStyle.ifBlank { refStyle }}")
        }
        val finalTitle = editTitle.ifBlank { refTitle }.ifBlank { "未命名作品" }
        val finalGenre = editGenre.ifBlank { refGenre }.ifBlank { "玄幻" }
        vm.updateBookFromAnalysis(bookId, finalTitle, finalGenre, finalAnalysis)
        mode = RefCreationMode.WRITING
    }

    when (mode) {
        RefCreationMode.DECIDE -> DecideScreen(
            refTitle, refGenre, refTheme, refWorld, refCharacters, refNarrative, refStyle,
            book, onBack = onBack,
            onUseDirectly = { confirmCreate() },
            onModify = {
                mode = RefCreationMode.EDIT
            }
        )
        RefCreationMode.EDIT -> EditScreen(
            editTitle, editGenre, editTheme, editWorld, editChars, editNarrative, editStyle,
            refAnalysis = refAnalysis,
            refTitle, refGenre, refTheme, refWorld, refCharacters, refNarrative, refStyle,
            onUpdateTitle = { editTitle = it },
            onUpdateGenre = { editGenre = it },
            onUpdateTheme = { editTheme = it },
            onUpdateWorld = { editWorld = it },
            onUpdateChars = { editChars = it },
            onUpdateNarrative = { editNarrative = it },
            onUpdateStyle = { editStyle = it },
            onCancel = { mode = RefCreationMode.DECIDE },
            onConfirm = { confirmCreate() },
            vm = vm
        )
        RefCreationMode.WRITING -> WritingWorkbenchScreen(
            bookId = bookId, book = book, vm = vm,
            refAnalysis = refAnalysis, refTitle = refTitle, refGenre = refGenre,
            refTheme = refTheme, refWorld = refWorld, refCharacters = refCharacters,
            refNarrative = refNarrative, refStyle = refStyle,
            onBack = onBack  // 直接回书架，防止重复创建书籍
        )
    }
}

// ============================================================
//  阶段1：选择创作方式
// ============================================================
@Composable
private fun DecideScreen(
    refTitle: String, refGenre: String, refTheme: String,
    refWorld: String, refCharacters: String, refNarrative: String, refStyle: String,
    book: BookEntity?,
    onBack: () -> Unit,
    onUseDirectly: () -> Unit,
    onModify: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("参考创作", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        InkDividerLight()

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 分析结果摘要卡片 =====
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AccentBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("📖 分析完成", fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(refTitle.ifBlank { "未命名" }, fontSize = 13.sp,
                            fontWeight = FontWeight.W600, color = TextPrimary)
                    }
                    Text("AI 已完成参考小说分析，提取了故事框架、角色原型、世界观等要素。",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)

                    // 分析摘要网格
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalysisTag("题材", refGenre, Modifier.weight(1f))
                        AnalysisTag("主题", refTheme.take(30), Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnalysisTag("角色", "${refCharacters.lines().size}个角色", Modifier.weight(1f))
                        AnalysisTag("风格", refStyle.take(20), Modifier.weight(1f))
                    }
                }
            }

            // ===== 选择创作方式 =====
            Text("请选择创作方式", fontSize = 15.sp, fontWeight = FontWeight.W600,
                color = TextPrimary, fontFamily = SerifFamily)

            // 选项1：直接使用分析结果
            Card(
                Modifier.fillMaxWidth().clickable { onUseDirectly() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Accent.copy(alpha = 0.3f))
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                    // 图标
                    Surface(
                        Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Accent.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("🎭", fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("使用分析结果创作", fontSize = 14.sp, fontWeight = FontWeight.W600,
                            color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("保留参考小说的故事框架、角色和世界观，在此基础上进行续写或改写。",
                            fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagChip("保留故事线")
                            TagChip("保留角色")
                            TagChip("保留世界观")
                        }
                    }
                    Icon(
                        NavIcons.ChevronRight,
                        contentDescription = null,
                        Modifier.size(20.dp).align(Alignment.CenterVertically),
                        tint = Accent
                    )
                }
            }

            // 选项2：修改后创作
            Card(
                Modifier.fillMaxWidth().clickable { onModify() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Border)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Info.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("✏️", fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("修改后创作", fontSize = 14.sp, fontWeight = FontWeight.W600,
                            color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("修改角色名、调整故事设定，借鉴参考小说的叙事风格，创作全新故事。",
                            fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagChip("修改角色名")
                            TagChip("调整故事")
                            TagChip("借鉴风格")
                        }
                    }
                    Icon(
                        NavIcons.ChevronRight,
                        contentDescription = null,
                        Modifier.size(20.dp).align(Alignment.CenterVertically),
                        tint = TextTertiary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 底部提示
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = BgSidebar.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("「使用分析结果」适合同人续写；「修改后创作」适合借鉴风格创作全新故事",
                        fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun AnalysisTag(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.7f)
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(label, fontSize = 10.sp, color = TextTertiary)
            Text(value.ifBlank { "未识别" }, fontSize = 11.sp, color = TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = BgSidebar
    ) {
        Text(text, fontSize = 10.sp, color = TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ============================================================
//  阶段2：修改模式 — 步骤向导
// ============================================================
private enum class EditStep(val label: String, val fieldKey: String, val description: String) {
    TITLE("书名与题材", "title", "确定作品名称和题材分类"),
    THEME("核心主题", "theme", "明确故事想表达的核心思想"),
    WORLD("世界观设定", "world", "构建故事的时空背景和规则体系"),
    CHARACTERS("角色原型", "characters", "塑造角色形象、性格和关系"),
    NARRATIVE("叙事结构", "narrative", "设计故事推进方式和节奏"),
    STYLE("语言风格", "style", "确定文字风格、描写密度和对话占比"),
    CONFIRM("确认创建", "confirm", "检查所有设定，开始创作")
}

private val fieldLabelMap = mapOf(
    "title" to "书名", "genre" to "题材",
    "theme" to "核心主题", "world" to "世界观设定",
    "characters" to "角色原型", "narrative" to "叙事结构", "style" to "语言风格"
)

@Composable
private fun EditScreen(
    editTitle: String, editGenre: String, editTheme: String,
    editWorld: String, editChars: String, editNarrative: String, editStyle: String,
    refAnalysis: String,
    refTitle: String, refGenre: String, refTheme: String,
    refWorld: String, refCharacters: String, refNarrative: String, refStyle: String,
    onUpdateTitle: (String) -> Unit, onUpdateGenre: (String) -> Unit,
    onUpdateTheme: (String) -> Unit, onUpdateWorld: (String) -> Unit,
    onUpdateChars: (String) -> Unit, onUpdateNarrative: (String) -> Unit,
    onUpdateStyle: (String) -> Unit,
    onCancel: () -> Unit, onConfirm: () -> Unit,
    vm: MainViewModel
) {
    var currentStep by remember { mutableStateOf(EditStep.TITLE) }
    val fieldLoading by vm.fieldRewriteLoading.collectAsState()
    val fieldResult by vm.fieldRewriteResult.collectAsState()
    val activeFieldKey by vm.activeRewriteFieldKey.collectAsState()

    // 离开EDIT模式时清除重写状态，防止残留数据污染下次进入
    DisposableEffect(Unit) {
        onDispose { vm.clearFieldRewriteResult() }
    }

    // 构建所有字段的交叉上下文映射
    val allFields = remember(editTitle, editGenre, editTheme, editWorld, editChars, editNarrative, editStyle) {
        mapOf(
            "title" to editTitle, "genre" to editGenre,
            "theme" to editTheme, "world" to editWorld,
            "characters" to editChars, "narrative" to editNarrative,
            "style" to editStyle
        )
    }

    // 空字段列表（用于提醒）
    val emptyFields = remember(editTitle, editGenre, editTheme, editWorld, editChars, editNarrative, editStyle) {
        listOf(
            "title" to editTitle, "genre" to editGenre,
            "theme" to editTheme, "world" to editWorld,
            "characters" to editChars, "narrative" to editNarrative,
            "style" to editStyle
        ).filter { it.second.isBlank() }.map { it.first }
    }

    // 一键重写字段列表（排除书名和题材，因为它们是短字段）
    val batchRewriteFields = remember(editTheme, editWorld, editChars, editNarrative, editStyle) {
        listOf(
            Triple("theme", "核心主题", editTheme),
            Triple("world", "世界观设定", editWorld),
            Triple("characters", "角色原型", editChars),
            Triple("narrative", "叙事结构", editNarrative),
            Triple("style", "语言风格", editStyle)
        )
    }

    // 进入EDIT模式时，自动为空字段触发AI重写（仅触发一次）
    var autoRewriteTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoRewriteTriggered) {
            autoRewriteTriggered = true
            val emptyOnes = listOf(
                Triple("title", "书名", editTitle),
                Triple("genre", "题材", editGenre),
                Triple("theme", "核心主题", editTheme),
                Triple("world", "世界观设定", editWorld),
                Triple("characters", "角色原型", editChars),
                Triple("narrative", "叙事结构", editNarrative),
                Triple("style", "语言风格", editStyle)
            ).filter { it.third.isBlank() }
            if (emptyOnes.isNotEmpty()) {
                vm.aiRewriteAllFields(emptyOnes, refAnalysis, allFields)
            }
        }
    }

    // 监听AI重写结果——使用 activeFieldKey 而非 currentStep.fieldKey，
    // 避免用户切换步骤后结果写入错误字段
    LaunchedEffect(fieldResult) {
        val result = fieldResult ?: return@LaunchedEffect
        when (activeFieldKey) {
            "title" -> onUpdateTitle(result)
            "genre" -> onUpdateGenre(result)
            "theme" -> onUpdateTheme(result)
            "world" -> onUpdateWorld(result)
            "characters" -> onUpdateChars(result)
            "narrative" -> onUpdateNarrative(result)
            "style" -> onUpdateStyle(result)
        }
        vm.clearFieldRewriteResult()
    }

    val steps = EditStep.entries
    val stepIndex = steps.indexOf(currentStep)
    val isLoading = fieldLoading.isNotBlank()

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                if (stepIndex > 0) currentStep = steps[stepIndex - 1]
                else onCancel()
            }) {
                Text("← ${if (stepIndex > 0) "上一步" else "返回"}", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("修改创作设定", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("${stepIndex + 1}/${steps.size}", fontSize = 12.sp, color = TextTertiary)
        }
        InkDividerLight()

        // 步骤进度条
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            steps.forEachIndexed { i, step ->
                val isActive = i <= stepIndex
                val isCurrent = i == stepIndex
                Box(
                    Modifier.weight(1f).height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                isCurrent -> Accent
                                isActive -> Accent.copy(alpha = 0.4f)
                                else -> Border
                            }
                        )
                )
            }
        }

        // 空字段提醒 + 一键AI重写按钮
        if (emptyFields.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Info.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚠️", fontSize = 12.sp)
                    Text(
                        "待填充：${emptyFields.joinToString("、") { fieldLabelMap[it] ?: it }}",
                        fontSize = 11.sp, color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { vm.aiRewriteAllFields(batchRewriteFields, refAnalysis, allFields) },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("一键AI重写", fontSize = 11.sp, color = Accent)
                    }
                }
            }
        } else if (batchRewriteFields.any { it.third.isNotEmpty() }) {
            // 所有字段都有值，但仍提供一键重写按钮
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.06f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✅", fontSize = 12.sp)
                    Text("所有字段已填写", fontSize = 11.sp, color = Success, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.aiRewriteAllFields(batchRewriteFields, refAnalysis, allFields) },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("一键AI重写", fontSize = 11.sp, color = Accent)
                    }
                }
            }
        }

        // 步骤内容
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (currentStep) {
                EditStep.TITLE -> {
                    StepTitleCard("书名与题材", "确定作品名称和题材分类")
                    StepEditCard(
                        label = "书名",
                        value = editTitle,
                        onValueChange = onUpdateTitle,
                        placeholder = refTitle.ifBlank { "请输入书名" },
                        singleLine = true
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "title",
                        onClick = { vm.aiRewriteField("title", "书名", editTitle, refAnalysis, allFields) },
                        label = "AI 重写书名"
                    )
                    StepEditCard(
                        label = "题材",
                        value = editGenre,
                        onValueChange = onUpdateGenre,
                        placeholder = refGenre.ifBlank { "如：玄幻、科幻、都市" },
                        singleLine = true
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "genre",
                        onClick = { vm.aiRewriteField("genre", "题材", editGenre, refAnalysis, allFields) },
                        label = "AI 重写题材"
                    )
                }
                EditStep.THEME -> {
                    StepTitleCard("核心主题", "明确故事想表达的核心思想")
                    StepEditCard(
                        label = "核心主题",
                        value = editTheme,
                        onValueChange = onUpdateTheme,
                        placeholder = refTheme.ifBlank { "请输入核心主题" },
                        minLines = 2
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "theme",
                        onClick = { vm.aiRewriteField("theme", "核心主题", editTheme, refAnalysis, allFields) },
                        label = "AI 重写主题"
                    )
                }
                EditStep.WORLD -> {
                    StepTitleCard("世界观设定", "构建故事的时空背景和规则体系")
                    StepEditCard(
                        label = "世界观设定",
                        value = editWorld,
                        onValueChange = onUpdateWorld,
                        placeholder = refWorld.ifBlank { "请输入世界观设定" },
                        minLines = 4
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "world",
                        onClick = { vm.aiRewriteField("world", "世界观设定", editWorld, refAnalysis, allFields) },
                        label = "AI 重写世界观"
                    )
                }
                EditStep.CHARACTERS -> {
                    StepTitleCard("角色原型", "塑造角色形象、性格和关系")
                    StepEditCard(
                        label = "角色原型（可修改角色名和设定）",
                        value = editChars,
                        onValueChange = onUpdateChars,
                        placeholder = refCharacters.ifBlank { "请输入角色设定" },
                        minLines = 5
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "characters",
                        onClick = { vm.aiRewriteField("characters", "角色原型", editChars, refAnalysis, allFields) },
                        label = "AI 重写角色"
                    )
                }
                EditStep.NARRATIVE -> {
                    StepTitleCard("叙事结构", "设计故事推进方式和节奏")
                    StepEditCard(
                        label = "叙事结构",
                        value = editNarrative,
                        onValueChange = onUpdateNarrative,
                        placeholder = refNarrative.ifBlank { "请输入叙事结构" },
                        minLines = 3
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "narrative",
                        onClick = { vm.aiRewriteField("narrative", "叙事结构", editNarrative, refAnalysis, allFields) },
                        label = "AI 重写叙事结构"
                    )
                }
                EditStep.STYLE -> {
                    StepTitleCard("语言风格", "确定文字风格、描写密度和对话占比")
                    StepEditCard(
                        label = "语言风格",
                        value = editStyle,
                        onValueChange = onUpdateStyle,
                        placeholder = refStyle.ifBlank { "请输入语言风格" },
                        minLines = 3
                    )
                    StepAiButton(
                        isLoading = isLoading && fieldLoading == "style",
                        onClick = { vm.aiRewriteField("style", "语言风格", editStyle, refAnalysis, allFields) },
                        label = "AI 重写语言风格"
                    )
                }
                EditStep.CONFIRM -> {
                    StepTitleCard("确认创建", "检查所有设定，确认无误后开始创作")
                    // 汇总预览
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = BgCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewRow("书名", editTitle.ifBlank { refTitle }.ifBlank { "未命名" })
                            PreviewRow("题材", editGenre.ifBlank { refGenre }.ifBlank { "玄幻" })
                            PreviewRow("核心主题", editTheme.ifBlank { refTheme }.ifBlank { "未设定" })
                            PreviewRow("世界观", editWorld.ifBlank { refWorld }.ifBlank { "未设定" })
                            PreviewRow("角色", editChars.ifBlank { refCharacters }.ifBlank { "未设定" })
                            PreviewRow("叙事结构", editNarrative.ifBlank { refNarrative }.ifBlank { "未设定" })
                            PreviewRow("语言风格", editStyle.ifBlank { refStyle }.ifBlank { "未设定" })
                        }
                    }
                }
            }

            // 导航按钮
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stepIndex > 0) {
                    OutlinedButton(
                        onClick = { currentStep = steps[stepIndex - 1] },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("上一步", fontSize = 13.sp)
                    }
                }
                if (currentStep == EditStep.CONFIRM) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("确认，开始创作", fontSize = 13.sp, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = { currentStep = steps[stepIndex + 1] },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("下一步", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepTitleCard(title: String, description: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AccentBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.W600, color = Accent)
            Text(description, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StepEditCard(
    label: String, value: String, onValueChange: (String) -> Unit,
    placeholder: String, minLines: Int = 2, singleLine: Boolean = false
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.W600, color = Accent)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = if (singleLine) 1 else minLines,
                maxLines = if (singleLine) 1 else 10,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 18.sp, color = TextPrimary),
                placeholder = { Text(placeholder, fontSize = 12.sp, color = TextTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(alpha = 0.4f),
                    unfocusedBorderColor = Border,
                    focusedContainerColor = BgCard,
                    unfocusedContainerColor = BgCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
private fun StepAiButton(isLoading: Boolean, onClick: () -> Unit, label: String) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLoading) Accent.copy(alpha = 0.3f) else Accent.copy(alpha = 0.1f),
            contentColor = Accent
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent
            )
            Spacer(Modifier.width(8.dp))
            Text("AI 重写中...", fontSize = 13.sp)
        } else {
            Text("🤖 $label", fontSize = 13.sp)
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("$label：", fontSize = 11.sp, fontWeight = FontWeight.W600,
            color = Accent, modifier = Modifier.width(70.dp))
        Text(value.take(200), fontSize = 11.sp, color = TextSecondary,
            lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}

// 保留旧的 EditCard 供其他用途（如有需要）
@Composable
private fun EditCard(label: String, value: String, onValueChange: (String) -> Unit, minLines: Int = 2) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.W600, color = Accent)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = minLines,
                maxLines = 10,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 18.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(alpha = 0.4f),
                    unfocusedBorderColor = Border
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

// ============================================================
//  阶段3：写作工作台（参考面板 + 写作区）
// ============================================================
@Composable
private fun WritingWorkbenchScreen(
    bookId: String,
    book: BookEntity?,
    vm: MainViewModel,
    refAnalysis: String,
    refTitle: String,
    refGenre: String,
    refTheme: String,
    refWorld: String,
    refCharacters: String,
    refNarrative: String,
    refStyle: String,
    onBack: () -> Unit
) {
    val storyConfig by vm.storyConfig.collectAsState()
    val currentChapterNum by vm.currentChapterNum.collectAsState()
    val currentContent by vm.currentContent.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val chapters by vm.chapters.collectAsState()

    var chapterNum by remember { mutableIntStateOf(currentChapterNum.coerceAtLeast(1)) }
    var chapterTitle by remember { mutableStateOf("") }
    var chapterNumText by remember { mutableStateOf(chapterNum.toString()) }

    // 同步 ViewModel 章节号变化
    LaunchedEffect(currentChapterNum) {
        chapterNum = currentChapterNum.coerceAtLeast(1)
        chapterNumText = chapterNum.toString()
    }

    // 切换章节号时自动从数据库加载章节标题
    LaunchedEffect(chapterNum, chapters) {
        val title = chapters.find { it.chapterNumber == chapterNum }?.title ?: ""
        chapterTitle = title
    }

    // 参考面板展开状态
    var refPanelExpanded by remember { mutableStateOf(true) }
    var activeRefTab by remember { mutableIntStateOf(0) }
    val refTabs = listOf("概览", "角色", "风格")

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("写作工作台", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            // 参考面板切换
            TextButton(onClick = { refPanelExpanded = !refPanelExpanded }) {
                Text(if (refPanelExpanded) "收起参考" else "展开参考",
                    fontSize = 12.sp, color = Accent)
            }
        }
        InkDividerLight()

        if (refPanelExpanded) {
            // ===== 参考面板 =====
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BgSidebar.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // 参考标题
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("📖 参考：${refTitle.ifBlank { "未命名" }}",
                                fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(refGenre.ifBlank { "" }, fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.weight(1f))
                        if (book != null) {
                            Text("《${book.title}》", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tab切换
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        refTabs.forEachIndexed { index, tab ->
                            val selected = index == activeRefTab
                            Surface(
                                Modifier.clickable { activeRefTab = index },
                                shape = RoundedCornerShape(6.dp),
                                color = if (selected) Accent.copy(alpha = 0.15f) else Color.Transparent
                            ) {
                                Text(tab, fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
                                    color = if (selected) Accent else TextTertiary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tab内容
                    when (activeRefTab) {
                        0 -> { // 概览
                            Text(refTheme.ifBlank { "未提取到主题" }, fontSize = 11.sp,
                                color = TextSecondary, lineHeight = 16.sp, maxLines = 3,
                                overflow = TextOverflow.Ellipsis)
                            if (refWorld.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("世界观：${refWorld.take(80)}", fontSize = 10.sp,
                                    color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (refNarrative.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("叙事：${refNarrative.take(80)}", fontSize = 10.sp,
                                    color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        1 -> { // 角色
                            if (refCharacters.isNotBlank()) {
                                Text(refCharacters.take(200), fontSize = 11.sp,
                                    color = TextSecondary, lineHeight = 16.sp, maxLines = 4,
                                    overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("未提取到角色信息", fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                        2 -> { // 风格
                            if (refStyle.isNotBlank()) {
                                Text(refStyle.take(150), fontSize = 11.sp,
                                    color = TextSecondary, lineHeight = 16.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis)
                            } else {
                                Text("未提取到风格信息", fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }

        // ===== 写作区 =====
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 章节设置卡片
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Success.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("✍️ 章节写作", fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Success, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("第${chapterNum}章", fontSize = 12.sp,
                            fontWeight = FontWeight.W600, color = TextPrimary)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("章节：", fontSize = 12.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = chapterNumText,
                            onValueChange = {
                                chapterNumText = it
                                val v = it.toIntOrNull()
                                if (v != null && v >= 1) chapterNum = v
                            },
                            singleLine = true,
                            modifier = Modifier.width(70.dp).height(40.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                focusedContainerColor = BgCard,
                                unfocusedContainerColor = BgCard,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Accent
                            )
                        )
                        Text("标题：", fontSize = 12.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = chapterTitle,
                            onValueChange = { chapterTitle = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).height(40.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary),
                            placeholder = { Text("可选", fontSize = 11.sp, color = TextTertiary) },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = Border,
                                focusedContainerColor = BgCard,
                                unfocusedContainerColor = BgCard,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = Accent
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("字数：", fontSize = 12.sp, color = TextSecondary)
                        Text("由AI自动控制", fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }

            // 已有章节
            if (chapters.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("已写章节（${chapters.size}）", fontSize = 12.sp,
                            fontWeight = FontWeight.W600, color = TextPrimary)
                        chapters.sortedBy { it.chapterNumber }.takeLast(5).forEach { ch ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BgSidebar.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${ch.chapterNumber}", fontSize = 10.sp,
                                        fontWeight = FontWeight.W600, color = Accent)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (ch.title.isNotBlank()) "第${ch.chapterNumber}章 ${ch.title}"
                                    else "第${ch.chapterNumber}章",
                                    fontSize = 11.sp, color = TextSecondary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${ch.wordCount}字", fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }

            // 生成内容
            if (currentContent.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = BgCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("第${chapterNum}章 内容", fontSize = 12.sp,
                                fontWeight = FontWeight.W600, color = TextPrimary)
                            Spacer(Modifier.weight(1f))
                            Text("${currentContent.length}字", fontSize = 11.sp, color = TextTertiary)
                        }
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                        Text(currentContent, fontSize = 12.sp, color = TextSecondary,
                            lineHeight = 20.sp)
                    }
                }
            }

            // 生成中
            if (isGenerating) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏳ $stepLoadingMessage", fontSize = 12.sp, color = TextSecondary)
                        InkProgressBar(stepProgress * 100, Modifier.fillMaxWidth())
                    }
                }
            }

            // 操作按钮
            // 构建完整的 StoryConfig，将参考分析中的世界观、角色、叙事、风格填入对应字段
            val effectiveConfig = remember(storyConfig, refWorld, refTheme, refCharacters, refNarrative, refStyle) {
                storyConfig.copy(
                    coreSetting = storyConfig.coreSetting.ifBlank {
                        buildString {
                            if (refWorld.isNotBlank()) append(refWorld)
                            if (refTheme.isNotBlank()) {
                                if (refWorld.isNotBlank()) append("\n")
                                append("主题：$refTheme")
                            }
                            if (refNarrative.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append("叙事：$refNarrative")
                            }
                            if (refStyle.isNotBlank()) {
                                if (isNotEmpty()) append("\n")
                                append("风格：$refStyle")
                            }
                        }
                    },
                    characters = storyConfig.characters.ifBlank { refCharacters }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        vm.aiWriteContent(
                            effectiveConfig,
                            chapterNum,
                            chapterTitle
                        )
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text(
                        if (isGenerating) "生成中..." else "AI 写作",
                        fontSize = 13.sp, color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = {
                        vm.aiBatchWrite(
                            effectiveConfig,
                            3
                        )
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("批量写3章", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ===== 工具函数统一使用 TextParser =====