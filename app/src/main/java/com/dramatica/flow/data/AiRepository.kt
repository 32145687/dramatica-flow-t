package com.dramatica.flow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("api_settings", 0)

    private fun getApiSettings(): ApiSettings {
        return ApiSettings(
            url = prefs.getString("api_url", "https://api.deepseek.com") ?: "https://api.deepseek.com",
            key = (prefs.getString("api_key", "") ?: "").trim(),
            model = prefs.getString("api_model", "deepseek-v4-flash") ?: "deepseek-v4-flash",
            temperature = prefs.getFloat("temperature", 0.8f),
            maxTokens = prefs.getInt("max_tokens", 4096),
            systemPrompt = prefs.getString("system_prompt",
                "你是专业小说创作助手。写作原则：\n" +
                "1. 用具体动作和感官细节代替抽象描述\n" +
                "2. 对话自然，短句为主（3-15字），符合人物性格\n" +
                "3. 段落短小精悍，避免超过200字的大段描述\n" +
                "4. 每300字至少有一次对话或动作\n" +
                "5. 避免AI套话：仿佛、忽然、竟然、不禁、宛如、猛地、顿时\n" +
                "6. 不要总结、不要评价、不要解释——直接叙述\n" +
                "7. 用角色的眼睛看世界，不要站在上帝视角评论"
            ) ?: "你是专业小说创作助手"
        )
    }

    suspend fun generateContent(
        prompt: String,
        maxTokens: Int = 4096,
        temperature: Float? = null,
        maxRetries: Int = 2
    ): AiResult = withContext(Dispatchers.IO) {
        val settings = getApiSettings()

        if (settings.url.isBlank() || settings.key.isBlank()) {
            return@withContext AiResult.Error("请先配置API密钥")
        }

        val baseUrl = settings.url.trim().let { if (!it.startsWith("http")) "https://$it" else it }.trimEnd('/')
        val fullUrl = "${baseUrl}/chat/completions"
        try {
            val parsedUrl = URL(fullUrl)
            val host = parsedUrl.host
            if (host.isBlank() || host == "chat" || host == "localhost") {
                return@withContext AiResult.Error("API地址无效，请检查设置中的URL（当前：$host）")
            }
        } catch (e: java.net.MalformedURLException) {
            return@withContext AiResult.Error("API地址格式错误：${fullUrl.take(50)}")
        }

        var lastError: AiResult.Error? = null
        for (attempt in 0..maxRetries) {
            if (attempt > 0) {
                kotlinx.coroutines.delay((1000L * (1 shl (attempt - 1))).coerceAtMost(8000))
            }
            var conn: HttpURLConnection? = null
            try {
                val url = URL(fullUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", "Bearer ${settings.key}")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 120000

                val requestBody = JSONObject().apply {
                    put("model", settings.model)
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", settings.systemPrompt))
                        put(JSONObject().put("role", "user").put("content", prompt))
                    })
                    put("max_tokens", maxTokens)
                    put("temperature", (temperature ?: settings.temperature).toDouble())
                }.toString()

                conn.outputStream.write(requestBody.toByteArray(Charsets.UTF_8))
                val responseCode = conn.responseCode
                when {
                    responseCode == 200 -> {
                        val response = conn.inputStream.bufferedReader().readText()
                        val content = extractContent(response)
                        return@withContext AiResult.Success(content)
                    }
                    responseCode in 429..599 -> {
                        val error = conn.errorStream?.bufferedReader()?.readText() ?: "服务端错误"
                        lastError = AiResult.Error("API错误($responseCode)${if (attempt > 0) " [重试${attempt}/$maxRetries]" else ""}: ${error.take(100)}")
                        continue
                    }
                    else -> {
                        val error = conn.errorStream?.bufferedReader()?.readText() ?: "请求错误"
                        return@withContext AiResult.Error("API错误($responseCode): ${error.take(200)}")
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                lastError = AiResult.Error("连接超时${if (attempt > 0) " [重试${attempt}/$maxRetries]" else ""}")
                continue
            } catch (e: java.io.IOException) {
                lastError = AiResult.Error("网络错误${if (attempt > 0) " [重试${attempt}/$maxRetries]" else ""}: ${e.message?.take(80)}")
                continue
            } catch (e: Exception) {
                return@withContext AiResult.Error("连接失败: ${e.message?.take(100) ?: "未知错误"}")
            } finally {
                conn?.disconnect()
            }
        }
        lastError ?: AiResult.Error("未知错误")
    }

    private fun extractContent(response: String): String {
        return try {
            val json = JSONObject(response)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } catch (e: Exception) {
            response
        }
    }
}

data class ApiSettings(
    val url: String,
    val key: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String
)

sealed class AiResult {
    data class Success(val content: String) : AiResult()
    data class Error(val message: String) : AiResult()
}