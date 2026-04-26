package com.example.personaeval

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.personaeval.ui.screens.classlist.*
import com.example.personaeval.ui.screens.studentlist.*
import com.example.personaeval.ui.screens.evaluation.*
import com.example.personaeval.ui.screens.studentdetail.*

@Composable
fun MainNavigation(onNavigateToSettings: () -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "class_list") {
        // 班级列表
        composable("class_list") {
            val viewModel: ClassListViewModel = viewModel()
            val state by viewModel.uiState.collectAsState()

            LaunchedEffect(state.error) {
                if (state.error != null) viewModel.clearError()
            }

            ClassListScreen(
                classes = state.classes,
                isLoading = state.isExtracting,
                editMode = state.editMode,
                onToggleEditMode = viewModel::toggleEditMode,
                onNavigateToStudents = { classId, className ->
                    val encodedName = Uri.encode(className)
                    navController.navigate("student_list/$classId/$encodedName") {
                        launchSingleTop = true
                    }
                },
                onAddClass = { grade, name -> viewModel.addClass(grade, name) },
                onUpdateClass = { cls -> viewModel.updateClass(cls) },
                onDeleteClass = { id -> viewModel.deleteClass(id) },
                onAiExtract = { text -> viewModel.aiExtract(text) },
                onNavigateToSettings = onNavigateToSettings
            )
        }

        // 学生列表（核心页面 — 含课堂弹窗 + 评价3列展示 + 模板选择）
        composable(
            route = "student_list/{classId}/{className}",
            arguments = listOf(
                navArgument("classId") { type = NavType.IntType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getInt("classId") ?: return@composable
            val className = backStackEntry.arguments?.getString("className") ?: ""

            val viewModel: StudentListViewModel = viewModel(key = "student_list_$classId") {
                StudentListViewModel(classId)
            }
            val state by viewModel.uiState.collectAsState()

            StudentListScreen(
                className = className,
                students = state.students,
                traitsMap = state.traitsMap,
                evaluationsMap = state.evaluationsMap,
                isLoading = state.isLoading,
                editMode = state.editMode,
                lessonContent = state.lessonContent,
                classAtmosphere = state.classAtmosphere,
                performanceNotes = state.performanceNotes,
                templates = state.templates,
                selectedTemplateId = state.selectedTemplateId,
                isTemplateLoading = state.isTemplateLoading,
                onToggleEditMode = viewModel::toggleEditMode,
                onBack = { navController.popBackStack() },
                onLessonContentChange = viewModel::updateLessonContent,
                onClassAtmosphereChange = viewModel::updateClassAtmosphere,
                onPerformanceNotesChange = viewModel::updatePerformanceNotes,
                onAddStudent = { name -> viewModel.addStudent(name) },
                onUpdateStudent = { student -> viewModel.updateStudent(student) },
                onDeleteStudent = { id -> viewModel.deleteStudent(id) },
                onGenerateAll = viewModel::generateAll,
                onGenerateSingle = { id -> viewModel.generateSingle(id) },
                onAdoptEvaluation = { evalId, studentId, template -> viewModel.adoptEvaluation(evalId, studentId, template) },
                onNavigateToStudentDetail = { studentId ->
                    navController.navigate("student_detail/$studentId") {
                        launchSingleTop = true
                    }
                },
                onSelectTemplate = viewModel::selectTemplate,
                onShowTemplateManagement = { /* handled internally in StudentListScreen now */ },
                onCreateTemplate = { name, p, s -> viewModel.createTemplate(name, p, s) },
                onUpdateTemplate = { t -> viewModel.updateTemplate(t) },
                onDeleteTemplate = { id -> viewModel.deleteTemplate(id) },
                onAiGenerateTemplate = { desc -> viewModel.aiGenerateTemplate(desc) },
                onAddTrait = { studentId, name, pct -> viewModel.addTrait(studentId, name, pct) },
                onUpdateTrait = { trait -> viewModel.updateTrait(trait) },
                onDeleteTrait = { traitId -> viewModel.deleteTrait(traitId) }
            )
        }

        // 评价页面（保留独立入口 — 从学生详情页可查看）
        composable(
            route = "evaluation/{studentId}/{studentName}",
            arguments = listOf(
                navArgument("studentId") { type = NavType.IntType },
                navArgument("studentName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: return@composable
            val studentName = backStackEntry.arguments?.getString("studentName") ?: ""

            val viewModel: EvaluationViewModel = viewModel(key = "eval_$studentId") {
                EvaluationViewModel(studentId)
            }
            val state by viewModel.uiState.collectAsState()

            EvaluationScreen(
                studentName = studentName,
                evaluations = state.evaluations,
                isLoading = state.isLoading,
                editMode = state.editMode,
                onBack = { navController.popBackStack() },
                onToggleEditMode = viewModel::toggleEditMode,
                onAdopt = { id, sid, template -> viewModel.adoptEvaluation(id, sid, template) },
                onDelete = { id -> viewModel.deleteEvaluation(id) }
            )
        }

        // 学生详情（性格画像 CRUD + 已采纳历史评价 + 个人评价/提升建议）
        composable(
            route = "student_detail/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: return@composable

            val viewModel: StudentDetailViewModel = viewModel(key = "detail_$studentId") {
                StudentDetailViewModel(studentId)
            }
            val state by viewModel.uiState.collectAsState()

            StudentDetailScreen(
                uiState = state,
                editMode = state.editMode,
                onBack = { navController.popBackStack() },
                onToggleEditMode = viewModel::toggleEditMode,
                onAddTrait = { name, pct -> viewModel.addTrait(name, pct) },
                onUpdateTrait = { trait -> viewModel.updateTrait(trait) },
                onDeleteTrait = { id -> viewModel.deleteTrait(id) },
                onUpdatePersonalEval = { sid, pEval -> viewModel.updatePersonalEval(sid, pEval) },
                onUpdateSuggestion = { sid, sugg -> viewModel.updateSuggestion(sid, sugg) }
            )
        }
    }
}
