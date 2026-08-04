package com.dramatica.flow.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.BookEntity
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    books: List<BookEntity>,
    onSelect: (BookEntity) -> Unit,
    onCreate: (String, String) -> Unit,
    onDelete: (BookEntity) -> Unit,
    onExport: (BookEntity) -> Unit = {},
    onRename: (BookEntity, String) -> Unit = { _, _ -> },
    onAnalyzeNovel: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingBook by remember { mutableStateOf<BookEntity?>(null) }
    var renamingBook by remember { mutableStateOf<BookEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showFullTitle by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now()
    val dayLabel = "${today.monthValue}月${today.dayOfMonth}日 · ${
        when (today.dayOfWeek.value) {
            1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"
            4 -> "星期四"; 5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
        }
    }"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.padding(start = 16.dp, top = 20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text("你好，", fontFamily = SerifFamily, fontWeight = FontWeight.W300, fontSize = 22.sp)
            Text("创作者", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 22.sp, color = Accent)
        }
        Text(dayLabel, fontSize = 11.5.sp, color = TextTertiary,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 18.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("${books.size}", "作品", 100f)
            StatChip("${books.sumOf { it.currentChapter }}", "总章节", 50f)
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader(title = "我的作品")
        Spacer(Modifier.height(10.dp))

        books.forEach { book ->
            InkCard(Modifier.padding(horizontal = 16.dp), onClick = { onSelect(book) }) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.width(52.dp).height(70.dp).clip(RoundedCornerShape(6.dp)).background(Accent),
                        contentAlignment = Alignment.Center) {
                        Text(book.title.firstOrNull()?.toString() ?: "?", color = Color.White, fontFamily = SerifFamily,
                            fontWeight = FontWeight.W700, fontSize = 18.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(book.genre, fontSize = 9.sp, color = Accent, fontWeight = FontWeight.W500)
                        Text(book.title, fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                                .combinedClickable(
                                    onClick = { onSelect(book) },
                                    onLongClick = { showFullTitle = book.title }
                                ))
                        Text("第${book.currentChapter}/${book.targetChapters}章", fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            renamingBook = book
                            renameText = book.title
                        },
                            modifier = Modifier.size(28.dp)) {
                            Icon(NavIcons.Writing, "重命名", Modifier.size(14.dp), tint = TextTertiary)
                        }
                        Text("重命名", fontSize = 8.sp, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onExport(book) },
                            modifier = Modifier.size(28.dp)) {
                            Icon(NavIcons.Send, "导出", Modifier.size(14.dp), tint = TextTertiary)
                        }
                        Text("导出", fontSize = 8.sp, color = TextTertiary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { deletingBook = book },
                            modifier = Modifier.size(28.dp)) {
                            Icon(NavIcons.Delete, "删除", Modifier.size(14.dp), tint = TextTertiary)
                        }
                        Text("删除", fontSize = 8.sp, color = TextTertiary)
                    }
                }
                Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    InkProgressBar(
                        if (book.targetChapters > 0) book.currentChapter.toFloat() / book.targetChapters * 100 else 0f,
                        Modifier.fillMaxWidth(), height = 3.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(InkRadius.md)).background(BgCard)
            .drawBehind {
                val s = 1.5.dp.toPx()
                drawRoundRect(color = Border, topLeft = Offset(s / 2, s / 2),
                    size = Size(size.width - s, size.height - s),
                    cornerRadius = CornerRadius(InkRadius.md.toPx()),
                    style = Stroke(s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))))
            }
            .clickable { showDialog = true }.padding(20.dp), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(BgWarm), contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 15.sp, color = TextTertiary)
                }
                Text("创建新作品", fontSize = 13.sp, color = TextTertiary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDialog) {
        // 三选一对话框
        AlertDialog(onDismissRequest = { showDialog = false }, containerColor = BgCard,
            title = { Text("创建新作品", fontFamily = SerifFamily, fontWeight = FontWeight.W600) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 全新创作
                    InkCard(
                        onClick = {
                            showDialog = false
                            showCreateDialog = true
                        }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("📖 全新创作", fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                            Text("从零开始，自由发挥", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    // 参考小说创作
                    InkCard(
                        onClick = {
                            showDialog = false
                            onAnalyzeNovel()
                        }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("📚 参考小说创作", fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                            Text("分析TXT，提取元素辅助创作", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var genre by remember { mutableStateOf("玄幻") }
        AlertDialog(onDismissRequest = { showCreateDialog = false }, containerColor = BgCard,
            title = { Text("全新创作", fontFamily = SerifFamily) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("书名") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("题材", fontSize = 13.sp, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("玄幻", "科幻", "都市", "悬疑", "言情", "奇幻").forEach { g ->
                            FilterChip(selected = genre == g, onClick = { genre = g }, label = { Text(g, fontSize = 12.sp) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (title.isNotBlank()) { onCreate(title, genre); showCreateDialog = false } }) { Text("创建", color = Accent) } },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } }
        )
    }

    // 删除确认对话框
    if (deletingBook != null) {
        AlertDialog(onDismissRequest = { deletingBook = null }, containerColor = BgCard,
            title = { Text("确认删除", fontFamily = SerifFamily, fontWeight = FontWeight.W600) },
            text = {
                Text("确定要删除「${deletingBook!!.title}」吗？\n此操作不可恢复，所有章节和创作数据将被永久删除。",
                    fontSize = 13.sp, color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deletingBook!!)
                    deletingBook = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deletingBook = null }) { Text("取消") }
            }
        )
    }

    // 长按显示完整书名
    if (showFullTitle != null) {
        AlertDialog(onDismissRequest = { showFullTitle = null }, containerColor = BgCard,
            title = { Text("完整书名", fontFamily = SerifFamily, fontWeight = FontWeight.W600) },
            text = {
                Text(showFullTitle!!, fontSize = 15.sp, color = TextPrimary,
                    fontFamily = SerifFamily, fontWeight = FontWeight.W500)
            },
            confirmButton = {
                TextButton(onClick = { showFullTitle = null }) { Text("关闭") }
            }
        )
    }

    // 重命名对话框
    if (renamingBook != null) {
        AlertDialog(onDismissRequest = { renamingBook = null }, containerColor = BgCard,
            title = { Text("重命名", fontFamily = SerifFamily, fontWeight = FontWeight.W600) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("原名：${renamingBook!!.title}", fontSize = 12.sp, color = TextSecondary)
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it },
                        label = { Text("新书名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank() && renameText != renamingBook!!.title) {
                        onRename(renamingBook!!, renameText.trim())
                    }
                    renamingBook = null
                }) { Text("确认", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { renamingBook = null }) { Text("取消") }
            }
        )
    }
}
