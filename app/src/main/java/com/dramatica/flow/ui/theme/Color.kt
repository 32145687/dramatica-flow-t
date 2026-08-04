// ===== Color.kt =====
// 从 HTML 提取的完整配色方案
package com.dramatica.flow.ui.theme

import androidx.compose.ui.graphics.Color

// 暖色纸质背景系统
val BgPrimary = Color(0xFFFAF8F5)       // --bg: 主背景
val BgWarm = Color(0xFFF5F0EA)          // --bg-warm: 暖色背景
val BgCard = Color(0xFFFFFFFF)          // --bg-card: 卡片背景
val BgSidebar = Color(0xFFF0EBE3)       // --bg-sidebar: 侧边栏

// 文字色阶
val TextPrimary = Color(0xFF2C2418)     // --text-primary: 主文字
val TextSecondary = Color(0xFF6B5D4F)   // --text-secondary: 次文字
val TextTertiary = Color(0xFF9C8E7E)    // --text-tertiary: 辅助文字

// 强调色（琥珀/铜色）
val Accent = Color(0xFFC47D3B)          // --accent: 主强调色
val AccentLight = Color(0xFFE8C9A0)     // --accent-light: 浅强调
val AccentBg = Color(0xFFFDF6EE)        // --accent-bg: 强调背景

// 边框
val Border = Color(0xFFE8E0D6)          // --border: 主边框
val BorderLight = Color(0xFFF0EBE3)     // --border-light: 浅边框

// 语义色
val Success = Color(0xFF5A8C5A)         // --success: 成功/完成
val Danger = Color(0xFFB85450)          // --danger: 危险/警告
val Info = Color(0xFF5A7A8C)            // --info: 信息

// 角色类型色
val ProtagonistColor = Accent           // 主角
val AntagonistColor = Danger            // 对立者
val ImpactColor = Info                  // 影响者
val GuardianColor = Success             // 守护者
val SidekickColor = Color(0xFF8B7EC8)   // 伙伴
val ContagonistColor = Color(0xFFD4A843) // 阻碍者
val SkepticColor = Color(0xFF7A9A9A)     // 怀疑者

// AI 模型色
val MiMoColor = Color(0xFF6C5CE7)
val DeepSeekColor = Color(0xFF00B894)

// 时间线事件类型色
val ConflictColor = Danger
val RevealColor = Success
val EmotionColor = Info
val ForeshadowColor = Accent
