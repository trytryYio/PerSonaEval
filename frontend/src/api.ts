import type { ExtractedData, Evaluation, ClassInfo, SavedStudent, StudentDetail, BatchGenerateResponse, BatchGenerateResult, StudentWithClass } from './types'

const API = (path: string) => `/api${path}`

export async function extractText(text: string, apiKey?: string): Promise<ExtractedData> {
  const res = await fetch(API('/extract'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, api_key: apiKey || undefined }),
  })
  if (!res.ok) throw new Error('提取失败')
  return res.json()
}

export async function saveExtracted(
  data: ExtractedData & { total_count: number },
  apiKey?: string,
): Promise<{ class_id: number; grade: string; class_name: string; students: { id: number; name: string }[] }> {
  const res = await fetch(API('/save'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      grade: data.grade,
      class_name: data.class,
      students: data.students,
      total_count: data.total_count,
      api_key: apiKey || undefined,
    }),
  })
  if (!res.ok) throw new Error('保存失败')
  return res.json()
}

export async function generateEvaluation(
  studentId: number,
  lessonContent: string,
  lessonNotes: string,
  apiKey?: string,
): Promise<Evaluation[]> {
  const res = await fetch(API('/generate'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      student_id: studentId,
      lesson_content: lessonContent,
      lesson_notes: lessonNotes || undefined,
      api_key: apiKey || undefined,
    }),
  })
  if (!res.ok) {
    const err = await res.json()
    throw new Error(err.detail || '生成失败')
  }
  return res.json()
}

export async function batchGenerate(
  classId: number,
  studentIds: number[] | null,
  lessonContent: string,
  lessonNotes: string,
  apiKey?: string,
): Promise<BatchGenerateResult[]> {
  const res = await fetch(API('/batch-generate'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      class_id: classId,
      student_ids: studentIds,
      lesson_content: lessonContent,
      lesson_notes: lessonNotes || undefined,
      api_key: apiKey || undefined,
    }),
  })
  if (!res.ok) {
    const err = await res.json()
    throw new Error(err.detail || '批量生成失败')
  }
  const data = await res.json()
  return data.results
}

export async function fetchClasses(): Promise<ClassInfo[]> {
  const res = await fetch(API('/classes'))
  return res.json()
}

export async function fetchStudents(classId: number): Promise<SavedStudent[]> {
  const res = await fetch(API(`/students/${classId}`))
  return res.json()
}

export async function fetchStudentDetail(studentId: number): Promise<StudentDetail> {
  const [studentRes, traitsRes, tempTraitsRes, evalsRes, classesRes] = await Promise.all([
    fetch(API(`/students/${Math.floor(studentId / 100)}/${studentId % 100}`)), // 需要修改后端 API
    fetch(API(`/student/${studentId}/traits`)),
    fetch(API(`/student/${studentId}/temp-traits`)),
    fetch(API(`/evaluations/${studentId}`)),
    fetch(API('/classes')),
  ])

  if (!studentRes.ok) throw new Error('获取学生详情失败')
  const student = await studentRes.json()

  // 找到学生所属班级
  const classes = await classesRes.json()
  const studentClass = classes.find((c: ClassInfo) => c.id === student.class_id)

  return {
    ...student,
    class_grade: studentClass?.grade || '',
    class_name: studentClass?.name || '',
    long_term_traits: await traitsRes.json(),
    temp_traits: await tempTraitsRes.json(),
    evaluations: await evalsRes.json(),
  }
}

// Class CRUD
export async function createClass(grade: string, name: string, studentCount?: number): Promise<{ id: number; grade: string; name: string; student_count: number }> {
  const res = await fetch(API('/class'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ grade, name, student_count: studentCount || 0 }),
  })
  if (!res.ok) throw new Error('创建班级失败')
  return res.json()
}

export async function updateClass(classId: number, updates: { grade: string; name: string; student_count?: number }): Promise<{ id: number; grade: string; name: string; student_count: number }> {
  const res = await fetch(API(`/api/class/${classId}`), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  })
  if (!res.ok) throw new Error('修改班级失败')
  return res.json()
}

export async function deleteClass(classId: number): Promise<void> {
  const res = await fetch(API(`/class/${classId}`), { method: 'DELETE' })
  if (!res.ok) throw new Error('删除班级失败')
}

// Student CRUD
export async function createStudent(classId: number, name: string, traits?: string): Promise<{ id: number; name: string; class_id: number }> {
  const res = await fetch(API('/student'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ class_id: classId, name, traits: traits || '' }),
  })
  if (!res.ok) throw new Error('创建学生失败')
  return res.json()
}

export async function updateStudent(studentId: number, updates: { class_id: number; name: string; traits?: string }): Promise<{ id: number; name: string; class_id: number }> {
  const res = await fetch(API(`/student/${studentId}`), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates),
  })
  if (!res.ok) throw new Error('修改学生失败')
  return res.json()
}

export async function deleteStudent(studentId: number): Promise<void> {
  const res = await fetch(API(`/student/${studentId}`), { method: 'DELETE' })
  if (!res.ok) throw new Error('删除学生失败')
}

// Trait CRUD
export async function createTrait(studentId: number, trait: string, percentage: number): Promise<{ trait: string; percentage: number }[]> {
  const res = await fetch(API(`/trait/${studentId}`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ trait, percentage }),
  })
  if (!res.ok) throw new Error('添加特点失败')
  return res.json()
}

export async function updateTrait(studentId: number, oldTraitName: string, newTrait: string, newPercentage: number): Promise<{ trait: string; percentage: number }[]> {
  const res = await fetch(API(`/trait/${studentId}/${encodeURIComponent(oldTraitName)}`), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ trait: newTrait, percentage: newPercentage }),
  })
  if (!res.ok) throw new Error('修改特点失败')
  return res.json()
}

export async function deleteTrait(studentId: number, traitName: string): Promise<void> {
  const res = await fetch(API(`/trait/${studentId}/${encodeURIComponent(traitName)}`), { method: 'DELETE' })
  if (!res.ok) throw new Error('删除特点失败')
}

// Search students
export async function searchStudents(query: string): Promise<StudentWithClass[]> {
  const res = await fetch(API(`/students/search?q=${encodeURIComponent(query)}`))
  if (!res.ok) throw new Error('搜索失败')
  return res.json()
}

// Delete evaluation
export async function deleteEvaluation(evalId: number): Promise<void> {
  const res = await fetch(API(`/evaluation/${evalId}`), { method: 'DELETE' })
  if (!res.ok) throw new Error('删除评价失败')
}
