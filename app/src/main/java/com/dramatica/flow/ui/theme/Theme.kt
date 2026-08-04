// ===== Theme.kt =====
package com.dramatica.flow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 字体：中文衬线用于标题，无衬线用于正文
// 实际项目中需要添加 Noto Serif SC / Noto Sans SC 字体文件到 res/font
// 这里用系统字体作为 fallback
val SerifFamily = FontFamily.Default      // 使用系统默认字体，避免厂商竖排衬线字体
val SansFamily = FontFamily.SansSerif   // 替换为 Font(R.font.noto_sans_sc)

val InkWritingTypography = Typography(
    // 大标题 - 问候语
    headlineLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.W300,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        color = TextPrimary
    ),
    // 页面标题
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.W600,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        color = TextPrimary
    ),
    // 卡片标题
    titleLarge = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    // 正文
    bodyLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    // 辅助文字
    bodySmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = TextTertiary
    ),
    // 标签
    labelLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextTertiary
    ),
    labelSmall = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.W500,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        color = TextTertiary
    )
)

private val InkWritingColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = BgCard,
    primaryContainer = AccentBg,
    onPrimaryContainer = Accent,
    secondary = TextSecondary,
    onSecondary = BgCard,
    secondaryContainer = BgWarm,
    onSecondaryContainer = TextPrimary,
    tertiary = Info,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgWarm,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderLight,
    error = Danger,
    onError = BgCard
)

@Composable
fun InkWritingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InkWritingColorScheme,
        typography = InkWritingTypography,
        content = content
    )
}
