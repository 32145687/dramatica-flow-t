package com.dramatica.flow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dramatica.flow.data.model.Hook
import com.dramatica.flow.ui.viewmodel.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HooksScreen(
    novelId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TrackingViewModel = viewModel()
) {
    val hooks by viewModel.hooks.collectAsState()
    var selectedHook by remember { mutableStateOf<Hook?>(null) }
    
    LaunchedEffect(novelId) {
        viewModel.loadHooks(novelId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("伏笔追踪") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (hooks.isEmpty()) {
                EmptyState("暂无伏笔数据", "在写作时会自动提取伏笔信息")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(hooks) { hook ->
                        HookCard(
                            hook = hook,
                            onClick = { selectedHook = hook }
                        )
                    }
                }
            }
            
            // 伏笔详情对话框
            selectedHook?.let { hook ->
                HookDetailDialog(
                    hook = hook,
                    onDismiss = { selectedHook = null }
                )
            }
        }
    }
}

@Composable
fun HookCard(
    hook: Hook,
    onClick: () -> Unit
) {
    val backgroundColor = when (hook.status) {
        "active" -> Color(0xFFFFF3E0)
        "resolved" -> Color(0xFFE8F5E9)
        "overdue" -> Color(0xFFFFEBEE)
        else -> Color.White
    }
    
    val borderColor = when (hook.status) {
        "active" -> Color(0xFFFF9800)
        "resolved" -> Color(0xFF4CAF50)
        "overdue" -> Color(0xFFF44336)
        else -> Color.Gray
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hook.type.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                
                StatusChip(hook.status)
            }
            
            Text(
                text = hook.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "埋设：第${hook.setupChapter}章",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (hook.resolvedChapter != null) {
                    Text(
                        text = "回收：第${hook.resolvedChapter}章",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                } else if (hook.expectedResolveChapter != null) {
                    Text(
                        text = "预计：第${hook.expectedResolveChapter}章",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hook.status == "overdue") Color.Red else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    val (color, label) = when (status) {
        "active" -> Color(0xFFFF9800) to "进行中"
        "resolved" -> Color(0xFF4CAF50) to "已回收"
        "overdue" -> Color(0xFFF44336) to "超期预警"
        else -> Color.Gray to status
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun HookDetailDialog(
    hook: Hook,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${hook.type.replaceFirstChar { it.uppercase() }}详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("描述", hook.description)
                InfoRow("状态", when (hook.status) {
                    "active" -> "进行中"
                    "resolved" -> "已回收"
                    "overdue" -> "超期预警"
                    else -> hook.status
                })
                InfoRow("埋设章节", "第${hook.setupChapter}章")
                
                if (hook.resolvedChapter != null) {
                    InfoRow("回收章节", "第${hook.resolvedChapter}章")
                }
                
                if (hook.expectedResolveChapter != null) {
                    InfoRow("预计回收", "第${hook.expectedResolveChapter}章")
                }
                
                if (hook.relatedCharacters.isNotEmpty()) {
                    InfoRow("相关角色", hook.relatedCharacters.joinToString(", "))
                }
                
                if (hook.notes.isNotBlank()) {
                    InfoRow("备注", hook.notes)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}
