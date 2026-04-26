package com.example.personaeval.ui.screens.studentdetail

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
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.EvaluationEntity
import java.text.SimpleDateFormat
import java.util.*

data class StudentDetailUiState(
    val student: StudentEntity? = null,
    val traits: List<StudentTraitsEntity> = emptyList(),
    val evaluations: List<EvaluationEntity> = emptyList(),
    val editMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    uiState: StudentDetailUiState,
    editMode: Boolean,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAddTrait: (String, Float) -> Unit,
    onUpdateTrait: (StudentTraitsEntity) -> Unit,
    onDeleteTrait: (Int) -> Unit,
    onUpdatePersonalEval: (Int, String?) -> Unit,
    onUpdateSuggestion: (Int, String?) -> Unit
) {
    val actualEditMode = uiState.editMode
    var showAddTraitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.student?.name ?: "学生详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    if (actualEditMode) {
                        IconButton(onClick = { showAddTraitDialog = true }) {
                            Icon(Icons.Default.Add, "添加性格")
                        }
                    }
                    IconButton(onClick = onToggleEditMode) {
                        Icon(
                            if (actualEditMode) Icons.Default.Close else Icons.Default.Settings,
                            if (actualEditMode) "退出编辑" else "编辑模式"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val student = uiState.student
        if (student == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 基本信息 + 最后更新时间
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(student.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            // 最后更新时间
                            val updateTime = student.updatedAt ?: student.createdAt
                            if (updateTime != null) {
                                Text(
                                    "最后更新：${formatTimestamp(updateTime)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!student.traits.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(student.traits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // 长期性格画像 — 标签自动换行
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("长期性格画像", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.traits.isEmpty()) {
                            Text("暂无性格特点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            // 使用 FlowRow 实现自动换行
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (trait in uiState.traits) {
                                    var isEditing by remember { mutableStateOf(false) }
                                    var editName by remember { mutableStateOf(trait.trait) }
                                    var editPct by remember { mutableStateOf(trait.percentage) }

                                    if (isEditing) {
                                        // 编辑模式 — 展开为输入框
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = editName,
                                                onValueChange = { editName = it },
                                                modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            OutlinedTextField(
                                                value = editPct.toInt().toString(),
                                                onValueChange = { editPct = it.toFloatOrNull() ?: 0f },
                                                modifier = Modifier.width(50.dp).heightIn(min = 36.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                            Text("%", style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = {
                                                onUpdateTrait(trait.copy(trait = editName, percentage = editPct, updatedAt = System.currentTimeMillis()))
                                                isEditing = false
                                            }) { Text("保存", style = MaterialTheme.typography.labelSmall) }
                                        }
                                    } else {
                                        // 展示模式 — 标签式
                                        SuggestionChip(
                                            onClick = { if (actualEditMode) isEditing = true },
                                            label = { Text("${trait.trait} ${trait.percentage.toInt()}%", style = MaterialTheme.typography.labelSmall) },
                                            icon = if (actualEditMode) {
                                                { Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(12.dp)) }
                                            } else null
                                        )
                                        if (actualEditMode) {
                                            IconButton(onClick = { onDeleteTrait(trait.id) }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Close, "删除", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 个人评价独立模块 — 弹性显示（仅有内容时显示，无内容不占空间）
                if (!student.personalEval.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            var isEditingEval by remember { mutableStateOf(false) }
                            var editText by remember { mutableStateOf(student.personalEval ?: "") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("个人评价", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (!isEditingEval) {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("personalEval", student.personalEval))
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) { Text("复制", style = MaterialTheme.typography.labelSmall) }
                                    if (actualEditMode) {
                                        TextButton(
                                            onClick = { isEditingEval = true; editText = student.personalEval ?: "" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("编辑", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isEditingEval) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    maxLines = 6
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { isEditingEval = false }) { Text("取消") }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(onClick = {
                                        onUpdatePersonalEval(student.id, editText.ifBlank { null })
                                        isEditingEval = false
                                    }) { Text("保存") }
                                }
                            } else {
                                Text(student.personalEval, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 提升建议独立模块 — 弹性显示（仅有内容时显示，无内容不占空间）
                if (!student.suggestion.isNullOrBlank()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            var isEditingSugg by remember { mutableStateOf(false) }
                            var editText by remember { mutableStateOf(student.suggestion ?: "") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("提升建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (!isEditingSugg) {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("suggestion", student.suggestion))
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) { Text("复制", style = MaterialTheme.typography.labelSmall) }
                                    if (actualEditMode) {
                                        TextButton(
                                            onClick = { isEditingSugg = true; editText = student.suggestion ?: "" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("编辑", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isEditingSugg) {
                                OutlinedTextField(
                                    value = editText,
                                    onValueChange = { editText = it },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                    maxLines = 6
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { isEditingSugg = false }) { Text("取消") }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Button(onClick = {
                                        onUpdateSuggestion(student.id, editText.ifBlank { null })
                                        isEditingSugg = false
                                    }) { Text("保存") }
                                }
                            } else {
                                Text(student.suggestion, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 已采纳历史评价 — 弹性显示（有评价才显示）
                if (uiState.evaluations.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("已采纳评价 (${uiState.evaluations.size}条)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            for (ev in uiState.evaluations) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val badgeColors = mapOf(
                                                "80/20" to Color(0xFF4CAF50),
                                                "65/35" to Color(0xFF2196F3),
                                                "90/10" to Color(0xFFFFC107)
                                            )
                                            Surface(
                                                color = badgeColors[ev.template] ?: Color.Gray,
                                                shape = MaterialTheme.shapes.extraSmall
                                            ) {
                                                Text(ev.template, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("✓ 已采纳", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                            Spacer(modifier = Modifier.weight(1f))
                                            // 复制按钮
                                            TextButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("evaluation", ev.content))
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("复制", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Text(ev.content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                        // 弹性显示个人评价和提升建议
                                        if (!ev.personalEval.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("💬 个人评价：${ev.personalEval}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!ev.suggestion.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("💡 提升建议：${ev.suggestion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTraitDialog) {
        AddTraitDialog(
            onDismiss = { showAddTraitDialog = false },
            onConfirm = { name, pct ->
                showAddTraitDialog = false
                onAddTrait(name, pct)
            }
        )
    }
}

@Composable
private fun AddTraitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pct by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加性格特点") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("特点名称") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = pct, onValueChange = { pct = it }, label = { Text("占比(%)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, pct.toFloatOrNull() ?: 0f) }, enabled = name.isNotBlank()) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
