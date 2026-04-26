package com.example.personaeval.ui.screens.studentlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personaeval.data.local.entity.TemplateEntity

/**
 * 学习模板管理弹窗 — 选择+CRUD合一
 * 新增 selectedTemplateId / onSelectTemplate 参数，在同一弹窗中完成模板选择和CRUD
 */
@Composable
fun TemplateManagementDialog(
    templates: List<TemplateEntity>,
    selectedTemplateId: Int?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (Int?) -> Unit,
    onCreateTemplate: (name: String, personalEvalFormat: String, suggestionFormat: String) -> Unit,
    onUpdateTemplate: (TemplateEntity) -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    onAiGenerate: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("模板管理", style = MaterialTheme.typography.titleSmall)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 模板选择区
                Text("选择模板", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))

                Column(modifier = Modifier.selectableGroup()) {
                    // 系统默认选项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedTemplateId == null,
                                onClick = { onSelectTemplate(null) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTemplateId == null,
                            onClick = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("系统默认模板", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 用户模板选项
                    for (template in templates) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedTemplateId == template.id,
                                    onClick = { onSelectTemplate(template.id) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTemplateId == template.id,
                                onClick = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(template.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            // 编辑/删除按钮（非默认模板才显示）
                            if (!template.isDefault) {
                                IconButton(onClick = { editingTemplate = template }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteTemplate(template.id) }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                if (templates.isEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("暂无自定义模板，可点击下方按钮创建或AI生成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showAiDialog = true },
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("AI生成", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { showCreateDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("新建", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDismiss) { Text("关闭", style = MaterialTheme.typography.labelSmall) }
            }
        }
    )

    // 新建模板弹窗
    if (showCreateDialog) {
        TemplateEditDialog(
            title = "新建模板",
            initialName = "",
            initialPersonalEval = "",
            initialSuggestion = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, pEval, sugg ->
                onCreateTemplate(name, pEval, sugg)
                showCreateDialog = false
            }
        )
    }

    // 编辑模板弹窗
    if (editingTemplate != null) {
        TemplateEditDialog(
            title = "编辑模板",
            initialName = editingTemplate!!.name,
            initialPersonalEval = editingTemplate!!.personalEvalFormat,
            initialSuggestion = editingTemplate!!.suggestionFormat,
            onDismiss = { editingTemplate = null },
            onConfirm = { name, pEval, sugg ->
                onUpdateTemplate(editingTemplate!!.copy(name = name, personalEvalFormat = pEval, suggestionFormat = sugg, updatedAt = System.currentTimeMillis()))
                editingTemplate = null
            }
        )
    }

    // AI生成弹窗
    if (showAiDialog) {
        AiTemplateDialog(
            isLoading = isLoading,
            onDismiss = { showAiDialog = false },
            onGenerate = { desc -> onAiGenerate(desc) }
        )
    }
}

@Composable
private fun TemplateEditDialog(
    title: String,
    initialName: String,
    initialPersonalEval: String,
    initialSuggestion: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, personalEval: String, suggestion: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var personalEval by remember { mutableStateOf(initialPersonalEval) }
    var suggestion by remember { mutableStateOf(initialSuggestion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("模板名称", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例：课堂表现模板", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = personalEval,
                    onValueChange = { personalEval = it },
                    label = { Text("个人评价模块格式", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("描述如何评价学生...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = suggestion,
                    onValueChange = { suggestion = it },
                    label = { Text("提升建议模块格式", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("描述如何给出建议...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, personalEval, suggestion) }, enabled = name.isNotBlank()) { Text("保存", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelMedium) } }
    )
}

@Composable
private fun AiTemplateDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 生成模板", style = MaterialTheme.typography.titleSmall)
            }
        },
        text = {
            Column {
                Text("描述你想要的评价模板，AI 将自动生成包含\"个人评价\"和\"提升建议\"模块的完整模板。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("模板描述", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例：侧重学生课堂表现和作业完成情况", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelMedium) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onGenerate(description) },
                    enabled = description.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLoading) "生成中..." else "AI生成", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}
