// ===== MainActivity.kt =====
package com.dramatica.flow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.dramatica.flow.ui.DramaticaFlowApp
import com.dramatica.flow.ui.theme.InkWritingTheme
import com.dramatica.flow.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏：透明背景 + 深色图标（适配浅色主题）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color(0xFFFAF8F5).toArgb(),  // 与 BgPrimary 一致
                darkScrim = Color(0xFF1A1A1A).toArgb()
            )
        )
        setContent { InkWritingTheme { DramaticaFlowApp(viewModel) } }
    }
}
