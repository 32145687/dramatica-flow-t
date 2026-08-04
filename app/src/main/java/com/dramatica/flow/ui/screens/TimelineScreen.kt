package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

@Composable
fun TimelineScreen(
    book: BookEntity?,
    bookId: String,
    timeline: List<TimelineEntity>,
    vm: MainViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (book != null) {
                BookBanner(
                    name = book.title, genre = book.genre,
                    chapters = book.currentChapter, words = book.currentChapter * book.targetWords,
                    color = Accent, onClick = {}
                )
            } else {
                Text("时间线", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 19.sp,
                    modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 14.dp))
            }

            if (timeline.isEmpty()) {
                EmptyState("📅", "暂无时间线", "写作时自动生成，或手动添加", "手动添加") { showAddDialog = true }
            } else {
                val grouped = timeline.groupBy { it.chapter }
                grouped.toSortedMap().forEach { (ch, events) ->
                    Column(Modifier.fillMaxWidth()) {
                        Text("第${ch}章", fontSize = 11.sp, fontWeight = FontWeight.W600, color = TextTertiary,
                            letterSpacing = 0.08.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp).padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            events.forEach { event ->
                                val typeColor = when (event.type) {
                                    "conflict" -> Danger; "reveal" -> Success
                                    "emotion" -> Info; "foreshadow" -> Accent; else -> Border
                                }
                                val typeLabel = when (event.type) {
                                    "conflict" -> "冲突"; "reveal" -> "揭示"
                                    "emotion" -> "情感"; "foreshadow" -> "伏笔"; else -> "事件"
                                }
                                Box(Modifier.width(150.dp).clip(RoundedCornerShape(InkRadius.sm)).background(BgCard)
                                    .clip(RoundedCornerShape(InkRadius.sm))) {
                                    Row {
                                        Box(Modifier.width(3.dp).fillMaxHeight().background(typeColor))
                                        Column(Modifier.padding(11.dp, 9.dp).width(140.dp)) {
                                            Text(typeLabel, fontSize = 9.sp, color = typeColor, fontWeight = FontWeight.W600)
                                            Spacer(Modifier.height(3.dp))
                                            if (event.characterId.isNotBlank())
                                                Text(event.characterId, fontSize = 9.sp, color = Accent)
                                            Text(event.action, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                        InkDividerLight()
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        // 底部添加按钮
        if (bookId.isNotBlank()) {
            Row(Modifier.fillMaxWidth().background(BgPrimary).padding(12.dp, 8.dp),
                horizontalArrangement = Arrangement.Center) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("添加事件", fontSize = 13.sp, color = Accent)
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimelineEventDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { chapter, action, type ->
                vm.addTimelineEvent(bookId, chapter, action, type)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddTimelineEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (chapter: Int, action: String, type: String) -> Unit
) {
    var chapter by remember { mutableStateOf("1") }
    var action by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("conflict") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加时间线事件", fontWeight = FontWeight.W600) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = chapter, onValueChange = { chapter = it.filter { c -> c.isDigit() } },
                    label = { Text("章节号") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // 事件类型选择
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("conflict" to "冲突", "reveal" to "揭示", "emotion" to "情感", "foreshadow" to "伏笔").forEach { (type, label) ->
                        val colors = when (type) {
                            "conflict" -> Danger; "reveal" -> Success
                            "emotion" -> Info; "foreshadow" -> Accent; else -> Border
                        }
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.copy(alpha = 0.15f),
                                selectedLabelColor = colors
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = action, onValueChange = { action = it },
                    label = { Text("事件描述") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如：主角发现密室", fontSize = 13.sp) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ch = chapter.toIntOrNull() ?: 1
                    if (action.isNotBlank()) onConfirm(ch, action, selectedType)
                },
                enabled = action.isNotBlank()
            ) { Text("添加", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
