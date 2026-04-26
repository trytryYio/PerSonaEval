package com.example.personaeval.ui.screens.studentlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personaeval.data.local.entity.StudentEntity
import com.example.personaeval.data.local.entity.StudentTraitsEntity
import com.example.personaeval.data.local.entity.EvaluationEntity
import com.example.personaeval.data.local.entity.TemplateEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    className: String,
    students: List<StudentEntity>,
    traitsMap: Map<Int, List<StudentTraitsEntity>>,
    evaluationsMap: Map<Int, List<EvaluationEntity>>,
    isLoading: Boolean,
    editMode: Boolean,
    lessonContent: String,
    classAtmosphere: String,
    performanceNotes: Map<Int, String>,
    templates: List<TemplateEntity>,
    selectedTemplateId: Int?,
    isTemplateLoading: Boolean,
    onToggleEditMode: () -> Unit,
    onBack: () -> Unit,
    onLessonContentChange: (String) -> Unit,
    onClassAtmosphereChange: (String) -> Unit,
    onPerformanceNotesChange: (Int, String) -> Unit,
    onAddStudent: (String) -> Unit,
    onUpdateStudent: (StudentEntity) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    onGenerateAll: () -> Unit,
    onGenerateSingle: (Int) -> Unit,
    onAdoptEvaluation: (Int, Int, String) -> Unit,
    onNavigateToStudentDetail: (Int) -> Unit,
    onSelectTemplate: (Int?) -> Unit,
    onShowTemplateManagement: () -> Unit,
    onCreateTemplate: (String, String, String) -> Unit,
    onUpdateTemplate: (TemplateEntity) -> Unit,
    onDeleteTemplate: (Int) -> Unit,
    onAiGenerateTemplate: (String) -> Unit,
    onAddTrait: (Int, String, Float) -> Unit,
    onUpdateTrait: (StudentTraitsEntity) -> Unit,
    onDeleteTrait: (Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showLessonDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var expandedStudentId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(className, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 模板选择 AssistChip
                    AssistChip(
                        onClick = { showTemplateDialog = true },
                        label = {
                            Text(
                                templates.find { it.id == selectedTemplateId }?.name ?: "默认模板",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (editMode) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加学生")
                        }
                    }
                    IconButton(onClick = onToggleEditMode) {
                        Icon(
                            if (editMode) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = if (editMode) "退出编辑" else "编辑模式"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 课堂信息卡片 — 点击弹出编辑
            Card(
                onClick = { showLessonDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    if (lessonContent.isNotBlank()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lessonContent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (classAtmosphere.isNotBlank()) {
                                Text(classAtmosphere, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        Text("点击设置课程内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                    }
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }

            // 学生列表
            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无学生", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(students, key = { it.id }) { student ->
                        val isExpanded = expandedStudentId == student.id
                        CompactStudentCard(
                            student = student,
                            traits = traitsMap[student.id] ?: emptyList(),
                            evaluations = evaluationsMap[student.id] ?: emptyList(),
                            editMode = editMode,
                            performanceNote = performanceNotes[student.id] ?: "",
                            isGenerating = isLoading,
                            isExpanded = isExpanded,
                            onPerformanceNotesChange = { onPerformanceNotesChange(student.id, it) },
                            onUpdate = onUpdateStudent,
                            onDelete = { onDeleteStudent(student.id) },
                            onGenerate = {
                                onGenerateSingle(student.id)
                                expandedStudentId = student.id
                            },
                            onToggleExpand = {
                                expandedStudentId = if (isExpanded) null else student.id
                            },
                            onDetailClick = { onNavigateToStudentDetail(student.id) },
                            onAdoptEvaluation = { evalId, template -> onAdoptEvaluation(evalId, student.id, template) },
                            onAddTrait = { name, pct -> onAddTrait(student.id, name, pct) },
                            onUpdateTrait = onUpdateTrait,
                            onDeleteTrait = onDeleteTrait
                        )
                    }
                }
            }
        }
    }

    // 课堂编辑弹窗
    if (showLessonDialog) {
        LessonEditDialog(
            lessonContent = lessonContent,
            classAtmosphere = classAtmosphere,
            isLoading = isLoading,
            onLessonContentChange = onLessonContentChange,
            onClassAtmosphereChange = onClassAtmosphereChange,
            onGenerateAll = {
                onGenerateAll()
                showLessonDialog = false
            },
            onDismiss = { showLessonDialog = false }
        )
    }

    // 模板管理弹窗
    if (showTemplateDialog) {
        TemplateManagementDialog(
            templates = templates,
            selectedTemplateId = selectedTemplateId,
            isLoading = isTemplateLoading,
            onDismiss = { showTemplateDialog = false },
            onSelectTemplate = onSelectTemplate,
            onCreateTemplate = onCreateTemplate,
            onUpdateTemplate = onUpdateTemplate,
            onDeleteTemplate = onDeleteTemplate,
            onAiGenerate = onAiGenerateTemplate
        )
    }

    if (showAddDialog) {
        AddStudentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                onAddStudent(name)
            }
        )
    }
}

/**
 * 课堂编辑弹窗
 */
@Composable
private fun LessonEditDialog(
    lessonContent: String,
    classAtmosphere: String,
    isLoading: Boolean,
    onLessonContentChange: (String) -> Unit,
    onClassAtmosphereChange: (String) -> Unit,
    onGenerateAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当前课堂", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = lessonContent,
                    onValueChange = onLessonContentChange,
                    label = { Text("课程内容", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例：while 循环", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = classAtmosphere,
                    onValueChange = onClassAtmosphereChange,
                    label = { Text("课堂整体氛围", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例：今天整体活跃但不愿动手", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelMedium) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onGenerateAll,
                    enabled = lessonContent.isNotBlank() && !isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLoading) "生成中..." else "批量生成", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}

/**
 * 紧凑学生卡片 — 约70dp高度，5+学生可见
 * 名字+badge一行，性格FlowRow最多4个+N，课堂表现点击弹窗编辑
 */
@Composable
private fun CompactStudentCard(
    student: StudentEntity,
    traits: List<StudentTraitsEntity>,
    evaluations: List<EvaluationEntity>,
    editMode: Boolean,
    performanceNote: String,
    isGenerating: Boolean,
    isExpanded: Boolean,
    onPerformanceNotesChange: (String) -> Unit,
    onUpdate: (StudentEntity) -> Unit,
    onDelete: () -> Unit,
    onGenerate: () -> Unit,
    onToggleExpand: () -> Unit,
    onDetailClick: () -> Unit,
    onAdoptEvaluation: (Int, String) -> Unit,
    onAddTrait: (String, Float) -> Unit,
    onUpdateTrait: (StudentTraitsEntity) -> Unit,
    onDeleteTrait: (Int) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(student.name) }
    val context = LocalContext.current
    var showTraitsDialog by remember { mutableStateOf(false) }
    var showPerformanceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // 第一行：名字 + badge + 操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = {
                        onUpdate(student.copy(name = editName, updatedAt = System.currentTimeMillis()))
                        isEditing = false
                    }) { Text("保存", style = MaterialTheme.typography.labelSmall) }
                } else {
                    // 名字
                    Text(
                        student.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onDetailClick() }
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // 课堂表现 badge
                    Surface(
                        color = if (performanceNote.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.clickable { showPerformanceDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (performanceNote.isNotBlank()) performanceNote else "表现...",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (performanceNote.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 右侧操作
                    if (editMode) {
                        IconButton(onClick = { isEditing = true; editName = student.name }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = !isGenerating,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(12.dp))
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("生成", style = MaterialTheme.typography.labelSmall)
                    }
                    if (evaluations.isNotEmpty()) {
                        IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "收起" else "展开"
                            )
                        }
                    }
                }
            }

            // 性格标签 — FlowRow 最多4个 + "+N"
            if (traits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val visibleTraits = traits.take(4)
                        val remainingCount = traits.size - 4
                        for (trait in visibleTraits) {
                            SuggestionChip(
                                onClick = { onDetailClick() },
                                label = {
                                    Text(
                                        "${trait.trait} ${trait.percentage.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        if (remainingCount > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "+$remainingCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    // 铅笔图标 — 编辑性格
                    IconButton(
                        onClick = { showTraitsDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Edit, "编辑性格", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 展开评价
            if (isExpanded && evaluations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))
                EvaluationTripleColumn(
                    evaluations = evaluations,
                    onAdopt = { evalId, template ->
                        onAdoptEvaluation(evalId, template)
                        val eval = evaluations.find { it.id == evalId }
                        if (eval != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("evaluation", eval.content))
                        }
                    }
                )
            }
        }
    }

    // 课堂表现弹窗
    if (showPerformanceDialog) {
        PerformanceNoteDialog(
            studentName = student.name,
            note = performanceNote,
            onNoteChange = onPerformanceNotesChange,
            onDismiss = { showPerformanceDialog = false }
        )
    }

    // 性格特点CRUD弹窗
    if (showTraitsDialog) {
        TraitsCrudDialog(
            studentName = student.name,
            traits = traits,
            onDismiss = { showTraitsDialog = false },
            onAddTrait = onAddTrait,
            onUpdateTrait = onUpdateTrait,
            onDeleteTrait = onDeleteTrait
        )
    }
}

/**
 * 课堂表现弹窗 — 点击badge弹出
 */
@Composable
private fun PerformanceNoteDialog(
    studentName: String,
    note: String,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$studentName — 课堂表现", style = MaterialTheme.typography.titleSmall) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                placeholder = { Text("输入课堂表现...", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成", style = MaterialTheme.typography.labelMedium) }
        }
    )
}

