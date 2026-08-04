package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("api_settings", 0) }
    val scope = rememberCoroutineScope()

    var apiUrl by remember { mutableStateOf(prefs.getString("api_url", "https://api.deepseek.com") ?: "https://api.deepseek.com") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var apiModel by remember { mutableStateOf(prefs.getString("api_model", "deepseek-v4-flash") ?: "deepseek-v4-flash") }
    var temperature by remember { mutableStateOf(prefs.getFloat("temperature", 0.8f).toString()) }
    var maxTokens by remember { mutableStateOf(prefs.getInt("max_tokens", 4096).toString()) }
    var systemPrompt by remember { mutableStateOf(prefs.getString("system_prompt", "你是专业小说创作助手") ?: "你是专业小说创作助手") }

    var apiStatus by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPrimary).padding(16.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("API 设置", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 18.sp)
        }
        InkDividerLight()

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // API 配置
            InkCard(Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(InkRadius.sm))
                            .background(Accent), contentAlignment = Alignment.Center) {
                            Text("API", color = Color.White, fontWeight = FontWeight.W700, fontSize = 13.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("API 配置", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
                            Text("兼容 OpenAI 格式接口", fontSize = 11.sp, color = TextTertiary)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("API 地址", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("https://api.deepseek.com") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))

                    Spacer(Modifier.height(10.dp))
                    Text("API Key", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("sk-...") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            isTesting = true
                            apiStatus = "测试中..."
                            val url = apiUrl; val key = apiKey; val model = apiModel
                            scope.launch(Dispatchers.IO) {
                                var conn: HttpURLConnection? = null
                                try {
                                    val baseUrl = url.trim().let { if (!it.startsWith("http")) "https://$it" else it }.trimEnd('/')
                                    val urlObj = URL("$baseUrl/chat/completions")
                                    conn = urlObj.openConnection() as HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.setRequestProperty("Content-Type", "application/json")
                                    conn.setRequestProperty("Authorization", "Bearer ${key.trim()}")
                                    conn.doOutput = true
                                    conn.connectTimeout = 10000
                                    conn.readTimeout = 15000
                                    val body = """{"model":"${model}","messages":[{"role":"user","content":"你好"}],"max_tokens":10}"""
                                    conn.outputStream.write(body.toByteArray())
                                    val code = conn.responseCode
                                    withContext(Dispatchers.Main) {
                                        apiStatus = if (code == 200) "✅ 连接成功" else "❌ 失败 ($code)"
                                        isTesting = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        apiStatus = "❌ ${e.message?.take(50) ?: "连接失败"}"
                                        isTesting = false
                                    }
                                } finally {
                                    conn?.disconnect()
                                }
                            }
                        }, enabled = !isTesting) {
                            Text("测试连接", fontSize = 12.sp)
                        }
                    }
                    if (apiStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(apiStatus, fontSize = 11.sp, color = if (apiStatus.contains("成功")) Success else Danger)
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("模型名称", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = apiModel, onValueChange = { apiModel = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))
                }
            }

            Spacer(Modifier.height(12.dp))

            // 通用设置
            InkCard(Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(InkRadius.sm))
                            .background(Accent), contentAlignment = Alignment.Center) {
                            Text("⚙", color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("通用设置", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 14.sp)
                            Text("AI 对话通用参数", fontSize = 11.sp, color = TextTertiary)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Temperature (0~2)", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = temperature, onValueChange = { temperature = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))

                    Spacer(Modifier.height(10.dp))
                    Text("Max Tokens", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = maxTokens, onValueChange = { maxTokens = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))

                    Spacer(Modifier.height(10.dp))
                    Text("System Prompt", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.W500)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(value = systemPrompt, onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Border))
                }
            }

            Spacer(Modifier.height(20.dp))

            // 保存按钮
            InkButton(text = "保存设置", onClick = {
                prefs.edit().putString("api_url", apiUrl.trim()).putString("api_key", apiKey.trim())
                    .putString("api_model", apiModel.trim())
                    .putFloat("temperature", temperature.toFloatOrNull() ?: 0.8f)
                    .putInt("max_tokens", maxTokens.toIntOrNull() ?: 4096)
                    .putString("system_prompt", systemPrompt).apply()
                toastMsg = "设置已保存"
            }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
        }
    }

    if (toastMsg != null) {
        LaunchedEffect(toastMsg) {
            kotlinx.coroutines.delay(2000)
            toastMsg = null
        }
        Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
            Text(toastMsg!!, Modifier.clip(RoundedCornerShape(InkRadius.md)).background(TextPrimary).padding(18.dp, 9.dp),
                color = Color.White, fontSize = 13.sp)
        }
    }
}
