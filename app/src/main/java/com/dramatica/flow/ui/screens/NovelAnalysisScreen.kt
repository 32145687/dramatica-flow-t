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
import com.dramatica.flow.data.TextParser
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun NovelAnalysisScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onConfirm: (title: String, genre: String, analysis: String) -> Unit
) {
    val analysisResult by vm.novelAnalysisResult.collectAsState()
    val progress by vm.novelAnalysisProgress.collectAsState()
    val message by vm.novelAnalysisMessage.collectAsState()
    val isAnalyzing by vm.isNovelAnalyzing.collectAsState()
    val context = LocalContext.current

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var fileCharCount by remember { mutableStateOf(0) }
    var showResult by remember { mutableStateOf(false) }

    // 可编辑的分析结果
    var editTitle by remember { mutableStateOf("") }
    var editGenre by remember { mutableStateOf("") }
    var editAnalysis by remember { mutableStateOf("") }

    // 离开页面时清理分析状态
    DisposableEffect(Unit) {
        onDispose { vm.clearNovelAnalysis() }
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
                // 尝试 GBK 编码
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

    // 监听分析完成——使用 isAnalyzing 防止初始 composition 触发
    LaunchedEffect(analysisResult) {
        if (analysisResult != null && isAnalyzing) {
            showResult = true
            val text = analysisResult ?: ""
            editTitle = TextParser.extractField(text, "书名")
            editGenre = TextParser.extractField(text, "题材")
            editAnalysis = text
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                if (showResult) { showResult = false }
                else onBack()
            }) {
                Text("← 返回", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("参考小说创作", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        InkDividerLight()

        if (!showResult) {
            // 页面1：选择文件 + 分析
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 文件选择区域
                InkCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📄", fontSize = 40.sp)
                        Text("点击选择 TXT 文件", fontSize = 14.sp, color = TextSecondary)
                        if (selectedFileName != null) {
                            Text(selectedFileName!!, fontSize = 13.sp, fontWeight = FontWeight.W500,
                                color = Accent)
                            if (fileCharCount > 0) {
                                val sizeStr = if (fileCharCount > 10000) "${fileCharCount / 10000}万字" else "${fileCharCount}字"
                                Text("约 $sizeStr · ${if (fileCharCount > 16000) "将自动分块分析" else "全文分析"}",
                                    fontSize = 11.sp, color = TextTertiary)
                            }
                        }
                        Box(
                            Modifier.fillMaxWidth().height(120.dp)
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

                // 分析维度说明
                InkCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("分析维度", fontSize = 13.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                        Text("• 题材与主题", fontSize = 12.sp, color = TextSecondary)
                        Text("• 世界观设定", fontSize = 12.sp, color = TextSecondary)
                        Text("• 角色原型", fontSize = 12.sp, color = TextSecondary)
                        Text("• 叙事结构", fontSize = 12.sp, color = TextSecondary)
                        Text("• 语言风格", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                // 分析中进度
                if (isAnalyzing) {
                    InkCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⏳ $message", fontSize = 13.sp, color = TextSecondary)
                            InkProgressBar(progress * 100, Modifier.fillMaxWidth())
                            Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                }

                // 开始分析按钮
                InkButton(
                    text = "开始分析",
                    onClick = {
                        val content = fileContent
                        val name = selectedFileName
                        if (content != null && name != null) {
                            vm.analyzeNovel(name, content)
                        }
                    },
                    enabled = fileContent != null && !isAnalyzing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // 页面2：分析结果
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("分析结果（可编辑）", fontSize = 14.sp, fontWeight = FontWeight.W600,
                    color = TextPrimary, fontFamily = SerifFamily)

                // 书名
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 题材
                OutlinedTextField(
                    value = editGenre,
                    onValueChange = { editGenre = it },
                    label = { Text("题材") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 完整分析
                OutlinedTextField(
                    value = editAnalysis,
                    onValueChange = { editAnalysis = it },
                    label = { Text("完整分析结果") },
                    minLines = 8,
                    maxLines = 20,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            showResult = false
                            val content = fileContent
                            val name = selectedFileName
                            if (content != null && name != null) {
                                vm.analyzeNovel(name, content)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重新分析", fontSize = 13.sp)
                    }
                    InkButton(
                        text = "确认创建",
                        onClick = {
                            val title = editTitle.ifBlank { "未命名作品" }
                            val genre = editGenre.ifBlank { "玄幻" }
                            onConfirm(title, genre, editAnalysis)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 工具函数统一使用 TextParser