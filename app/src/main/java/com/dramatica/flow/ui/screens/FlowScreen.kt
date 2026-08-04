package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.*
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun FlowScreen(
    bookId: String, book: BookEntity?,
    characters: List<CharacterEntity>, hooks: List<HookEntity>,
    causalChainList: List<CausalLinkEntity>, emotions: List<EmotionEntity>,
    vm: MainViewModel
) {
    val currentStep by vm.currentStep.collectAsState()
    val storyConfig by vm.storyConfig.collectAsState()
    val outline by vm.outline.collectAsState()
    var selectedStep by remember { mutableStateOf<DramaticaStep?>(null) }

    // 伏笔添加对话框
    var showAddHookDialog by remember { mutableStateOf(false) }
    var newHookDesc by remember { mutableStateOf("") }
    var newHookChapter by remember { mutableStateOf("") }

    // 因果链添加对话框
    var showAddCausalDialog by remember { mutableStateOf(false) }
    var newCausalChapter by remember { mutableStateOf("") }
    var newCausalCause by remember { mutableStateOf("") }
    var newCausalEvent by remember { mutableStateOf("") }
    var newCausalConsequence by remember { mutableStateOf("") }

    // 同步selectedStep与currentStep
    LaunchedEffect(currentStep) {
        if (selectedStep != currentStep) {
            selectedStep = currentStep
        }
    }

    // 如果选择了步骤，显示步骤详情
    selectedStep?.let { step ->
        key(step) {
            FlowStepDetailScreen(
                step = step, bookId = bookId, book = book,
                characters = characters, hooks = hooks,
                causalChain = causalChainList, emotions = emotions,
                vm = vm,
                onBack = { selectedStep = null }
            )
        }
        return
    }

    val steps = listOf(
        DramaticaStep.BASIC_INFO to "基础信息",
        DramaticaStep.WORLD_BUILDING to "世界观构建",
        DramaticaStep.CHARACTER_DESIGN to "角色设计",
        DramaticaStep.OUTLINE to "大纲规划",
        DramaticaStep.WRITING to "章节创作",
        DramaticaStep.AI_RESULT to "AI创作结果",
        DramaticaStep.TIMELINE to "完稿审校"
    )

    val completedSteps = remember(storyConfig) {
        mutableSetOf<DramaticaStep>().apply {
            if (storyConfig.title.isNotBlank()) add(DramaticaStep.BASIC_INFO)
            if (storyConfig.coreSetting.isNotBlank()) add(DramaticaStep.WORLD_BUILDING)
            if (storyConfig.characters.isNotBlank()) add(DramaticaStep.CHARACTER_DESIGN)
            if (storyConfig.outline.isNotBlank()) add(DramaticaStep.OUTLINE)
        }
    }

    // 步骤内容预览
    val stepPreview: Map<DramaticaStep, String> = remember(storyConfig, outline) {
        mapOf(
            DramaticaStep.WORLD_BUILDING to storyConfig.coreSetting.take(80).toString(),
            DramaticaStep.CHARACTER_DESIGN to (storyConfig.characters.lines().firstOrNull()?.take(60) ?: "").toString(),
            DramaticaStep.OUTLINE to (outline.lines().firstOrNull()?.take(60) ?: "").toString()
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 顶部横幅
        if (book != null) {
            BookBanner(
                name = book.title, genre = book.genre,
                chapters = book.currentChapter, words = book.currentChapter * book.targetWords,
                color = Accent, onClick = {}
            )
        } else {
            Column(Modifier.padding(16.dp, 20.dp)) {
                Text("创作中心", fontFamily = SerifFamily, fontWeight = FontWeight.W700, fontSize = 22.sp)
                Text("Dramatica 7步创作流程", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 3.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 步骤进度条
        val currentStepIdx = steps.indexOfFirst { it.first == currentStep }
        StepProgressBar(currentStep = currentStepIdx, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))

        // 整体进度概览
        val completedCount = completedSteps.size
        val totalSteps = steps.size
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$completedCount / $totalSteps 步已完成", fontSize = 11.sp, color = TextTertiary)
            Text("${(completedCount * 100 / totalSteps)}%", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.W600)
        }

        Spacer(Modifier.height(12.dp))

        // 步骤卡片
        steps.forEach { (step, title) ->
            val isCompleted = step in completedSteps
            val isCurrent = step == currentStep
            val isAutoStep = step.number in 2..4  // 步骤2-4为AI自动生成步骤
            val preview = stepPreview[step]

            val cardBg = when { isCurrent -> AccentBg; isCompleted -> Color(0xFFF5FAF5); else -> BgCard }
            val circleBg = when { isCompleted -> Success; isCurrent -> Accent; else -> Color.Transparent }
            val circleBorder = if (!isCompleted && !isCurrent) Border else null
            val circleTextColor = if (isCompleted || isCurrent) Color.White else TextTertiary
            val statusText = when {
                isCompleted -> "✓ 已完成"
                isCurrent -> "进行中"
                else -> if (isAutoStep) "AI自动生成" else "待开始"
            }
            val statusColor = when { isCompleted -> Success; isCurrent -> Accent; else -> TextTertiary }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(InkRadius.sm)).background(cardBg)
                .clickable { selectedStep = step }
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // 左侧状态条
                Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp))
                    .background(when { isCompleted -> Success; isCurrent -> Accent; else -> Border }))
                Spacer(Modifier.width(12.dp))
                // 圆形编号
                Box(Modifier.size(30.dp).clip(CircleShape)
                    .then(if (isCompleted || isCurrent) Modifier.background(circleBg) else Modifier.background(circleBg).clip(CircleShape)),
                    contentAlignment = Alignment.Center) {
                    if (isCompleted) Text("✓", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.W600)
                    else Text("${step.number}", fontSize = 12.sp, fontWeight = FontWeight.W600, color = circleTextColor)
                }
                Spacer(Modifier.width(12.dp))
                // 标题和状态
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = SerifFamily, fontWeight = FontWeight.W500, fontSize = 14.sp,
                        color = if (isCurrent) Accent else TextPrimary)
                    Text(statusText, fontSize = 10.sp, color = statusColor)
                    // 内容预览
                    if (isCompleted && preview != null && preview.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(preview, fontSize = 10.sp, color = TextTertiary, maxLines = 1)
                    }
                }
                // 快捷操作
                if (isCurrent) {
                    Text("→", fontSize = 16.sp, color = Accent, fontWeight = FontWeight.W600)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 伏笔
        SectionHeader(title = "伏笔（${hooks.size}）", actionText = "添加", onAction = { showAddHookDialog = true })
        Spacer(Modifier.height(8.dp))
        if (hooks.isEmpty()) {
            Text("暂无伏笔，AI创作章节时会自动生成", fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            hooks.forEach { hook ->
                InkCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dotColor = when (hook.status) { "open" -> Accent; "resolved" -> Success; else -> Danger }
                        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                        Text(hook.description, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text("Ch.${hook.plantedChapter}", fontSize = 10.sp, color = TextTertiary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 因果链
        SectionHeader(title = "因果链（${causalChainList.size}）", actionText = "添加", onAction = { showAddCausalDialog = true })
        Spacer(Modifier.height(8.dp))
        if (causalChainList.isEmpty()) {
            Text("暂无因果链，AI创作章节时会自动生成", fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            causalChainList.takeLast(8).forEach { link ->
                InkCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Ch.${link.chapter}", fontSize = 10.sp, fontWeight = FontWeight.W600, color = Accent)
                        Text("因：${link.cause}", fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp))
                        Text("事：${link.event}", fontSize = 12.sp, color = TextSecondary)
                        Text("果：${link.consequence}", fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // 添加伏笔对话框
    if (showAddHookDialog) {
        AlertDialog(
            onDismissRequest = { showAddHookDialog = false },
            title = { Text("添加伏笔", fontWeight = FontWeight.W600) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newHookDesc,
                        onValueChange = { newHookDesc = it },
                        label = { Text("伏笔描述") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newHookChapter,
                        onValueChange = { newHookChapter = it.filter { c -> c.isDigit() } },
                        label = { Text("埋设章节") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chapter = newHookChapter.toIntOrNull() ?: 1
                    if (newHookDesc.isNotBlank()) {
                        vm.addHook(bookId, newHookDesc, chapter)
                        newHookDesc = ""
                        newHookChapter = ""
                        showAddHookDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newHookDesc = ""
                    newHookChapter = ""
                    showAddHookDialog = false
                }) { Text("取消") }
            }
        )
    }

    // 添加因果链对话框
    if (showAddCausalDialog) {
        AlertDialog(
            onDismissRequest = { showAddCausalDialog = false },
            title = { Text("添加因果链", fontWeight = FontWeight.W600) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newCausalChapter,
                        onValueChange = { newCausalChapter = it.filter { c -> c.isDigit() } },
                        label = { Text("章节") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCausalCause,
                        onValueChange = { newCausalCause = it },
                        label = { Text("因") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCausalEvent,
                        onValueChange = { newCausalEvent = it },
                        label = { Text("事") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCausalConsequence,
                        onValueChange = { newCausalConsequence = it },
                        label = { Text("果") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chapter = newCausalChapter.toIntOrNull() ?: 1
                    if (newCausalCause.isNotBlank() && newCausalEvent.isNotBlank()) {
                        vm.addCausalLink(bookId, chapter, newCausalCause, newCausalEvent, newCausalConsequence)
                        newCausalChapter = ""
                        newCausalCause = ""
                        newCausalEvent = ""
                        newCausalConsequence = ""
                        showAddCausalDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newCausalChapter = ""
                    newCausalCause = ""
                    newCausalEvent = ""
                    newCausalConsequence = ""
                    showAddCausalDialog = false
                }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(InkRadius.md),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(content = content) }
}

@Composable
private fun StatChip(label: String, completed: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (completed) color else Border))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = if (completed) TextSecondary else TextTertiary)
    }
}
