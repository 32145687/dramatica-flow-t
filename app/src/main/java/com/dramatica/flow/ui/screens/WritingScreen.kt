package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.ChapterEntity
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String, // "user" or "ai"
    val content: String
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun WritingScreen(
    bookId: String, chapters: List<ChapterEntity>,
    editingChapter: ChapterEntity?, vm: MainViewModel
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var editorText by remember { mutableStateOf("") }
    var showAiChat by remember { mutableStateOf(false) }
    var showChapterMenu by remember { mutableStateOf(false) }
    var savedText by remember { mutableStateOf("") }  // 已保存的文本，用于判断是否有修改

    // 撤销/重做栈
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var isUndoRedo by remember { mutableStateOf(false) }  // 避免撤销操作本身触发推入栈

    // 推入撤销栈（非撤销/重做操作时）
    fun pushUndo(text: String) {
        if (!isUndoRedo && text != undoStack.lastOrNull()) {
            undoStack.add(text)
            if (undoStack.size > 50) undoStack.removeFirst()
        }
    }

    // AI 对话状态
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var chatInput by remember { mutableStateOf("") }
    var chatLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listScrollState = rememberScrollState()

    LaunchedEffect(editingChapter) {
        editingChapter?.let {
            editorText = it.content
            savedText = it.content
            undoStack.clear(); redoStack.clear()
            undoStack.add(it.content)
        }
    }

    // 自动保存：用户停止输入30秒后触发保存
    LaunchedEffect(Unit) {
        snapshotFlow { editorText }
            .debounce(30000L)
            .collect { text ->
                if (text != savedText) {
                    val chNum = chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1
                    vm.saveChapter(bookId, chNum, text)
                    savedText = text
                }
            }
    }

    // 切换章节时自动保存
    fun switchChapter(newIndex: Int) {
        if (newIndex in chapters.indices && newIndex != selectedIndex) {
            // 保存当前章节
            val chNum = chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1
            if (editorText != savedText) {
                vm.saveChapter(bookId, chNum, editorText)
                savedText = editorText
            }
            selectedIndex = newIndex
            vm.loadChapter(bookId, chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部工具栏
        Row(Modifier.fillMaxWidth().background(BgPrimary).padding(14.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { switchChapter(selectedIndex - 1) },
                    modifier = Modifier.size(32.dp), enabled = selectedIndex > 0) {
                    Icon(NavIcons.KeyboardArrowLeft, null, Modifier.size(15.dp), tint = TextTertiary)
                }
                val chNum = chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1
                val chTitle = chapters.getOrNull(selectedIndex)?.title ?: ""
                Box {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showChapterMenu = true }.padding(8.dp, 2.dp)) {
                        Text("第${chNum}章", fontFamily = SerifFamily, fontWeight = FontWeight.W500, fontSize = 14.sp)
                        if (chTitle.isNotBlank()) {
                            Text(chTitle, fontSize = 10.sp, color = TextTertiary, maxLines = 1)
                        }
                    }
                    DropdownMenu(expanded = showChapterMenu, onDismissRequest = { showChapterMenu = false }) {
                        chapters.forEachIndexed { idx, ch ->
                            DropdownMenuItem(
                                text = { Text("第${ch.chapterNumber}章 ${ch.title}".trim(), fontSize = 13.sp) },
                                onClick = { showChapterMenu = false; switchChapter(idx) },
                                leadingIcon = if (idx == selectedIndex) {
                                    { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Accent)) }
                                } else null
                            )
                        }
                    }
                }
                IconButton(onClick = { switchChapter(selectedIndex + 1) },
                    modifier = Modifier.size(32.dp), enabled = selectedIndex < chapters.size - 1) {
                    Icon(NavIcons.KeyboardArrowRight, null, Modifier.size(15.dp), tint = TextTertiary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // 撤销按钮
                TopbarButton(icon = NavIcons.Undo, onClick = {
                    if (undoStack.size > 1) {
                        isUndoRedo = true
                        val current = undoStack.removeLast()
                        redoStack.add(current)
                        editorText = undoStack.last()
                        isUndoRedo = false
                    }
                }, enabled = undoStack.size > 1)
                // 重做按钮
                TopbarButton(icon = NavIcons.Refresh, onClick = {
                    if (redoStack.isNotEmpty()) {
                        isUndoRedo = true
                        val text = redoStack.removeLast()
                        undoStack.add(text)
                        editorText = text
                        isUndoRedo = false
                    }
                }, enabled = redoStack.isNotEmpty())
                TopbarButton(icon = NavIcons.SmartToy, onClick = { showAiChat = true }, accent = true)
            }
        }
        InkDividerLight()

        // 编辑器
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 20.dp)) {
                val chNum = chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1
                val chTitle = chapters.getOrNull(selectedIndex)?.title ?: ""
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("第${chNum}章", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 18.sp)
                    if (chTitle.isNotBlank()) {
                        Text(chTitle, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Box(Modifier.padding(top = 10.dp).width(30.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Accent))
                }
                Spacer(Modifier.height(24.dp))
                BasicTextField(value = editorText, onValueChange = { newText ->
                    pushUndo(editorText)
                    editorText = newText
                    redoStack.clear()
                },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp),
                    textStyle = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, lineHeight = 30.sp, color = TextPrimary),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { innerTextField ->
                        Box {
                            if (editorText.isEmpty()) {
                                Text("开始创作...", style = TextStyle(fontFamily = SerifFamily, fontSize = 15.sp, color = TextTertiary))
                            }
                            innerTextField()
                        }
                    })
                Spacer(Modifier.height(100.dp))
            }
        }

        // 底部工具栏
        InkDividerLight()
        Row(Modifier.fillMaxWidth().background(BgPrimary).padding(14.dp, 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val wc = editorText.replace("\\s+".toRegex(), "").length
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$wc 字", fontSize = 11.sp, fontWeight = FontWeight.W500, color = TextSecondary)
                InkProgressBar((wc.toFloat() / 4000 * 100).coerceIn(0f, 100f), Modifier.width(50.dp), height = 3.dp)
                // 修改指示器
                if (editorText != savedText) {
                    Text("●", fontSize = 8.sp, color = Accent)
                }
            }
            val chNum = chapters.getOrNull(selectedIndex)?.chapterNumber ?: 1
            Button(onClick = {
                vm.saveChapter(bookId, chNum, editorText)
                savedText = editorText
            }, modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(12.dp, 0.dp)) {
                Text("保存", fontSize = 11.sp, color = Color.White)
            }
        }
    }

    // AI 对话 BottomSheet
    if (showAiChat) {
        ModalBottomSheet(
            onDismissRequest = { showAiChat = false },
            containerColor = BgPrimary,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().heightIn(min = 400.dp, max = 600.dp)) {
                // 标题栏
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("AI 写作助手", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
                    TextButton(onClick = { chatMessages.clear() }) {
                        Text("清空对话", fontSize = 12.sp, color = TextTertiary)
                    }
                }
                InkDividerLight()

                // 快捷操作
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickActions = listOf(
                        "续写" to "请续写当前章节，保持风格一致，约500字",
                        "润色" to "请润色当前章节，优化文笔",
                        "建议" to "请针对当前章节给出3条写作建议",
                        "扩写" to "请扩写当前章节，增加细节描写",
                        "大纲" to "请为当前章节写一个简短的大纲",
                        "对话" to "请为当前章节增加一段对话"
                    )
                    quickActions.forEach { (label, prompt) ->
                        SuggestionChip(
                            onClick = {
                                chatInput = prompt
                            },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = BgCard, labelColor = TextSecondary
                            )
                        )
                    }
                }

                // 消息列表
                Column(Modifier.weight(1f).verticalScroll(listScrollState).padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    if (chatMessages.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🤖", fontSize = 32.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("输入问题或点击上方快捷操作", fontSize = 13.sp, color = TextTertiary)
                                Text("AI 会结合小说设定给出建议", fontSize = 12.sp, color = TextTertiary)
                            }
                        }
                    }
                    chatMessages.forEach { msg ->
                        ChatBubble(msg, onInsert = { content ->
                            editorText = if (editorText.isBlank()) content else "$editorText\n\n$content"
                        })
                        Spacer(Modifier.height(8.dp))
                    }
                    if (chatLoading) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 思考中...", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 输入框
                InkDividerLight()
                Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = chatInput, onValueChange = { chatInput = it },
                        modifier = Modifier.weight(1f).height(44.dp),
                        placeholder = { Text("输入问题...", fontSize = 13.sp, color = TextTertiary) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedContainerColor = BgCard, unfocusedContainerColor = BgCard,
                            cursorColor = Accent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (chatInput.isNotBlank() && !chatLoading) {
                                val msg = chatInput.trim()
                                chatMessages.add(ChatMessage("user", msg))
                                chatInput = ""
                                chatLoading = true
                                coroutineScope.launch {
                                    listScrollState.animateScrollTo(listScrollState.maxValue)
                                    try {
                                        val reply = vm.aiChatMessage(msg, editorText)
                                        chatMessages.add(ChatMessage("ai", reply))
                                    } catch (e: Exception) {
                                        chatMessages.add(ChatMessage("ai", "出错了：${e.message}"))
                                    } finally {
                                        chatLoading = false
                                    }
                                }
                            }
                        },
                        enabled = chatInput.isNotBlank() && !chatLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Send, "发送", tint = if (chatInput.isNotBlank()) Accent else Border)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, onInsert: (String) -> Unit) {
    val isUser = msg.role == "user"
    val bgColor = if (isUser) AccentBg else BgCard
    val textColor = if (isUser) TextPrimary else TextPrimary
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(12.dp)).background(bgColor)
            .padding(12.dp, 10.dp)) {
            Column {
                Text(msg.content, fontSize = 13.sp, color = textColor, lineHeight = 20.sp)
                if (!isUser) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { onInsert(msg.content) },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("插入正文", fontSize = 10.sp, color = Accent)
                        }
                    }
                }
            }
        }
    }
}
