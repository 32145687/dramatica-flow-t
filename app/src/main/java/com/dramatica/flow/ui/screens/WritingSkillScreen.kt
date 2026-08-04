package com.dramatica.flow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.WritingSkillEntity
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WritingSkillScreen(
    vm: MainViewModel,
    currentBookId: String,
    onBack: () -> Unit
) {
    val skillResult by vm.writingSkillResult.collectAsState()
    val progress by vm.writingSkillProgress.collectAsState()
    val message by vm.writingSkillMessage.collectAsState()
    val context = LocalContext.current

    var existingSkill by remember { mutableStateOf<WritingSkillEntity?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var fileCharCount by remember { mutableStateOf(0) }
    var isDistilling by remember { mutableStateOf(false) }
    var showDistill by remember { mutableStateOf(false) }

    // 加载已有技能：跟随 skillResult 变化（包括切换书籍、清除技能等场景）
    LaunchedEffect(skillResult) {
        existingSkill = skillResult
        if (isDistilling) {
            isDistilling = false
            showDistill = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val name = uri.lastPathSegment ?: "未知文件.txt"
                selectedFileName = name
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val content = reader.readText()
                reader.close()
                inputStream?.close()
                fileContent = content
                fileCharCount = content.length
            } catch (e: Exception) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream, "GBK"))
                    val content = reader.readText()
                    reader.close()
                    inputStream?.close()
                    fileContent = content
                    fileCharCount = content.length
                } catch (_: Exception) {
                    selectedFileName = null
                    fileContent = null
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                if (showDistill) showDistill = false
                else onBack()
            }) {
                Text("← 返回", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("写作技能", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        InkDividerLight()

        if (!showDistill) {
            // 页面1：技能状态
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (existingSkill != null) {
                    val skill = existingSkill!!
                    // 当前技能展示
                    InkCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("📋 当前技能", fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                            Text("来源：${skill.sourceNovel}", fontSize = 12.sp, color = TextSecondary)
                            Text("蒸馏时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(skill.createdAt))}",
                                fontSize = 11.sp, color = TextTertiary)

                            if (skill.sentencePatterns.isNotBlank()) {
                                Text("句式特征", fontSize = 12.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(skill.sentencePatterns, fontSize = 12.sp, color = TextSecondary)
                            }
                            if (skill.vocabularyFingerprint.isNotBlank()) {
                                Text("词汇指纹", fontSize = 12.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(skill.vocabularyFingerprint, fontSize = 12.sp, color = TextSecondary)
                            }
                            if (skill.narrativeStyle.isNotBlank()) {
                                Text("叙事手法", fontSize = 12.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(skill.narrativeStyle, fontSize = 12.sp, color = TextSecondary)
                            }
                            if (skill.dialogueStyle.isNotBlank()) {
                                Text("对话风格", fontSize = 12.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(skill.dialogueStyle, fontSize = 12.sp, color = TextSecondary)
                            }
                            if (skill.pacingStyle.isNotBlank()) {
                                Text("节奏特征", fontSize = 12.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                                Text(skill.pacingStyle, fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InkButton(
                            text = "重新蒸馏",
                            onClick = { showDistill = true },
                            modifier = Modifier.weight(1f)
                        )
                        InkButton(
                            text = "清除技能",
                            onClick = { vm.clearWritingSkill(); existingSkill = null },
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("⚠ 清除后写作将恢复通用风格", fontSize = 11.sp, color = TextTertiary)
                } else {
                    // 无技能
                    InkCard {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🎨", fontSize = 40.sp)
                            Text("尚未设置写作技能", fontSize = 14.sp, color = TextSecondary)
                            Text("选择一篇参考小说，AI 将蒸馏其写作风格", fontSize = 12.sp, color = TextTertiary)
                        }
                    }

                    InkButton(
                        text = "选择参考小说进行蒸馏",
                        onClick = { showDistill = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // 页面2：选择文件 + 蒸馏
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 文件选择
                InkCard {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📄", fontSize = 40.sp)
                        Text("选择参考小说 TXT 文件", fontSize = 14.sp, color = TextSecondary)
                        if (selectedFileName != null) {
                            Text(selectedFileName!!, fontSize = 13.sp, fontWeight = FontWeight.W500, color = Accent)
                            if (fileCharCount > 0) {
                                val sizeStr = if (fileCharCount > 10000) "${fileCharCount / 10000}万字" else "${fileCharCount}字"
                                Text("约 $sizeStr · 将均匀采样分析", fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(100.dp)
                                .clip(RoundedCornerShape(InkRadius.md))
                                .background(BgPrimary)
                                .clickable { filePicker.launch(arrayOf("text/plain", "*/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (selectedFileName != null) "点击重新选择" else "选择 .txt 文件",
                                fontSize = 12.sp, color = TextTertiary
                            )
                        }
                    }
                }

                // 蒸馏维度
                InkCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("蒸馏维度", fontSize = 13.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                        Text("• 句式模式：长短句、断句、标点、段落", fontSize = 12.sp, color = TextSecondary)
                        Text("• 词汇指纹：高频词、禁用词、惯用搭配", fontSize = 12.sp, color = TextSecondary)
                        Text("• 叙事手法：视角、白描/渲染、对话占比", fontSize = 12.sp, color = TextSecondary)
                        Text("• 对话风格：语气词、称呼、交锋模式", fontSize = 12.sp, color = TextSecondary)
                        Text("• 节奏特征：段落分布、场景切换、张弛", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                // 蒸馏进度
                if (isDistilling) {
                    InkCard {
                        Column(Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⏳ $message", fontSize = 13.sp, color = TextSecondary)
                            InkProgressBar(progress * 100, Modifier.fillMaxWidth())
                            Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }

                InkButton(
                    text = "开始蒸馏",
                    onClick = {
                        val content = fileContent
                        val name = selectedFileName
                        if (content != null && name != null) {
                            isDistilling = true
                            vm.distillWritingSkill(name, content)
                        }
                    },
                    enabled = fileContent != null && !isDistilling,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}