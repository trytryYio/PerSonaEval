package com.example.personaeval.ui.screens.classlist

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personaeval.data.local.entity.ClassEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassListScreen(
    classes: List<ClassEntity>,
    isLoading: Boolean,
    editMode: Boolean,
    onToggleEditMode: () -> Unit,
    onNavigateToStudents: (classId: Int, className: String) -> Unit,
    onAddClass: (grade: String, name: String) -> Unit,
    onUpdateClass: (ClassEntity) -> Unit,
    onDeleteClass: (Int) -> Unit,
    onAiExtract: (text: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("班级列表") },
                actions = {
                    if (editMode) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "添加班级")
                        }
                    }
                    IconButton(onClick = onToggleEditMode) {
                        Icon(
                            if (editMode) Icons.Default.Close else Icons.Default.Settings,
                            if (editMode) "退出编辑" else "编辑模式"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Key, "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!editMode) {
                ExtendedFloatingActionButton(
                    onClick = { showAiDialog = true },
                    icon = { Icon(Icons.Default.AutoAwesome, null) },
                    text = { Text("AI 识别") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (classes.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("暂无班级", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("点击下方「AI 识别」自动创建", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(classes, key = { it.id }) { cls ->
                        ClassCard(
                            cls = cls,
                            editMode = editMode,
                            onClick = { onNavigateToStudents(cls.id, "${cls.grade}${cls.name}") },
                            onUpdate = onUpdateClass,
                            onDelete = { onDeleteClass(cls.id) }
                        )
                    }
                }
            }
        }
    }

    // AI 识别 Dialog
    if (showAiDialog) {
        AiExtractDialog(
            onDismiss = { showAiDialog = false },
            onConfirm = { text ->
                showAiDialog = false
                onAiExtract(text)
            }
        )
    }

    // 手动添加 Dialog
    if (showAddDialog) {
        AddClassDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { grade, name ->
                showAddDialog = false
                onAddClass(grade, name)
            }
        )
    }
}

@Composable
private fun ClassCard(
    cls: ClassEntity,
    editMode: Boolean,
    onClick: () -> Unit,
    onUpdate: (ClassEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editGrade by remember { mutableStateOf(cls.grade) }
    var editName by remember { mutableStateOf(cls.name) }

    Card(
        onClick = if (editMode && !isEditing) { {} } else onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = editGrade,
                    onValueChange = { editGrade = it },
                    label = { Text("年级") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("班级") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    onUpdate(cls.copy(grade = editGrade, name = editName))
                    isEditing = false
                }) { Text("保存") }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${cls.grade}${cls.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${cls.studentCount ?: 0} 人",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (editMode) {
                    IconButton(onClick = { isEditing = true; editGrade = cls.grade; editName = cls.name }) {
                        Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AiExtractDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 识别学生信息") },
        text = {
            Column {
                Text(
                    "示例格式：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "八年级 a 班 5 个学生\n小明活泼好动喜欢上课跳舞，同时还是一个烦人精\n小红比较安静但是做题很慢\n小刚特别聪明就是有点懒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("粘贴学生信息") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("识别") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun AddClassDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var grade by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加班级") },
        text = {
            Column {
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("年级（如：八年级）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("班级（如：a 班）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(grade, name) },
                enabled = grade.isNotBlank() && name.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
