package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun TrackingScreen(
    book: BookEntity?, hooks: List<HookEntity>,
    causalChain: List<CausalLinkEntity>, relationships: List<RelationshipEntity>,
    emotions: List<EmotionEntity>,
    onNavigateToFlow: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (book != null) {
            BookBanner(
                name = book.title, genre = book.genre,
                chapters = book.currentChapter, words = book.currentChapter * book.targetWords,
                color = Accent, onClick = {}
            )
        } else {
            Text("一致性追踪", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 19.sp,
                modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp))
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (causalChain.isNotEmpty()) {
                InkCard {
                    Column(Modifier.padding(16.dp)) {
                        TrackTitle(Accent, "因果链（${causalChain.size}条）")
                        Spacer(Modifier.height(12.dp))
                        causalChain.forEach { link ->
                            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("Ch.${link.chapter}", fontSize = 10.sp, fontWeight = FontWeight.W600, color = Accent,
                                    modifier = Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("因：${link.cause}", fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
                                    Text("事：${link.event}", fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                                    Text("果：${link.consequence}", fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (hooks.isNotEmpty()) {
                InkCard {
                    Column(Modifier.padding(16.dp)) {
                        TrackTitle(Accent, "伏笔追踪（${hooks.size}条）")
                        Spacer(Modifier.height(12.dp))
                        hooks.forEach { hook ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val dotColor = when (hook.status) { "open" -> Accent; "resolved" -> Success; else -> Danger }
                                Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(dotColor))
                                Text(hook.description, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                                Text("Ch.${hook.plantedChapter}", fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }

            if (relationships.isNotEmpty()) {
                InkCard {
                    Column(Modifier.padding(16.dp)) {
                        TrackTitle(Success, "关系网络（${relationships.size}条）")
                        Spacer(Modifier.height(12.dp))
                        relationships.forEach { rel ->
                            Column(Modifier.padding(bottom = 10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${rel.characterA} ↔ ${rel.characterB}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${if (rel.strength > 0) "+" else ""}${rel.strength}", fontSize = 11.sp, color = TextTertiary)
                                }
                                InkProgressBar(((rel.strength + 100) / 2f).coerceIn(0f, 100f), Modifier.fillMaxWidth().padding(top = 4.dp),
                                    height = 5.dp,
                                    color = if (rel.strength >= 30) Success else if (rel.strength <= -30) Danger else Info)
                            }
                        }
                    }
                }
            }

            if (emotions.isNotEmpty()) {
                InkCard {
                    Column(Modifier.padding(16.dp)) {
                        TrackTitle(Info, "情感弧线")
                        Spacer(Modifier.height(12.dp))
                        emotions.groupBy { it.characterId }.forEach { (charId, snaps) ->
                            Text(charId, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom) {
                                snaps.takeLast(15).forEach { snap ->
                                    Box(Modifier.weight(1f).fillMaxHeight(fraction = snap.intensity / 10f)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(Accent))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            if (causalChain.isEmpty() && hooks.isEmpty() && relationships.isEmpty() && emotions.isEmpty()) {
                EmptyState("📊", "暂无追踪数据", "在「创作流程」页面添加伏笔和因果链", "前往创作流程") { onNavigateToFlow() }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TrackTitle(dotColor: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(3.5.dp)).background(dotColor))
        Text(title, fontFamily = SerifFamily, fontWeight = FontWeight.W500, fontSize = 14.sp)
    }
}
