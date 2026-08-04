package com.dramatica.flow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramatica.flow.ui.components.*
import com.dramatica.flow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class SplitFileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val outputFiles: MutableList<File> = mutableListOf(),
    var status: String = "等待",
    var progress: Float = 0f
)

@Composable
fun TxtSplitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var files by remember { mutableStateOf(listOf<SplitFileItem>()) }
    var splitSizeMB by remember { mutableIntStateOf(4) }
    var isSplitting by remember { mutableStateOf(false) }
    var outputFolder by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.let {
            val newItems = it.mapNotNull { uri ->
                try {
                    val name = getFileName(context, uri)
                    val size = getFileSize(context, uri)
                    SplitFileItem(uri, name, size)
                } catch (_: Exception) {
                    null
                }
            }
            files = files + newItems
        }
    }

    val outputDir: File by remember {
        derivedStateOf {
            File(context.getExternalFilesDir(null), "split_output")
        }
    }

    LaunchedEffect(Unit) {
        if (!outputDir.exists()) outputDir.mkdirs()
        outputFolder = outputDir.absolutePath
    }

    fun startSplit() {
        if (files.isEmpty() || isSplitting) return
        isSplitting = true

        coroutineScope.launch(Dispatchers.IO) {
            val sizeBytes = splitSizeMB * 1024 * 1024L
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outDir = File(outputDir, timeStamp)
            outDir.mkdirs()

            files.forEachIndexed { index, item ->
                try {
                    item.status = "拆分中"
                    item.progress = 0f

                    val inputStream = context.contentResolver.openInputStream(item.uri)
                    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    val baseName = item.name.substringBeforeLast(".txt")
                    val fileSize = item.size.coerceAtLeast(1L)

                    var partNum = 1
                    var currentSize = 0L
                    var currentWriter: BufferedWriter? = null
                    var currentFile: File? = null
                    var bytesRead = 0L

                    fun newPartFile(): File {
                        val partFile = File(outDir, "${baseName}${partNum}.txt")
                        item.outputFiles.add(partFile)
                        return partFile
                    }

                    var line: String?

                    while (true) {
                        line = reader.readLine()
                        if (line == null) break

                        val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8).size.toLong()

                        if (currentWriter == null || currentSize + lineBytes > sizeBytes) {
                            currentWriter?.close()
                            partNum++
                            currentFile = newPartFile()
                            currentWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(currentFile), "UTF-8"))
                            currentSize = 0L
                        }

                        currentWriter.write(line)
                        currentWriter.newLine()
                        currentSize += lineBytes
                        bytesRead += lineBytes

                        if (bytesRead % (200 * 1024L) == 0L) {
                            val progress = bytesRead.toFloat() / fileSize
                            item.progress = progress.coerceIn(0f, 0.99f)
                        }
                    }

                    currentWriter?.close()
                    reader.close()
                    inputStream?.close()

                    item.status = "完成（${item.outputFiles.size}个文件）"
                    item.progress = 1f

                } catch (e: Exception) {
                    item.status = "失败：${e.message?.take(30) ?: "未知错误"}"
                    item.progress = 0f
                }
            }

            withContext(Dispatchers.Main) {
                isSplitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        // 顶部栏
        Row(
            Modifier.fillMaxWidth().background(BgCard).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← 返回", color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("TXT文件拆分", fontFamily = SerifFamily, fontWeight = FontWeight.W600, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        InkDividerLight()

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 说明卡片
            InkCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📝 使用说明", fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                    Text("选择多个 TXT 文件，按设定大小自动拆分为多个小文件，拆分后的文件命名为「原文件名+序号.txt」",
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                    Text("输出位置：内部存储/Android/data/com.dramatica.flow/files/split_output/",
                        fontSize = 11.sp, color = TextTertiary)
                }
            }

            // 拆分大小设置
            InkCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚙️ 拆分大小", fontSize = 14.sp, fontWeight = FontWeight.W600, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(2, 4, 8, 16).forEach { mb ->
                            FilterChip(
                                selected = splitSizeMB == mb,
                                onClick = { splitSizeMB = mb },
                                label = { Text("${mb}MB", fontSize = 12.sp) }
                            )
                        }
                        Text("自定义：", fontSize = 12.sp, color = TextSecondary,
                            modifier = Modifier.padding(start = 8.dp))
                        OutlinedTextField(
                            value = splitSizeMB.toString(),
                            onValueChange = {
                                val v = it.toIntOrNull() ?: 4
                                splitSizeMB = v.coerceIn(1, 100)
                            },
                            singleLine = true,
                            modifier = Modifier.width(80.dp).height(44.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        Text("MB", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            // 已选文件列表
            InkCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📁 已选文件（${files.size}）", fontSize = 14.sp,
                            fontWeight = FontWeight.W600, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { files = emptyList() }, enabled = files.isNotEmpty()) {
                            Text("清空", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    if (files.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSidebar)
                                .clickable { filePicker.launch(arrayOf("text/plain")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📂", fontSize = 28.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("点击选择 TXT 文件（可多选）",
                                    fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                    } else {
                        files.forEachIndexed { index, item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgSidebar)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📄", fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name.take(30), fontSize = 12.sp,
                                        fontWeight = FontWeight.W500, color = TextPrimary,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text("${formatFileSize(item.size)} · ${item.status}",
                                        fontSize = 11.sp, color = TextTertiary)
                                    if (item.progress > 0f && item.progress < 1f) {
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { item.progress },
                                            modifier = Modifier.fillMaxWidth().height(2.dp),
                                            color = Accent,
                                            trackColor = BgCard
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    files = files.filterIndexed { i, _ -> i != index }
                                }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "删除",
                                        tint = TextTertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkButton(
                    text = "选择文件",
                    onClick = { filePicker.launch(arrayOf("text/plain")) },
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.weight(1f)
                )
                InkButton(
                    text = if (isSplitting) "拆分中..." else "开始拆分",
                    onClick = { startSplit() },
                    enabled = files.isNotEmpty() && !isSplitting,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "unknown.txt"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
    } catch (_: Exception) {}
    return name
}

private fun getFileSize(context: android.content.Context, uri: Uri): Long {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    return -1L
}

private fun formatFileSize(size: Long): String {
    if (size < 0) return "未知大小"
    return when {
        size >= 1024 * 1024 * 1024 -> "${String.format("%.1f", size / 1024.0 / 1024.0 / 1024.0)}GB"
        size >= 1024 * 1024 -> "${String.format("%.1f", size / 1024.0 / 1024.0)}MB"
        size >= 1024 -> "${String.format("%.1f", size / 1024.0)}KB"
        else -> "${size}B"
    }
}
