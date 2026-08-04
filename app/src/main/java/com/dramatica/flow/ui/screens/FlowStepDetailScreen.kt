package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.*
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

@Composable
fun FlowStepDetailScreen(
    step: DramaticaStep,
    bookId: String,
    book: BookEntity?,
    characters: List<CharacterEntity>,
    hooks: List<HookEntity>,
    causalChain: List<CausalLinkEntity>,
    emotions: List<EmotionEntity>,
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val storyConfig by vm.storyConfig.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val outline by vm.outline.collectAsState()
    val summaryHistory by vm.summaryHistory.collectAsState()

    when (step) {
        DramaticaStep.BASIC_INFO -> Step1BasicInfo(storyConfig, vm, onBack)
        DramaticaStep.WORLD_BUILDING -> Step2WorldBuilding(storyConfig, uiState, vm, onBack)
        DramaticaStep.CHARACTER_DESIGN -> Step3CharacterDesign(storyConfig, characters, uiState, vm, onBack)
        DramaticaStep.OUTLINE -> Step4Outline(storyConfig, outline, uiState, vm, onBack)
        DramaticaStep.WRITING -> Step5Writing(bookId, storyConfig, causalChain, hooks, summaryHistory, vm, onBack)
        DramaticaStep.AI_RESULT -> Step6AIResult(bookId, storyConfig, causalChain, hooks, summaryHistory, vm, onBack)
        DramaticaStep.TIMELINE -> Step7Timeline(causalChain, hooks, emotions, summaryHistory, vm, onBack)
    }
}

@Composable
private fun Step1BasicInfo(config: StoryConfig, vm: MainViewModel, onBack: () -> Unit) {
    var title by remember(config.title) { mutableStateOf(config.title) }
    var genre by remember(config.genre) { mutableStateOf(config.genre) }
    var briefIdea by remember(config.briefIdea) { mutableStateOf(config.briefIdea) }
    var targetChapters by remember(config.targetChapters) { mutableStateOf(config.targetChapters.toString()) }
    val genres = listOf("玄幻", "科幻", "都市", "悬疑", "言情", "奇幻", "武侠", "历史")

    Column(Modifier.fillMaxSize()) {
        StepTopBar("基础信息", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 0, modifier = Modifier.padding(bottom = 20.dp))

            Text("基础信息", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("填写小说的基本信息，AI 将据此生成世界观和角色",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("作品信息", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 10.dp))

                    Label("小说标题 *")
                    GlassInput(value = title, onValueChange = { title = it }, placeholder = "请输入书名")

                    Spacer(Modifier.height(12.dp))
                    Label("小说类型")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        genres.forEach { g ->
                            FilterChip(selected = genre == g, onClick = { genre = g },
                                label = { Text(g, fontSize = 12.sp) })
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Label("简要想法（可选，AI会根据这个生成世界观和角色）")
                    GlassInput(value = briefIdea, onValueChange = { briefIdea = it },
                        placeholder = "描述你的故事核心创意...", height = 100.dp)

                    Spacer(Modifier.height(12.dp))
                    Label("目标章节数（可选）")
                    GlassInput(value = targetChapters, onValueChange = { targetChapters = it.filter { c -> c.isDigit() } },
                        placeholder = "30", keyboardType = KeyboardType.Number)
                }
            }

            Spacer(Modifier.height(12.dp))
            InkButton(text = "开始生成", onClick = {
                vm.updateStoryConfig(StoryConfig(
                    title = title, genre = genre, briefIdea = briefIdea,
                    targetChapters = targetChapters.toIntOrNull() ?: 30
                ))
                vm.nextStep()
            }, modifier = Modifier.fillMaxWidth(), enabled = title.isNotBlank())
        }
    }
}

