package com.dramatica.flow.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.data.*
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.screens.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DramaticaFlowApp(vm: MainViewModel) {
    val books by vm.books.collectAsState()
    val currentBook by vm.currentBook.collectAsState()
    val currentBookId by vm.currentBookId.collectAsState()
    val chapters by vm.chapters.collectAsState()
    val editingChapter by vm.editingChapter.collectAsState()
    val characters by vm.characters.collectAsState()
    val hooks by vm.hooks.collectAsState()
    val causalChainList by vm.causalChainList.collectAsState()
    val relationships by vm.relationships.collectAsState()
    val emotions by vm.emotions.collectAsState()
    val timeline by vm.timeline.collectAsState()

    var currentPage by remember { mutableStateOf(NavPage.Home) }
    var sidebarExpanded by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentBookIsReference by vm.currentBookIsReference.collectAsState()

    LaunchedEffect(Unit) {
        vm.toast.collect { toastMsg = it }
    }
    LaunchedEffect(toastMsg) {
        if (toastMsg != null) { kotlinx.coroutines.delay(2000); toastMsg = null }
    }

    // 当检测到当前书籍类型变化时，自动重定向到正确界面
    LaunchedEffect(currentBookIsReference, currentPage) {
        if (currentBookId.isBlank()) return@LaunchedEffect
        if (currentBookIsReference && currentPage == NavPage.Flow) {
            currentPage = NavPage.ReferenceFlow
        } else if (!currentBookIsReference && currentPage == NavPage.ReferenceFlow) {
            currentPage = NavPage.Flow
        }
    }

    val pageTitle = when (currentPage) {
        NavPage.Home -> "书架"
        NavPage.Flow -> "创作流程"
        NavPage.Characters -> "角色档案"
        NavPage.Writing -> "写作"
        NavPage.WritingSkill -> "写作技能"
        NavPage.NovelAnalysis -> "参考小说创作"
        NavPage.Tracking -> "一致性追踪"
        NavPage.Timeline -> "时间线"
        NavPage.TxtSplit -> "TXT拆分"
        NavPage.ReferenceFlow -> "参考创作"
        NavPage.Settings -> "设置"
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().statusBarsPadding()) {
            AppSidebar(
                currentPage = currentPage, expanded = sidebarExpanded,
                currentBookIsReference = currentBookIsReference,
                onPageSelect = { currentPage = it; sidebarExpanded = false },
                onToggle = { sidebarExpanded = !sidebarExpanded }
            )

            Column(Modifier.weight(1f).fillMaxHeight().background(BgPrimary)) {
                Row(
                    Modifier.fillMaxWidth().background(BgPrimary).padding(16.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(pageTitle, fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 18.sp)
                        Text(currentBook?.title ?: "Dramatica · 本地创作", fontSize = 11.sp, color = TextTertiary)
                    }
                    if (currentPage != NavPage.Home && currentBook != null) {
                        Row(Modifier.clip(RoundedCornerShape(20.dp)).background(AccentBg)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
                            Text(currentBook?.title ?: "", fontSize = 12.sp, fontWeight = FontWeight.W500, color = TextPrimary,
                                maxLines = 1)
                            Icon(NavIcons.ExpandMore, null, Modifier.size(14.dp), tint = TextTertiary)
                        }
                    }
                }
                InkDividerLight()

                // 书籍Tab栏（多书时显示，首页和设置页不显示）
                val showBookTabs = books.size >= 2 && currentPage !in listOf(NavPage.Home, NavPage.Settings)
                if (showBookTabs) {
                    BookTabBar(
                        books = books,
                        currentBookId = currentBookId,
                        onSelect = { vm.selectBook(it.id); currentPage = if (vm.currentBookIsReference.value) NavPage.ReferenceFlow else NavPage.Flow },
                        onCreate = { currentPage = NavPage.Home }
                    )
                    InkDividerLight()
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (currentPage) {
                        NavPage.Home -> HomeScreen(books, onSelect = { vm.selectBook(it.id); currentPage = if (vm.currentBookIsReference.value) NavPage.ReferenceFlow else NavPage.Flow },
                            onCreate = { t, g -> vm.createBook(t, g) }, onDelete = { vm.deleteBook(it) },
                            onExport = { book ->
                                coroutineScope.launch {
                                    val text = vm.exportNovel(book.id)
                                    val textBytes = text.toByteArray(Charsets.UTF_8)
                                    if (textBytes.size > 500 * 1024) {
                                        val file = File(context.cacheDir, "${book.title}_小说.txt")
                                        file.writeText(text, Charsets.UTF_8)
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context, "${context.packageName}.fileprovider", file
                                        )
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "分享小说")
                                        )
                                        android.widget.Toast.makeText(context, "文本过长，已通过文件分享", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("novel", text))
                                        android.widget.Toast.makeText(context, "已复制到剪贴板（${text.length}字）", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onAnalyzeNovel = { currentPage = NavPage.NovelAnalysis },
                            onRename = { book, newTitle -> vm.renameBook(book, newTitle) })
                        NavPage.NovelAnalysis -> NovelAnalysisScreen(
                            vm = vm,
                            onBack = { currentPage = NavPage.Home },
                            onConfirm = { title, genre, analysis ->
                                vm.createBookFromAnalysis(title, genre, analysis)
                                currentPage = NavPage.ReferenceFlow
                            }
                        )
                        NavPage.Flow -> FlowScreen(currentBookId, currentBook, characters, hooks, causalChainList, emotions, vm)
                        NavPage.ReferenceFlow -> ReferenceFlowScreen(currentBookId, currentBook, characters, hooks, causalChainList, emotions, vm, onBack = { currentPage = NavPage.Home })
                        NavPage.Characters -> CharactersScreen(currentBookId, currentBook, characters, vm)
                        NavPage.Writing -> WritingScreen(currentBookId, chapters, editingChapter, vm)
                        NavPage.WritingSkill -> WritingSkillScreen(vm, currentBookId, onBack = { currentPage = NavPage.Flow })
                        NavPage.Tracking -> TrackingScreen(currentBook, hooks, causalChainList, relationships, emotions,
                            onNavigateToFlow = { currentPage = NavPage.Flow })
                        NavPage.Timeline -> TimelineScreen(currentBook, currentBookId, timeline, vm)
                        NavPage.TxtSplit -> TxtSplitScreen(onBack = { currentPage = NavPage.Home })
                        NavPage.Settings -> SettingsScreen(onBack = { currentPage = NavPage.Home })
                    }
                }
            }
        }

        if (sidebarExpanded) {
            Box(Modifier.fillMaxSize().background(TextPrimary.copy(alpha = 0.18f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { sidebarExpanded = false })
        }

        AnimatedVisibility(
            visible = toastMsg != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
                toastMsg?.let {
                    Text(it, Modifier.clip(RoundedCornerShape(InkRadius.md)).background(TextPrimary).padding(18.dp, 9.dp),
                        color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 书籍Tab切换栏，支持多本书之间快速切换。
 * 在创作页面（Flow/Characters/Writing等）顶部显示。
 */
@Composable
private fun BookTabBar(
    books: List<BookEntity>,
    currentBookId: String,
    onSelect: (BookEntity) -> Unit,
    onCreate: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(BgPrimary).padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        books.forEach { book ->
            val isActive = book.id == currentBookId
            Surface(
                modifier = Modifier.clickable { onSelect(book) },
                shape = RoundedCornerShape(16.dp),
                color = if (isActive) Accent else Color.Transparent,
                border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, Border) else null
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isActive) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    }
                    Text(
                        book.title,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.W600 else FontWeight.W400,
                        color = if (isActive) Color.White else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // 添加新书按钮
        IconButton(
            onClick = onCreate,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "创建新书",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
