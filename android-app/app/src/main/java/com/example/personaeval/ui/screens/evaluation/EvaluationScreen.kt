package com.example.personaeval.ui.screens.evaluation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personaeval.data.local.entity.EvaluationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    studentName: String,
    evaluations: List<EvaluationEntity>,
    isLoading: Boolean,
    editMode: Boolean,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAdopt: (Int, Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    val templateColors = mapOf(
        "80/20" to Pair(Color(0xFF4CAF50), Color(0xFFE8F5E9)),
        "65/35" to Pair(Color(0xFF2196F3), Color(0xFFE3F2FD)),
        "90/10" to Pair(Color(0xFFFFC107), Color(0xFFFFF8E1)),
    )
    val templateLabels = mapOf(
        "80/20" to "偏夸奖",
        "65/35" to "平衡",
        "90/10" to "鼓励",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$studentName - 评价") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = onToggleEditMode) {
                        Icon(
                            if (editMode) Icons.Default.Close else Icons.Default.Settings,
                            if (editMode) "退出编辑" else "编辑模式"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("正在生成评价...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (evaluations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无评价，请先生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // 3 列横排
            Row(
                modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (templateKey in listOf("80/20", "65/35", "90/10")) {
                    val templateEvals = evaluations.filter { it.template == templateKey }
                    val colors = templateColors[templateKey] ?: Pair(Color.Gray, Color.LightGray)
                    val label = templateLabels[templateKey] ?: templateKey

                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 模板标题
                        Surface(
                            color = colors.first,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "$templateKey · $label",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        for (ev in templateEvals) {
                            EvalCard(
                                evaluation = ev,
                                color = colors.second,
                                accentColor = colors.first,
                                editMode = editMode,
                                onAdopt = { onAdopt(ev.id, ev.studentId, ev.template) },
                                onDelete = { onDelete(ev.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvalCard(
    evaluation: EvaluationEntity,
    color: Color,
    accentColor: Color,
    editMode: Boolean,
    onAdopt: () -> Unit,
    onDelete: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                evaluation.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (editMode) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                if (evaluation.isAdopted) {
                    Text("✓ 已采纳", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                }
                TextButton(
                    onClick = {
                        // 复制到剪贴板
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("evaluation", evaluation.content))
                        // 标记采纳
                        onAdopt()
                        copied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (copied) "已复制" else "采纳并复制",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