@Composable
private fun Step2WorldBuilding(config: StoryConfig, uiState: DramaticaUiState, vm: MainViewModel, onBack: () -> Unit) {
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val generationError by vm.generationError.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 不自动触发生成，由用户手动点击"开始生成"
    Column(Modifier.fillMaxSize()) {
        StepTopBar("世界观构建", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 1, modifier = Modifier.padding(bottom = 20.dp))

            Text("世界观构建", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("AI 将根据你的基础信息自动构建完整的世界观设定",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (config.coreSetting.isNotBlank() && !isGenerating) {
                            Text("✅ 世界观已生成", color = Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("世界观设定", fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Accent, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isGenerating) {
                        // 生成中
                        LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text(stepLoadingMessage, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${(stepProgress * 100).toInt()}%", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.cancelGeneration() }, modifier = Modifier.weight(1f)) {
                                Text("停止生成", fontSize = 12.sp, color = Danger)
                            }
                        }
                    } else if (config.coreSetting.isNotBlank()) {
                        // 已生成完成
                        var editingContent by remember { mutableStateOf(config.coreSetting) }
                        OutlinedTextField(value = editingContent, onValueChange = { editingContent = it; vm.updateCoreSetting(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                    } else if (generationError != null) {
                        // 生成失败
                        Column(Modifier.fillMaxWidth()) {
                            Text("生成失败", color = Danger, fontWeight = FontWeight.W500, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(generationError ?: "", color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.generateWorldBuilding() }, modifier = Modifier.weight(1f)) {
                                    Text("重试生成", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                    Text("跳过此步骤", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        // 未生成，显示开始按钮
                        Text("AI将根据你的基础信息自动构建世界观设定，包括：", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("• 世界名称和背景", fontSize = 12.sp, color = TextTertiary)
                        Text("• 核心规则/力量体系", fontSize = 12.sp, color = TextTertiary)
                        Text("• 关键地点与社会结构", fontSize = 12.sp, color = TextTertiary)
                        Text("• 历史背景", fontSize = 12.sp, color = TextTertiary)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InkButton(text = "开始生成", onClick = { vm.generateWorldBuilding() },
                                modifier = Modifier.weight(1f), enabled = !isGenerating)
                            OutlinedButton(onClick = { vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                Text("跳过此步骤", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (config.coreSetting.isNotBlank() && !isGenerating && generationError == null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.generateWorldBuilding() }, modifier = Modifier.weight(1f)) { Text("重新生成", fontSize = 12.sp) }
                    OutlinedButton(onClick = { showImportDialog = true; importText = "" }, modifier = Modifier.weight(1f)) { Text("导入世界观", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("worldbuilding", config.coreSetting))
                        Toast.makeText(context, "世界观已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("导出世界观", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("上一步", fontSize = 12.sp) }
                    InkButton(text = "确认并继续 →", onClick = { vm.nextStep() }, modifier = Modifier.weight(1.5f))
                }
            }
        }
    }

    // 导入世界观对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入世界观", fontWeight = FontWeight.W600) },
            text = {
                Column {
                    Text("请粘贴世界观文本：", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.isNotBlank()) {
                        vm.updateCoreSetting(importText)
                        showImportDialog = false
                    }
                }) { Text("确定导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun Step3CharacterDesign(config: StoryConfig, characters: List<CharacterEntity>, uiState: DramaticaUiState, vm: MainViewModel, onBack: () -> Unit) {
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val generationError by vm.generationError.collectAsState()
    val context = LocalContext.current
    var showManageDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        StepTopBar("角色设计", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 2, modifier = Modifier.padding(bottom = 20.dp))

            Text("角色设计", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("基于 Dramatica 叙事理论，AI 将设计 7 种核心角色职能",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if ((config.characters.isNotBlank() || characters.isNotEmpty()) && !isGenerating) {
                            Text("✅ 角色已生成", color = Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("角色设定", fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Accent, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isGenerating) {
                        LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text(stepLoadingMessage, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${(stepProgress * 100).toInt()}%", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.cancelGeneration() }, modifier = Modifier.fillMaxWidth()) {
                            Text("停止生成", fontSize = 12.sp, color = Danger)
                        }
                    } else if (characters.isNotEmpty()) {
                        characters.forEach { ch ->
                            val typeColor = when(ch.type) {
                                        "protagonist" -> ProtagonistColor
                                        "antagonist" -> AntagonistColor
                                        "sidekick" -> SidekickColor
                                        "impact" -> ImpactColor
                                        "guardian" -> GuardianColor
                                        "contagonist" -> ContagonistColor
                                        "skeptic" -> SkepticColor
                                        else -> TextTertiary
                                    }
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(typeColor), contentAlignment = Alignment.Center) {
                                    Text(ch.avatar, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W600)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(ch.name, fontWeight = FontWeight.W600, fontSize = 14.sp); Text(ch.role, fontSize = 11.sp, color = TextTertiary) }
                            }
                        }
                    } else if (config.characters.isNotBlank()) {
                        Text(config.characters.take(300), fontSize = 12.sp, color = TextSecondary)
                    } else if (generationError != null) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("生成失败", color = Danger, fontWeight = FontWeight.W500, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(generationError ?: "", color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.generateCharacters() }, modifier = Modifier.weight(1f)) {
                                    Text("重试生成", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                    Text("跳过此步骤", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Text("AI将根据世界观设定，基于 Dramatica 叙事理论设计角色：", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("• 主角 — 推动故事前进的核心力量", fontSize = 12.sp, color = TextTertiary)
                        Text("• 反派 — 与主角目标对立的对抗者", fontSize = 12.sp, color = TextTertiary)
                        Text("• 冲击者 — 改变主角认知的关键人物", fontSize = 12.sp, color = TextTertiary)
                        Text("• 守护者 — 导师/引导者", fontSize = 12.sp, color = TextTertiary)
                        Text("• 阻碍者 — 表面帮助实则拖延", fontSize = 12.sp, color = TextTertiary)
                        Text("• 伙伴 — 忠诚的支持者", fontSize = 12.sp, color = TextTertiary)
                        Text("• 怀疑者 — 质疑与反面声音", fontSize = 12.sp, color = TextTertiary)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InkButton(text = "开始生成", onClick = { vm.generateCharacters() },
                                modifier = Modifier.weight(1f), enabled = !isGenerating)
                            OutlinedButton(onClick = { vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                Text("跳过此步骤", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if ((config.characters.isNotBlank() || characters.isNotEmpty()) && !isGenerating && generationError == null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showManageDialog = true }, modifier = Modifier.weight(1f)) { Text("管理角色", fontSize = 12.sp) }
                    OutlinedButton(onClick = { vm.generateCharacters() }, modifier = Modifier.weight(1f)) { Text("重新生成", fontSize = 12.sp) }
                    OutlinedButton(onClick = { vm.nextStep() }, modifier = Modifier.weight(1f)) { Text("确认继续", fontSize = 12.sp) }
                }
            }
        }
    }

    // 角色管理对话框
    if (showManageDialog) {
        var newName by remember { mutableStateOf("") }
        var newRole by remember { mutableStateOf("protagonist") }
        var newDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("管理角色", fontWeight = FontWeight.W600) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    characters.forEach { ch ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${ch.name}（${ch.role}）", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                val currentBookId = vm.currentBookId.value
                                vm.updateCharacter(ch.uid, currentBookId, ch.name, ch.role, ch.type, ch.description)
                                Toast.makeText(context, "角色已更新", Toast.LENGTH_SHORT).show()
                            }, contentPadding = PaddingValues(4.dp), modifier = Modifier.height(28.dp)) {
                                Text("更新", fontSize = 10.sp, color = Accent)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    InkDividerLight()
                    Spacer(Modifier.height(12.dp))
                    Text("添加新角色", fontWeight = FontWeight.W500, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("角色类型", fontSize = 11.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("protagonist" to "主角", "antagonist" to "反派", "sidekick" to "伙伴",
                            "impact" to "冲击者", "guardian" to "守护者", "contagonist" to "阻碍者", "skeptic" to "怀疑者").forEach { (type, label) ->
                            FilterChip(selected = newRole == type, onClick = { newRole = type }, label = { Text(label, fontSize = 10.sp) })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = newDesc, onValueChange = { newDesc = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val currentBookId = vm.currentBookId.value
                        vm.addCharacter(currentBookId, newName, newRole, newRole, newDesc)
                        newName = ""; newDesc = ""
                    }
                    showManageDialog = false
                }) { Text("添加角色", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showManageDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun Step4Outline(config: StoryConfig, outlineText: String, uiState: DramaticaUiState, vm: MainViewModel, onBack: () -> Unit) {
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val generationError by vm.generationError.collectAsState()
    var showImportOutlineDialog by remember { mutableStateOf(false) }
    var importOutlineText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        StepTopBar("大纲规划", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 3, modifier = Modifier.padding(bottom = 20.dp))

            Text("大纲规划", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("AI 将根据世界观和角色生成 5 卷结构的分卷大纲",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (outlineText.isNotBlank() && !isGenerating) {
                            Text("✅ 大纲已生成", color = Success, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("大纲规划", fontSize = 11.sp, fontWeight = FontWeight.W600,
                                color = Accent, letterSpacing = 1.5.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isGenerating) {
                        LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(8.dp))
                        Text(stepLoadingMessage, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${(stepProgress * 100).toInt()}%", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.cancelGeneration() }, modifier = Modifier.fillMaxWidth()) {
                            Text("停止生成", fontSize = 12.sp, color = Danger)
                        }
                    } else if (outlineText.isNotBlank()) {
                        OutlinedTextField(value = outlineText, onValueChange = { vm.updateOutline(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                    } else if (generationError != null) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("生成失败", color = Danger, fontWeight = FontWeight.W500, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(generationError ?: "", color = TextSecondary, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.generateOutline() }, modifier = Modifier.weight(1f)) {
                                    Text("重试生成", fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { vm.clearGenerationError(); vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                    Text("跳过此步骤", fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        Text("AI将根据世界观和角色设定生成分卷大纲，包括：", fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Text("• 5卷结构划分", fontSize = 12.sp, color = TextTertiary)
                        Text("• 每卷关键情节点", fontSize = 12.sp, color = TextTertiary)
                        Text("• 章节自动分配", fontSize = 12.sp, color = TextTertiary)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InkButton(text = "开始生成", onClick = { vm.generateOutline() },
                                modifier = Modifier.weight(1f), enabled = !isGenerating)
                            OutlinedButton(onClick = { vm.skipCurrentStep() }, modifier = Modifier.weight(1f)) {
                                Text("跳过此步骤", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (outlineText.isNotBlank() && !isGenerating && generationError == null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.generateOutline() }, modifier = Modifier.weight(1f)) { Text("重新生成", fontSize = 12.sp) }
                    OutlinedButton(onClick = { showImportOutlineDialog = true; importOutlineText = "" }, modifier = Modifier.weight(1f)) { Text("导入大纲", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("outline", outlineText))
                        Toast.makeText(context, "大纲已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("导出大纲", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(12.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("章节分配结果", fontWeight = FontWeight.W500, fontSize = 13.sp, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        val tc = config.targetChapters
                        val vol1 = (tc * 0.17).toInt(); val vol2 = (tc * 0.28).toInt()
                        val vol3 = (tc * 0.28).toInt(); val vol4 = (tc * 0.22).toInt()
                        val vol5 = tc - vol1 - vol2 - vol3 - vol4
                        listOf("第1卷" to vol1, "第2卷" to vol2, "第3卷" to vol3, "第4卷" to vol4, "第5卷" to vol5).forEach { (vol, count) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(vol, fontSize = 12.sp, color = TextSecondary); Text("${count}章", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                InkButton(text = "确认并开始创作", onClick = { vm.nextStep() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // 导入大纲对话框
    if (showImportOutlineDialog) {
        AlertDialog(
            onDismissRequest = { showImportOutlineDialog = false },
            title = { Text("导入大纲", fontWeight = FontWeight.W600) },
            text = {
                Column {
                    Text("请粘贴大纲文本：", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importOutlineText,
                        onValueChange = { importOutlineText = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importOutlineText.isNotBlank()) {
                        vm.updateOutline(importOutlineText)
                        showImportOutlineDialog = false
                    }
                }) { Text("确定导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportOutlineDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun Step5Writing(bookId: String, config: StoryConfig, causalChain: List<CausalLinkEntity>, hooks: List<HookEntity>, summaryHistory: String, vm: MainViewModel, onBack: () -> Unit) {
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val currentChapterNum by vm.currentChapterNum.collectAsState()
    var chapterNum by remember { mutableIntStateOf(currentChapterNum.coerceAtLeast(1)) }
    var chapterTitle by remember { mutableStateOf("") }
    var targetWords by remember { mutableIntStateOf(2000) }
    var chapterNumText by remember { mutableStateOf(chapterNum.toString()) }
    var showContextPreview by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize()) {
        StepTopBar("章节创作", onBack)
        Column(Modifier.weight(1f).padding(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StepProgressBar(currentStep = 4, modifier = Modifier.padding(bottom = 20.dp))

                Text("章节创作", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                    fontSize = 26.sp, letterSpacing = 0.5.sp)
                Text("输入章节号和标题，AI 将根据你的世界观、角色和因果链自动生成连贯内容",
                    fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

                GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("章节信息", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 10.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(0.25f)) {
                            Text("章节号", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = chapterNumText,
                                onValueChange = { text ->
                                    chapterNumText = text
                                    val parsed = text.toIntOrNull()
                                    if (parsed != null && parsed > 0) {
                                        chapterNum = parsed
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent))
                        }
                        Column(modifier = Modifier.weight(0.75f)) {
                            Text("章节标题", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(value = chapterTitle, onValueChange = { chapterTitle = it },
                                modifier = Modifier.fillMaxWidth().height(44.dp), singleLine = true,
                                placeholder = { Text("输入章节标题", fontSize = 13.sp, color = TextTertiary) },
                                textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border,
                                    focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    InkDividerLight()
                    Spacer(Modifier.height(10.dp))
                    StatRow(items = listOf(
                        "${targetWords}" to "目标字数",
                        "第 $chapterNum" to "当前章节",
                        "${config.targetChapters}" to "总章节数"
                    ))
                }
            }
            Spacer(Modifier.height(8.dp))

            // 口语化/玩梗开关
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("写作风格", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("口语化创作", fontSize = 14.sp, fontWeight = FontWeight.W500, color = TextPrimary)
                            Text("像真人聊天一样自然，告别AI味", fontSize = 11.sp, color = TextTertiary)
                        }
                        InkSwitch(checked = config.colloquialStyle, onCheckedChange = { vm.toggleColloquialStyle() })
                    }
                    Spacer(Modifier.height(4.dp))
                    InkDividerLight()
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("引用梗写作", fontSize = 14.sp, fontWeight = FontWeight.W500, color = TextPrimary)
                            Text("自然融入热梗/名台词，增加趣味", fontSize = 11.sp, color = TextTertiary)
                        }
                        InkSwitch(checked = config.useMemes, onCheckedChange = { vm.toggleUseMemes() })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // AI 上下文预览（可折叠）
            val hasContext = summaryHistory.isNotBlank() || hooks.any { it.status == "open" } || causalChain.isNotEmpty()
            if (hasContext) {
                GlassCard(Modifier.fillMaxWidth().clickable { showContextPreview = !showContextPreview }) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 上下文预览", fontWeight = FontWeight.W500, fontSize = 12.sp, color = Accent)
                            Text(if (showContextPreview) "收起 ▲" else "展开 ▼", fontSize = 10.sp, color = TextTertiary)
                        }
                        if (showContextPreview) {
                            Spacer(Modifier.height(6.dp))
                            InkDividerLight()
                            Spacer(Modifier.height(6.dp))
                            if (summaryHistory.isNotBlank()) {
                                Text("前情摘要", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Success)
                                Text(summaryHistory.take(300), fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                            val openHooks = hooks.filter { it.status == "open" }
                            if (openHooks.isNotEmpty()) {
                                Text("待回收伏笔 (${openHooks.size})", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Danger)
                                openHooks.take(5).forEach { hook ->
                                    Text("• Ch.${hook.plantedChapter} ${hook.description}", fontSize = 10.sp, color = TextSecondary)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (causalChain.isNotEmpty()) {
                                Text("因果链 (${causalChain.size})", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Info)
                                causalChain.takeLast(5).forEach { link ->
                                    Text("第${link.chapter}章：${link.cause} → ${link.consequence}", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("这些上下文将自动注入AI写作提示词，确保剧情连贯。", fontSize = 9.sp, color = TextTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 生成进度条
            if (isGenerating) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stepLoadingMessage, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.W500)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(4.dp))
                        Text("${(stepProgress * 100).toInt()}%", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.cancelGeneration() }, modifier = Modifier.fillMaxWidth()) {
                            Text("停止生成", fontSize = 12.sp, color = Danger)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            }

            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("上一步", fontSize = 12.sp) }
                InkButton(text = "AI写一章", onClick = { vm.aiWriteContent(config, chapterNum, chapterTitle) },
                    modifier = Modifier.weight(1.5f), enabled = !isGenerating)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    vm.aiBatchWrite(config, 3)
                }, modifier = Modifier.weight(1f), enabled = !isGenerating) {
                    Text("批量写3章", fontSize = 12.sp)
                }
                InkButton(text = "查看创作结果 →", onClick = { vm.nextStep() },
                    modifier = Modifier.weight(1.5f), enabled = !isGenerating)
            }
        }
    }
}

@Composable
private fun Step6AIResult(bookId: String, config: StoryConfig, causalChain: List<CausalLinkEntity>, hooks: List<HookEntity>, summaryHistory: String, vm: MainViewModel, onBack: () -> Unit) {
    val currentContent by vm.currentContent.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val stepProgress by vm.stepProgress.collectAsState()
    val stepLoadingMessage by vm.stepLoadingMessage.collectAsState()
    val chapterNum by vm.currentChapterNum.collectAsState()
    val chapters by vm.chapters.collectAsState()
    var localContent by remember { mutableStateOf(currentContent) }
    var showQuickPhraseDialog by remember { mutableStateOf(false) }
    var showContextPreview by remember { mutableStateOf(false) }

    // 同步ViewModel内容到本地
    LaunchedEffect(currentContent) {
        if (currentContent.isNotBlank() && currentContent != localContent) {
            localContent = currentContent
        }
    }

    val chapterTitle = remember(chapterNum, chapters) {
        chapters.find { it.chapterNumber == chapterNum }?.title ?: ""
    }

    Column(Modifier.fillMaxSize()) {
        StepTopBar("AI创作结果", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 5, modifier = Modifier.padding(bottom = 20.dp))

            Text("AI创作结果", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("审阅、润色和修订 AI 生成的内容",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            // 章节信息
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("章节信息", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("第${chapterNum}章", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp, color = TextPrimary)
                            if (chapterTitle.isNotBlank()) {
                                Text("· ${chapterTitle}", fontSize = 14.sp, color = TextSecondary)
                            }
                        }
                        InkTag(text = "${localContent.length} 字")
                    }
                    Spacer(Modifier.height(10.dp))
                    InkDividerLight()
                    Spacer(Modifier.height(10.dp))
                    StatRow(items = listOf(
                        "${localContent.length}" to "字数",
                        (if (localContent.length >= 1500) "✓" else "!") to "审核",
                        "第 ${chapterNum}" to "章节"
                    ))
                }
            }
            Spacer(Modifier.height(8.dp))

            // 生成进度条
            if (isGenerating) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stepLoadingMessage, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.W500)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { stepProgress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                        Spacer(Modifier.height(4.dp))
                        Text("${(stepProgress * 100).toInt()}%", fontSize = 11.sp, color = TextTertiary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.cancelGeneration() }, modifier = Modifier.fillMaxWidth()) {
                            Text("停止生成", fontSize = 12.sp, color = Danger)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 可编辑内容区（使用 TextFieldValue 支持选中文字）
            val textFieldValue = remember(localContent) { TextFieldValue(text = localContent, selection = TextRange(localContent.length)) }
            var currentSelection by remember { mutableStateOf(TextRange.Zero) }
            var showInstructionInput by remember { mutableStateOf(false) }
            var instructionText by remember { mutableStateOf("") }

            GlassCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = textFieldValue.copy(selection = currentSelection),
                    onValueChange = { newValue ->
                        currentSelection = newValue.selection
                        val newText = newValue.text
                        localContent = newText
                        vm.updateCurrentContent(newText)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 600.dp).padding(14.dp),
                    enabled = !isGenerating,
                    placeholder = { Text("在「章节创作」页面点击AI按钮生成内容，结果将显示在这里", fontSize = 13.sp, color = TextTertiary) },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 15.sp, lineHeight = 27.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent))
            }
            Spacer(Modifier.height(8.dp))

            // 选中文字操作栏
            if (currentSelection.collapsed == false && currentSelection.min != currentSelection.max) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("已选中 ${currentSelection.max - currentSelection.min} 字", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.W500)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InkButton(text = "AI重写此处", onClick = {
                                val selected = localContent.substring(currentSelection.min, currentSelection.max)
                                vm.rewriteSelectedContent(config, selected)
                            }, enabled = !isGenerating)
                            InkButton(text = "按指令改", onClick = {
                                showInstructionInput = !showInstructionInput
                                if (showInstructionInput) {
                                    val selected = localContent.substring(currentSelection.min, currentSelection.max)
                                    instructionText = "对选中文字：$selected"
                                }
                            }, enabled = !isGenerating)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 按指令修改输入框
            if (showInstructionInput) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("输入修改指令", fontSize = 11.sp, fontWeight = FontWeight.W600,
                            color = Accent, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = instructionText, onValueChange = { instructionText = it },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = !isGenerating,
                                placeholder = { Text("如：把这段改得更紧张", fontSize = 12.sp, color = TextTertiary) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextPrimary),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Border, unfocusedBorderColor = Border))
                            Spacer(Modifier.width(8.dp))
                            InkButton(text = "执行", onClick = {
                                // 如果选中了文字，重写选中部分；否则修改全文
                                if (currentSelection.collapsed == false && currentSelection.min != currentSelection.max) {
                                    val selected = localContent.substring(currentSelection.min, currentSelection.max)
                                    vm.rewriteSelectedContent(config, selected, instructionText.removePrefix("对选中文字：$selected"))
                                } else {
                                    vm.aiRewriteWithInstruction(config, instructionText)
                                }
                                showInstructionInput = false
                            }, enabled = !isGenerating && instructionText.isNotBlank())
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // AI 上下文预览
            val hasContext = summaryHistory.isNotBlank() || hooks.any { it.status == "open" } || causalChain.isNotEmpty()
            if (hasContext) {
                GlassCard(Modifier.fillMaxWidth().clickable { showContextPreview = !showContextPreview }) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("AI 上下文预览", fontWeight = FontWeight.W500, fontSize = 12.sp, color = Accent)
                            Text(if (showContextPreview) "收起 ▲" else "展开 ▼", fontSize = 10.sp, color = TextTertiary)
                        }
                        if (showContextPreview) {
                            Spacer(Modifier.height(6.dp))
                            InkDividerLight()
                            Spacer(Modifier.height(6.dp))
                            if (summaryHistory.isNotBlank()) {
                                Text("前情摘要", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Success)
                                Text(summaryHistory.take(300), fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
                                Spacer(Modifier.height(6.dp))
                            }
                            val openHooks = hooks.filter { it.status == "open" }
                            if (openHooks.isNotEmpty()) {
                                Text("待回收伏笔", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Danger)
                                openHooks.take(5).forEach { hook ->
                                    Text("• Ch.${hook.plantedChapter} ${hook.description}", fontSize = 10.sp, color = TextSecondary)
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (causalChain.isNotEmpty()) {
                                Text("因果链", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Info)
                                causalChain.takeLast(5).forEach { link ->
                                    Text("第${link.chapter}章：${link.cause} → ${link.consequence}", fontSize = 10.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 操作按钮区
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("AI 操作", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InkButton(text = "AI重写", onClick = { vm.aiWriteContent(config, chapterNum, chapterTitle) },
                            modifier = Modifier.weight(1f), enabled = !isGenerating)
                        InkButton(text = "润色", onClick = { vm.polishContent(config) },
                            modifier = Modifier.weight(1f), enabled = !isGenerating && localContent.isNotBlank())
                        InkButton(text = "续写", onClick = { vm.continueWriting(config) },
                            modifier = Modifier.weight(1f), enabled = !isGenerating)
                        InkButton(text = "修订", onClick = { vm.reviseContent(config) },
                            modifier = Modifier.weight(1f), enabled = !isGenerating && localContent.isNotBlank())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, enabled = !isGenerating,
                    modifier = Modifier.weight(1f).height(40.dp)) { Text("上一步", fontSize = 13.sp) }
                OutlinedButton(onClick = { showQuickPhraseDialog = true }, enabled = !isGenerating,
                    modifier = Modifier.weight(1f).height(40.dp)) { Text("快捷短语", fontSize = 13.sp) }
                InkButton(text = "保存", onClick = { vm.saveChapter(bookId, chapterNum, localContent) },
                    modifier = Modifier.weight(1f), enabled = !isGenerating)
            }
        }
    }

    // 快捷短语对话框
    if (showQuickPhraseDialog) {
        val quickPhrases = listOf(
            "他心中涌起一阵不安" to "情绪",
            "突然，一道灵光闪过" to "转折",
            "远处传来一阵脚步声" to "场景",
            "她意味深长地看了他一眼" to "动作",
            "空气中弥漫着紧张的气氛" to "氛围",
            "这一刻，他终于明白了" to "感悟",
            "\u201C你来了。\u201D他淡淡地说" to "对话",
            "夜色如墨，星辰黯淡" to "描写",
            "往事如潮水般涌来" to "回忆",
            "命运的齿轮开始转动" to "叙事"
        )
        AlertDialog(
            onDismissRequest = { showQuickPhraseDialog = false },
            title = { Text("快捷短语", fontWeight = FontWeight.W600) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    quickPhrases.forEach { (phrase, category) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                localContent = if (localContent.isNotBlank()) "$localContent\n$phrase" else phrase
                                vm.updateCurrentContent(localContent)
                                showQuickPhraseDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = BgCard
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(phrase, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(category, fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickPhraseDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun Step7Timeline(causalChain: List<CausalLinkEntity>, hooks: List<HookEntity>, emotions: List<EmotionEntity>, summaryHistory: String, vm: MainViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepTopBar("完稿审校", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StepProgressBar(currentStep = 6, modifier = Modifier.padding(bottom = 20.dp))

            Text("完稿审校", fontFamily = SerifFamily, fontWeight = FontWeight.W700,
                fontSize = 26.sp, letterSpacing = 0.5.sp)
            Text("检查伏笔、因果链、角色行为一致性，确保作品质量",
                fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("一致性检查", fontSize = 11.sp, fontWeight = FontWeight.W600,
                        color = Accent, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("检查伏笔、因果链、角色行为一致性", fontSize = 12.sp, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
            TrackingCard("因果链", "事件的因果关系网络", Accent, causalChain.size) {
                if (causalChain.isEmpty()) Text("暂无因果链数据", fontSize = 11.sp, color = TextTertiary)
                else causalChain.takeLast(5).forEach { link ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("Ch.${link.chapter}", fontSize = 10.sp, color = Accent, modifier = Modifier.width(36.dp))
                        Column {
                            Text("因：${link.cause}", fontSize = 11.sp, color = TextSecondary)
                            Text("果：${link.consequence}", fontSize = 11.sp, color = TextTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TrackingCard("伏笔状态", "未闭合伏笔追踪", Danger, hooks.size) {
                if (hooks.isEmpty()) Text("暂无伏笔数据", fontSize = 11.sp, color = TextTertiary)
                else hooks.takeLast(5).forEach { hook ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(when(hook.status) { "open" -> Accent; "resolved" -> Success; else -> Danger }))
                        Spacer(Modifier.width(8.dp))
                        Text(hook.description, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text("Ch.${hook.plantedChapter}", fontSize = 9.sp, color = TextTertiary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TrackingCard("情感弧线", "角色情感变化追踪", Info, emotions.size) {
                if (emotions.isEmpty()) Text("暂无情感数据，AI创作章节后会自动提取", fontSize = 11.sp, color = TextTertiary)
                else emotions.groupBy { it.characterId }.forEach { (charId, snaps) ->
                    Text(charId, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        snaps.takeLast(10).forEach { snap ->
                            Box(Modifier.weight(1f).fillMaxHeight(fraction = snap.intensity / 10f)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(Accent))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            TrackingCard("前情摘要", "已完成章节摘要", Success, if (summaryHistory.isNotBlank()) 1 else 0) {
                if (summaryHistory.isBlank()) Text("暂无摘要数据，AI创作章节后会自动生成", fontSize = 11.sp, color = TextTertiary)
                else {
                    val lines = summaryHistory.lines().filter { it.isNotBlank() }
                    lines.takeLast(10).forEach { line ->
                        Text(line.take(100), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 2.dp), lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(InkRadius.md),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(content = content) }
}

@Composable
private fun GlassInput(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, placeholder: String = "", label: String = "",
    height: Dp = 44.dp, keyboardType: KeyboardType = KeyboardType.Text, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(height),
        singleLine = height == 44.dp, enabled = enabled,
        placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder, fontSize = 12.sp, color = TextTertiary) }} else null,
        label = if (label.isNotBlank()) {{ Text(label) }} else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Border,
            focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent))
}

@Composable
private fun StepTopBar(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPrimary.copy(alpha = 0.95f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BgCard)
                    .border(1.dp, Border, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontFamily = SerifFamily, fontWeight = FontWeight.W600,
                fontSize = 17.sp, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.height(2.dp))
        InkDividerLight()
    }
}

@Composable
private fun Label(text: String) { Text(text, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500) }

@Composable
private fun ActionBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(text, fontSize = 11.sp) }
}

@Composable
private fun TrackingCard(title: String, subtitle: String, dotColor: Color, count: Int, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(8.dp))
                Column { Text(title, fontWeight = FontWeight.W500, fontSize = 13.sp, color = TextPrimary); Text(subtitle, fontSize = 10.sp, color = TextTertiary) }
                if (count > 0) { Spacer(Modifier.weight(1f)); Text("${count}条", fontSize = 11.sp, color = TextTertiary) }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
