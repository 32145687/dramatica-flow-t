// ===== AppSidebar.kt =====
// 可折叠侧边栏 - 对应 HTML .sidebar
package com.dramatica.flow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.ui.theme.*

// 导航项
enum class NavPage(
    val label: String,
    val icon: ImageVector,
    val section: String,
    val badge: String? = null,
    val hasDot: Boolean = false
) {
    Home("书架", NavIcons.Home, "创作空间"),
    Flow("创作流程", NavIcons.Flow, "创作空间", badge = "3/7", hasDot = true),
    ReferenceFlow("参考创作", NavIcons.ReferenceFlow, "创作空间"),
    Characters("角色档案", NavIcons.Characters, "创作空间"),
    Writing("写作", NavIcons.Writing, "创作空间"),
    WritingSkill("写作技能", NavIcons.Style, "创作空间"),
    NovelAnalysis("参考小说", NavIcons.Book, "创作空间"),
    Tracking("一致性追踪", NavIcons.Tracking, "工具面板"),
    Hooks("伏笔追踪", NavIcons.Hooks, "工具面板"),
    Timeline("时间线", NavIcons.Timeline, "工具面板"),
    TxtSplit("TXT拆分", NavIcons.Split, "工具面板"),
    Settings("设置", NavIcons.Settings, "工具面板")
}

@Composable
fun AppSidebar(
    currentPage: NavPage,
    expanded: Boolean,
    currentBookIsReference: Boolean = false,
    onPageSelect: (NavPage) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (expanded) 220.dp else 56.dp,
        animationSpec = tween(320, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)),
        label = "sidebar_width"
    )

    Column(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(BgSidebar)
            .padding(top = 24.dp) // safe area
    ) {
        // ---- Header: "墨" / "墨迹·创作" ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = if (expanded) 16.dp else 0.dp),
            contentAlignment = if (expanded) Alignment.CenterStart else Alignment.Center
        ) {
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "墨迹",
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.W600,
                        fontSize = 17.sp,
                        color = Accent
                    )
                    Text(
                        " · 创作",
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.W300,
                        fontSize = 14.sp,
                        color = TextTertiary
                    )
                }
            } else {
                Text(
                    "墨",
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = 18.sp,
                    color = Accent
                )
            }
        }

        InkDividerLight()

        // ---- Navigation ----
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            var currentSection = ""

            NavPage.entries.forEach { page ->
                // 根据书籍类型合并"创作流程"和"参考创作"入口
                if (currentBookIsReference && page == NavPage.Flow) return@forEach  // 参考书籍时隐藏"创作流程"
                if (!currentBookIsReference && page == NavPage.ReferenceFlow) return@forEach  // 非参考书籍时隐藏"参考创作"

                // 分组标签
                if (page.section != currentSection) {
                    currentSection = page.section
                    if (expanded) {
                        Text(
                            text = currentSection,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.W600,
                            color = TextTertiary,
                            letterSpacing = 0.12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                val isSelected = currentPage == page ||
                    (currentBookIsReference && page == NavPage.ReferenceFlow && currentPage == NavPage.Flow)
                val targetPage = if (currentBookIsReference && page == NavPage.ReferenceFlow) {
                    NavPage.ReferenceFlow
                } else {
                    page
                }

                SidebarItem(
                    page = page,
                    selected = isSelected,
                    expanded = expanded,
                    onClick = { onPageSelect(targetPage) }
                )
            }
        }

        // ---- Toggle button ----
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(InkRadius.sm))
                .background(Accent.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ),
            contentAlignment = Alignment.Center
        ) {
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = NavIcons.ChevronLeft,
                        contentDescription = "收起",
                        modifier = Modifier.size(16.dp),
                        tint = TextTertiary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("收起", fontSize = 12.sp, color = TextTertiary)
                }
            } else {
                Icon(
                    imageVector = NavIcons.ChevronRight,
                    contentDescription = "展开",
                    modifier = Modifier.size(16.dp),
                    tint = TextTertiary
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SidebarItem(
    page: NavPage,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        selected -> AccentBg
        else -> Color.Transparent
    }
    val textColor = when {
        selected -> Accent
        else -> TextSecondary
    }
    val iconAlpha = if (selected) 1f else 0.65f

    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(InkRadius.sm))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 选中指示条
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(0.dp, 2.dp, 2.dp, 0.dp))
                        .background(Accent)
                )
            }

            // 图标
            Box(
                modifier = Modifier.width(if (expanded) 56.dp else 56.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = page.label,
                    modifier = Modifier.size(20.dp),
                    tint = textColor.copy(alpha = iconAlpha)
                )
            }

            // 标签（展开时显示）
            if (expanded) {
                Text(
                    text = page.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W500,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // Badge
                if (page.badge != null) {
                    Text(
                        text = page.badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W600,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentLight)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            } else {
                // 收起时：badge 用小圆点代替
                if (page.hasDot) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Accent)
                    )
                }
            }
        }
    }
}

// ==================== SVG 图标映射 ====================
object NavIcons {
    val Home = Icons.Outlined.Home
    val Flow = Icons.Outlined.AutoAwesome
    val Characters = Icons.Outlined.People
    val Writing = Icons.Outlined.Edit
    val Tracking = Icons.Outlined.Analytics
    val Hooks = Icons.Outlined.Flag
    val Timeline = Icons.Outlined.Timeline
    val ChevronRight = Icons.Outlined.ChevronRight
    val ChevronLeft = Icons.Outlined.ChevronLeft
    val Search = Icons.Outlined.Search
    val Book = Icons.Outlined.Book
    val Style = Icons.Outlined.Brush
    val Add = Icons.Outlined.Add
    val ArrowBack = Icons.Outlined.ArrowBack
    val Send = Icons.Outlined.Send
    val Settings = Icons.Outlined.Settings
    val KeyboardArrowLeft = Icons.Outlined.KeyboardArrowLeft
    val KeyboardArrowRight = Icons.Outlined.KeyboardArrowRight
    val Undo = Icons.Outlined.Undo
    val SmartToy = Icons.Outlined.SmartToy
    val Refresh = Icons.Outlined.Refresh
    val ExpandMore = Icons.Outlined.ExpandMore
    val Check = Icons.Outlined.Check
    val Close = Icons.Outlined.Close
    val Delete = Icons.Outlined.Delete
    val Split = Icons.Outlined.CallSplit
    val ReferenceFlow = Icons.Outlined.AutoStories
}