/**
 * 性格特点完整CRUD弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TraitsCrudDialog(
    studentName: String,
    traits: List<StudentTraitsEntity>,
    onDismiss: () -> Unit,
    onAddTrait: (String, Float) -> Unit,
    onUpdateTrait: (StudentTraitsEntity) -> Unit,
    onDeleteTrait: (Int) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newTraitName by remember { mutableStateOf("") }
    var newTraitPct by remember { mutableStateOf("50") }
    var editingTraitId by remember { mutableStateOf<Int?>(null) }
    var editName by remember { mutableStateOf("") }
    var editPct by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$studentName — 性格画像", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddForm = !showAddForm }) {
                    Icon(
                        if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (showAddForm) "取消添加" else "添加特点",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showAddForm) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("新增性格特点", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newTraitName,
                                    onValueChange = { newTraitName = it },
                                    placeholder = { Text("特点名", style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = newTraitPct,
                                    onValueChange = { newTraitPct = it },
                                    placeholder = { Text("占比", style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.width(60.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                Text("%", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    if (newTraitName.isNotBlank()) {
                                        onAddTrait(newTraitName, newTraitPct.toFloatOrNull() ?: 50f)
                                        newTraitName = ""
                                        newTraitPct = "50"
                                        showAddForm = false
                                    }
                                }) { Text("确认添加", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (traits.isEmpty()) {
                    Text("暂无性格特点，点击右上角 + 添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (trait in traits) {
                        if (editingTraitId == trait.id) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = editName,
                                        onValueChange = { editName = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    OutlinedTextField(
                                        value = editPct,
                                        onValueChange = { editPct = it },
                                        modifier = Modifier.width(55.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    Text("%", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TextButton(onClick = {
                                        onUpdateTrait(trait.copy(
                                            trait = editName,
                                            percentage = editPct.toFloatOrNull() ?: trait.percentage,
                                            updatedAt = System.currentTimeMillis()
                                        ))
                                        editingTraitId = null
                                    }) { Text("保存", style = MaterialTheme.typography.labelSmall) }
                                    TextButton(onClick = { editingTraitId = null }) { Text("取消", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(trait.trait, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.4f))
                                LinearProgressIndicator(
                                    progress = { trait.percentage / 100f },
                                    modifier = Modifier.weight(0.35f).height(4.dp),
                                )
                                Text("${trait.percentage.toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp).weight(0.15f))
                                IconButton(onClick = { editingTraitId = trait.id; editName = trait.trait; editPct = trait.percentage.toInt().toString() }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteTrait(trait.id) }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Close, "删除", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (trait != traits.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", style = MaterialTheme.typography.labelMedium) }
        }
    )
}

/**
 * 评价3列并列展示
 */
@Composable
private fun EvaluationTripleColumn(
    evaluations: List<EvaluationEntity>,
    onAdopt: (Int, String) -> Unit
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (templateKey in listOf("80/20", "65/35", "90/10")) {
            val templateEvals = evaluations.filter { it.template == templateKey }
            val colors = templateColors[templateKey] ?: Pair(Color.Gray, Color.LightGray)
            val label = templateLabels[templateKey] ?: templateKey

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = colors.first,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "$templateKey $label",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                for (ev in templateEvals) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.second),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(
                                ev.content,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!ev.personalEval.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                HorizontalDivider(color = colors.first.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("💬 ${ev.personalEval}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (!ev.suggestion.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("💡 ${ev.suggestion}", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (ev.isAdopted) {
                                    Text("✓", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                TextButton(
                                    onClick = { onAdopt(ev.id, ev.template) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (ev.isAdopted) "复制" else "采纳",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AddStudentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加学生", style = MaterialTheme.typography.titleSmall) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("学生姓名", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("添加", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", style = MaterialTheme.typography.labelMedium) } }
    )
}
