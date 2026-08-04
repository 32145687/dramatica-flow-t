// ===== Components.kt =====
// 通用可复用组件 - 从 HTML CSS 类映射而来
package com.dramatica.flow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.ui.theme.*

// ==================== 圆角常量 ====================
object InkRadius {
    val sm = 8.dp    // --radius-sm
    val md = 12.dp   // --radius
    val lg = 16.dp   // --radius-lg
    val xl = 20.dp
    val full = 50    // 百分比，用于圆形
}

// ==================== 阴影 ====================
object InkShadow {
    @Composable
    fun sm() = Modifier.shadow(
        elevation = 2.dp,
        shape = RoundedCornerShape(InkRadius.md),
        ambientColor = TextPrimary.copy(alpha = 0.06f),
        spotColor = TextPrimary.copy(alpha = 0.06f)
    )

    @Composable
    fun md() = Modifier.shadow(
        elevation = 8.dp,
        shape = RoundedCornerShape(InkRadius.md),
        ambientColor = TextPrimary.copy(alpha = 0.10f),
        spotColor = TextPrimary.copy(alpha = 0.10f)
    )

    @Composable
    fun lg() = Modifier.shadow(
        elevation = 16.dp,
        shape = RoundedCornerShape(InkRadius.lg),
        ambientColor = TextPrimary.copy(alpha = 0.14f),
        spotColor = TextPrimary.copy(alpha = 0.14f)
    )
}

// ==================== 卡片 ====================
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(InkRadius.md)
    val baseMod = modifier
        .fillMaxWidth()
        .then(InkShadow.sm())
        .clip(shape)
        .background(BgCard, shape)
        .border(1.dp, BorderLight, shape)

    if (onClick != null) {
        Column(
            modifier = baseMod.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
            content = content
        )
    } else {
        Column(modifier = baseMod, content = content)
    }
}

// ==================== 按钮 ====================
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(InkRadius.sm)
    val colors = when (variant) {
        ButtonVariant.Primary -> ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = BgCard
        )
        ButtonVariant.Outline -> ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = TextSecondary
        )
    }

    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            shape = shape,
            colors = colors,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        ButtonVariant.Outline -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            shape = shape,
            colors = colors,
            enabled = enabled,
            border = BorderStroke(1.dp, if (enabled) Border else BorderLight),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

enum class ButtonVariant { Primary, Outline }

// ==================== 圆形头像 ====================
@Composable
fun CharacterAvatar(
    letter: String,
    type: Color,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(type),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.W600,
            fontSize = (size.value * 0.38).sp
        )
    }
}

// ==================== 标签 ====================
@Composable
fun InkTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(AccentBg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.W500,
        color = Accent
    )
}

// ==================== 统计芯片（首页横向滚动） ====================
@Composable
fun StatChip(
    value: String,
    label: String,
    fillPercent: Float,  // 0-100
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .clip(RoundedCornerShape(InkRadius.md))
            .background(BgCard)
            .border(1.dp, BorderLight, RoundedCornerShape(InkRadius.md))
            .padding(12.dp, 10.dp)
    ) {
        Text(
            text = value,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.W600,
            fontSize = 21.sp,
            color = TextPrimary,
            lineHeight = 24.sp
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextTertiary,
            modifier = Modifier.padding(top = 2.dp)
        )
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BorderLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = fillPercent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accent)
            )
        }
    }
}

// ==================== 空状态 ====================
@Composable
fun EmptyState(
    icon: String,
    title: String,
    desc: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp, 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 45.sp, modifier = Modifier.padding(bottom = 14.dp))
        Text(
            title,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.W500,
            fontSize = 17.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            desc,
            fontSize = 13.sp,
            color = TextTertiary,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        InkButton(text = buttonText, onClick = onButtonClick)
    }
}

// ==================== 顶部栏按钮 ====================
@Composable
fun TopbarButton(
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(InkRadius.sm))
            .background(if (accent) AccentBg else BgWarm)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = if (accent) Accent else if (enabled) TextSecondary else TextTertiary
        )
    }
}

// ==================== 进度条 ====================
@Composable
fun InkProgressBar(
    progress: Float,  // 0-100
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    color: Color = Accent,
    trackColor: Color = BorderLight
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

// ==================== 分割线 ====================
@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = Border,
        thickness = 1.dp
    )
}

// ==================== 轻量分割线 ====================
@Composable
fun InkDividerLight(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = BorderLight,
        thickness = 1.dp
    )
}

// ==================== 区域标题 ====================
@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.W500,
            fontSize = 14.sp
        )
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                fontSize = 12.sp,
                color = Accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction
                )
            )
        }
    }
}

// ==================== Toast ====================
@Composable
fun InkToast(message: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = message,
                modifier = Modifier
                    .clip(RoundedCornerShape(InkRadius.md))
                    .background(TextPrimary)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                color = Color.White,
                fontSize = 13.sp
            )
        }
    }
}

// ==================== 书籍封面色块 ====================
@Composable
fun BookCoverBlock(
    letter: String,
    color: Color,
    size: Dp = 40.dp,
    fontSize: TextUnit = 14.sp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.W700,
            fontSize = fontSize
        )
    }
}

// ==================== 渐变横幅（书籍信息条） ====================
@Composable
fun BookBanner(
    name: String,
    genre: String,
    chapters: Int,
    words: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(AccentBg, AccentLight.copy(alpha = 0.15f))
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BookCoverBlock(name.first().toString(), color, 40.dp, 14.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.W600,
                fontSize = 15.sp,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(genre, fontSize = 11.sp, color = TextTertiary)
                Text("${chapters}章", fontSize = 11.sp, color = TextTertiary)
                Text("${"%,d".format(words)}字", fontSize = 11.sp, color = TextTertiary)
            }
        }
        Text("切换", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
    }
}

// ==================== 步骤进度条 ====================
@Composable
fun StepProgressBar(
    currentStep: Int,  // 0-based
    totalSteps: Int = 7,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InkRadius.lg))
            .background(BgCard)
            .border(1.dp, Border, RoundedCornerShape(InkRadius.lg))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until totalSteps) {
            val color = when {
                i < currentStep -> Success
                i == currentStep -> Accent
                else -> BorderLight
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ==================== 自定义开关 ====================
@Composable
fun InkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        if (checked) Accent else Border,
        animationSpec = tween(200),
        label = "switchTrack"
    )
    val thumbOffset by animateFloatAsState(
        if (checked) 18f else 0f,
        animationSpec = tween(200),
        label = "switchThumb"
    )

    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.dp, y = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(1.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.15f))
        )
    }
}

// ==================== 统计数字行 ====================
@Composable
fun StatRow(
    items: List<Pair<String, String>>,  // (value, label)
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Border)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.W700,
                    fontSize = 20.sp,
                    color = TextPrimary,
                    lineHeight = 24.sp
                )
                Text(
                    label,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
