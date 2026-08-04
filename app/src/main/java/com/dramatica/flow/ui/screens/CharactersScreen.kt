package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import com.dramatica.flow.data.BookEntity
import com.dramatica.flow.data.CharacterEntity
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import com.dramatica.flow.ui.viewmodel.MainViewModel

@Composable
fun CharactersScreen(bookId: String, book: BookEntity?, characters: List<CharacterEntity>, vm: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<CharacterEntity?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (book != null) {
            BookBanner(
                name = book.title, genre = book.genre,
                chapters = book.currentChapter, words = book.currentChapter * book.targetWords,
                color = Accent, onClick = {}
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("角色档案", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 19.sp)
            }
        }

        if (characters.isEmpty()) {
            EmptyState("👥", "还没有角色", "点击添加你的第一个角色", "添加角色") { showDialog = true }
        } else {
            SectionHeader(title = "角色（${characters.size}）", actionText = "添加", onAction = { showDialog = true })
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                characters.forEach { ch ->
                    val typeColor = when (ch.type) {
                        "protagonist" -> ProtagonistColor; "antagonist" -> AntagonistColor
                        "impact" -> ImpactColor; "guardian" -> GuardianColor; else -> SidekickColor
                    }
                    InkCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(typeColor),
                                    contentAlignment = Alignment.Center) {
                                    Text(ch.avatar, color = Color.White, fontFamily = SerifFamily,
                                        fontWeight = FontWeight.W600, fontSize = 16.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(ch.name, fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 15.sp)
                                    Text(ch.role, fontSize = 11.sp, color = TextTertiary)
                                }
                                IconButton(
                                    onClick = { editingCharacter = ch },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(NavIcons.Writing, "编辑", Modifier.size(14.dp), tint = TextTertiary)
                                }
                            }
                            Text(ch.description, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
                            if (ch.tags.isNotBlank()) {
                                Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ch.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
                                        Text(tag.trim(), fontSize = 9.5.sp, fontWeight = FontWeight.W500,
                                            color = Accent, modifier = Modifier.clip(RoundedCornerShape(3.dp))
                                                .background(AccentBg).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加角色弹窗
    if (showDialog) {
        CharacterEditDialog(
            title = "添加角色",
            onDismiss = { showDialog = false },
            onConfirm = { name, role, type, desc ->
                vm.addCharacter(bookId, name, role, type, desc)
                showDialog = false
            }
        )
    }

    // 编辑角色弹窗
    editingCharacter?.let { ch ->
        CharacterEditDialog(
            title = "编辑角色",
            initialName = ch.name,
            initialRole = ch.role,
            initialType = ch.type,
            initialDesc = ch.description,
            onDismiss = { editingCharacter = null },
            onConfirm = { name, role, type, desc ->
                vm.updateCharacter(ch.uid, bookId, name, role, type, desc)
                editingCharacter = null
            }
        )
    }
}

@Composable
private fun CharacterEditDialog(
    title: String,
    initialName: String = "",
    initialRole: String = "主角",
    initialType: String = "protagonist",
    initialDesc: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, role: String, type: String, desc: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }
    var type by remember { mutableStateOf(initialType) }
    var desc by remember { mutableStateOf(initialDesc) }
    val roles = listOf("主角" to "protagonist", "对立者" to "antagonist", "影响者" to "impact",
        "守护者" to "guardian", "伙伴" to "sidekick")

    AlertDialog(onDismissRequest = onDismiss, containerColor = BgCard,
        title = { Text(title, fontWeight = FontWeight.W600) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("角色类型", fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { (label, value) ->
                        FilterChip(selected = type == value, onClick = { type = value; role = label },
                            label = { Text(label, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("简介") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, role, type, desc) }) { Text("保存", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
