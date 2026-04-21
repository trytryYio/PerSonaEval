import { useState, useEffect } from 'react'
import { fetchStudents, batchGenerate, createStudent, updateStudent, deleteStudent } from '../api'
import type { SavedStudent, Evaluation } from '../types'
import StudentCard from './StudentCard'

interface ClassDetailPageProps {
  classId: number
  className: string
  apiKey: string
  loading: boolean
  setLoading: (v: boolean) => void
  onBack: () => void
  onNavigateStudent: (studentId: number) => void
  onError: (msg: string) => void
}

export default function ClassDetailPage({
  classId, className, apiKey, loading, setLoading,
  onBack, onNavigateStudent, onError,
}: ClassDetailPageProps) {
  const [classStudents, setClassStudents] = useState<SavedStudent[]>([])
  const [lessonContent, setLessonContent] = useState('')
  const [classAtmosphere, setClassAtmosphere] = useState('')

  // Student CRUD
  const [showAddStudent, setShowAddStudent] = useState(false)
  const [newStudentName, setNewStudentName] = useState('')
  const [editStudentId, setEditStudentId] = useState<number | null>(null)
  const [editStudentName, setEditStudentName] = useState('')

  useEffect(() => {
    loadStudents()
  }, [classId])

  const loadStudents = () => {
    setLoading(true)
    fetchStudents(classId)
      .then(setClassStudents)
      .catch(() => onError('获取学生失败'))
      .finally(() => setLoading(false))
  }

  const handleGenerateAll = async () => {
    if (!lessonContent.trim()) { onError('请先输入课程内容'); return }
    setLoading(true)
    try {
      const items = classStudents.map(s => ({
        student_id: s.id,
        lesson_notes: localStorage.getItem(`perf_notes_${s.id}`) || '',
      }))
      const results = await batchGenerate(classAtmosphere, lessonContent, items, apiKey)
      // Save to localStorage and update state
      for (const [sidStr, evals] of Object.entries(results)) {
        const sid = Number(sidStr)
        localStorage.setItem(`evals_${sid}`, JSON.stringify(evals))
      }
      // Refresh students to trigger re-render (evaluations are in StudentCard localStorage)
      loadStudents()
    } catch (e: any) {
      onError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleStudentGenerate = (studentId: number, evals: Evaluation[]) => {
    localStorage.setItem(`evals_${studentId}`, JSON.stringify(evals))
  }

  const handleAddStudent = async () => {
    if (!newStudentName.trim()) { onError('请填写学生姓名'); return }
    try {
      await createStudent(classId, newStudentName)
      setNewStudentName('')
      setShowAddStudent(false)
      loadStudents()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleEditStudent = async (id: number) => {
    if (!editStudentName.trim()) return
    try {
      await updateStudent(id, { name: editStudentName })
      setEditStudentId(null)
      loadStudents()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleDeleteStudent = async (id: number) => {
    if (!confirm('确定要删除这个学生吗？')) return
    try {
      await deleteStudent(id)
      loadStudents()
    } catch (e: any) {
      onError(e.message)
    }
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="bg-white rounded-xl p-4 shadow-sm">
        <button onClick={onBack} className="text-blue-500 text-sm mb-3 block">← 返回班级列表</button>
        <div className="flex justify-between items-center mb-3">
          <h2 className="text-sm font-medium text-gray-700">{className}</h2>
          <button onClick={() => setShowAddStudent(!showAddStudent)} className="text-xs bg-blue-500 text-white px-3 py-1.5 rounded-lg hover:bg-blue-600">
            {showAddStudent ? '取消' : '+ 添加学生'}
          </button>
        </div>

        {/* Add student form */}
        {showAddStudent && (
          <div className="mb-3 p-3 bg-blue-50 rounded-lg flex gap-2 items-center">
            <input type="text" value={newStudentName} onChange={(e) => setNewStudentName(e.target.value)} placeholder="学生姓名" className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            <button onClick={handleAddStudent} className="bg-blue-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-600">确认</button>
          </div>
        )}

        {/* Course content + atmosphere + generate all */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-3">
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">课程内容</label>
            <input type="text" value={lessonContent} onChange={(e) => setLessonContent(e.target.value)} placeholder="例：while 循环" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">当堂整体课堂氛围</label>
            <input type="text" value={classAtmosphere} onChange={(e) => setClassAtmosphere(e.target.value)} placeholder="例：今天整体活跃但不愿动手" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex items-end">
            <button onClick={handleGenerateAll} disabled={loading || !lessonContent} className="w-full bg-blue-500 text-white py-2 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50">
              {loading ? '生成中...' : '🔵 生成全部'}
            </button>
          </div>
        </div>
      </div>

      {/* Students */}
      {classStudents.length === 0 ? (
        <div className="bg-white rounded-xl p-4 shadow-sm text-center text-gray-400 text-sm">暂无学生</div>
      ) : classStudents.map((student) => (
        <div key={student.id}>
          {/* Student row header with edit/delete */}
          <div className="flex items-center justify-between px-4 py-1 bg-gray-100 rounded-t-xl -mb-1">
            {editStudentId === student.id ? (
              <div className="flex gap-2 items-center">
                <input type="text" value={editStudentName} onChange={(e) => setEditStudentName(e.target.value)} className="px-2 py-1 border border-gray-300 rounded text-sm w-24" />
                <button onClick={() => handleEditStudent(student.id)} className="text-green-500 text-xs">保存</button>
                <button onClick={() => setEditStudentId(null)} className="text-gray-400 text-xs">取消</button>
              </div>
            ) : (
              <div className="flex gap-2 items-center">
                <button onClick={() => { setEditStudentId(student.id); setEditStudentName(student.name) }} className="text-gray-400 text-xs px-2 py-1 hover:text-blue-500">✏️</button>
                <button onClick={() => handleDeleteStudent(student.id)} className="text-gray-400 text-xs px-2 py-1 hover:text-red-500">🗑️</button>
              </div>
            )}
          </div>
          <StudentCard
            student={student}
            lessonContent={lessonContent}
            classAtmosphere={classAtmosphere}
            onGenerate={handleStudentGenerate}
            onNavigateDetail={onNavigateStudent}
            apiKey={apiKey}
            generating={loading}
            setGenerating={setLoading}
            onError={onError}
          />
        </div>
      ))}
    </div>
  )
}
