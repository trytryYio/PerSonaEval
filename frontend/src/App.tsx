import { useState, useEffect } from 'react'

interface SavedStudent {
  id: number
  name: string
  traits_raw: string
  traits: { trait: string; percentage: number }[]
  class_id?: number
  class_name?: string
}

interface Evaluation {
  id?: number
  template: string
  label: string
  content: string
  is_adopted?: boolean
}

interface StudentWithClass extends SavedStudent {
  class_id: number
  class_name: string
}

interface StudentBatchResult {
  student_id: number
  student_name: string
  evaluations: Evaluation[]
  error?: string
}

type Page = 'input' | 'preview' | 'classes' | 'student-detail'

const templateBadge: Record<string, string> = {
  '80/20': 'bg-green-500', '65/35': 'bg-blue-500', '90/10': 'bg-yellow-500',
}
const templateColors: Record<string, string> = {
  '80/20': 'border-green-300 bg-green-50/30',
  '65/35': 'border-blue-300 bg-blue-50/30',
  '90/10': 'border-yellow-300 bg-yellow-50/30',
}
const templateHeaderColors: Record<string, string> = {
  '80/20': 'bg-green-100 text-green-800',
  '65/35': 'bg-blue-100 text-blue-800',
  '90/10': 'bg-yellow-100 text-yellow-800',
}

function App() {
  const [page, setPage] = useState<Page>('input')
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('dashscope_api_key') || '')
  const [rawText, setRawText] = useState('')
  const [extracted, setExtracted] = useState<{ grade: string; class: string; students: { name: string; traits_raw: string; traits_pct: Record<string, number> }[]; total_count: number } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [classes, setClasses] = useState<{ id: number; grade: string; name: string; student_count: number }[]>([])
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null)
  const [classStudents, setClassStudents] = useState<SavedStudent[]>([])
  const [selectedStudent, setSelectedStudent] = useState<SavedStudent | null>(null)
  const [lessonContent, setLessonContent] = useState('')
  const [lessonNotesGlobal, setLessonNotesGlobal] = useState('')
  const [studentNotes, setStudentNotes] = useState<Record<number, string>>({})
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<StudentWithClass[]>([])
  const [selectedForBatch, setSelectedForBatch] = useState<Set<number>>(new Set())
  const [batchResults, setBatchResults] = useState<StudentBatchResult[]>([])
  const [showBatchPanel, setShowBatchPanel] = useState(false)

  // CRUD modals
  const [showClassModal, setShowClassModal] = useState(false)
  const [editingClass, setEditingClass] = useState<{ id: number; grade: string; name: string } | null>(null)
  const [classFormGrade, setClassFormGrade] = useState('')
  const [classFormName, setClassFormName] = useState('')

  const [showStudentModal, setShowStudentModal] = useState(false)
  const [editingStudent, setEditingStudent] = useState<{ id: number; name: string } | null>(null)
  const [studentFormName, setStudentFormName] = useState('')

  const [showTraitModal, setShowTraitModal] = useState(false)
  const [traitFormTrait, setTraitFormTrait] = useState('')
  const [traitFormPct, setTraitFormPct] = useState('')
  const [traitFormOldName, setTraitFormOldName] = useState('')

  const API = (path: string) => `/api${path}`

  useEffect(() => { if (page === 'classes') fetchClasses() }, [page])
  useEffect(() => {
    if (searchQuery.trim()) handleSearch(searchQuery)
    else setSearchResults([])
  }, [searchQuery])

  const fetchClasses = async () => {
    try { const res = await fetch(API('/classes')); setClasses(await res.json()) }
    catch { setError('获取班级失败') }
  }

  const fetchClassStudents = async (classId: number) => {
    setLoading(true)
    try {
      const res = await fetch(API(`/students/${classId}`))
      const cls = classes.find(c => c.id === classId)
      const students: SavedStudent[] = await res.json()
      // Attach class info to each student
      students.forEach(s => {
        s.class_id = classId
        s.class_name = cls ? `${cls.grade}${cls.name}` : ''
      })
      setClassStudents(students)
      setSelectedClassId(classId); setSelectedStudent(null)
      setEvaluations([]); setShowBatchPanel(false)
      setStudentNotes({}); setBatchResults([])
    } catch { setError('获取学生失败') }
    finally { setLoading(false) }
  }

  const handleSearch = async (query: string) => {
    if (!query.trim()) { setSearchResults([]); return }
    try { const res = await fetch(API(`/students/search?q=${encodeURIComponent(query)}`)); setSearchResults(await res.json()) }
    catch { setError('搜索失败') }
  }

  const handleExtract = async () => {
    if (!rawText.trim()) { setError('请输入学生信息'); return }
    setLoading(true); setError('')
    try {
      const res = await fetch(API('/extract'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: rawText, api_key: apiKey || undefined }),
      })
      if (!res.ok) throw new Error('提取失败')
      const data = await res.json()
      setExtracted(data)
      if (data.students.length === 0) setError('未识别到学生信息')
      else setPage('preview')
    } catch (e: any) { setError(e.message) }
    finally { setLoading(false) }
  }

  const handleSave = async () => {
    if (!extracted) return
    setLoading(true); setError('')
    try {
      const res = await fetch(API('/save'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          grade: extracted.grade, class_name: extracted.class,
          students: extracted.students, total_count: extracted.total_count,
          api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) throw new Error('保存失败')
      setExtracted(null); setRawText(''); setPage('classes')
      fetchClasses()
    } catch (e: any) { setError(e.message) }
    finally { setLoading(false) }
  }

  const handleGenerate = async (studentId: number) => {
    if (!lessonContent.trim()) { setError('请输入课程内容'); return }
    setLoading(true); setError(''); setEvaluations([])
    const studentNote = studentNotes[studentId] || ''
    try {
      const res = await fetch(API('/generate'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          student_id: studentId, lesson_content: lessonContent,
          lesson_notes: studentNote || undefined, api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) { const err = await res.json(); throw new Error(err.detail || '生成失败') }
      const data = await res.json()
      setEvaluations(data)
    } catch (e: any) { setError(e.message) }
    finally { setLoading(false) }
  }

  const handleBatchGenerate = async () => {
    if (!lessonContent.trim()) { setError('请输入课程内容'); return }
    setLoading(true); setError(''); setBatchResults([])
    try {
      const studentIds = selectedForBatch.size > 0 ? Array.from(selectedForBatch) : null
      const res = await fetch(API('/batch-generate'), {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          class_id: selectedClassId, student_ids: studentIds,
          lesson_content: lessonContent, lesson_notes_global: lessonNotesGlobal || undefined,
          student_notes: studentNotes, api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) { const err = await res.json(); throw new Error(err.detail || '批量生成失败') }
      const data = await res.json()
      setBatchResults(data.results)
    } catch (e: any) { setError(e.message) }
    finally { setLoading(false) }
  }

  const handleAdopt = async (evalId: number) => {
    try {
      await fetch(API(`/evaluation/${evalId}/adopt`), { method: 'POST' })
      setCopiedId(evalId)
      setTimeout(() => setCopiedId(null), 2000)
      // 刷新评价列表
      if (selectedStudent) {
        const res = await fetch(API(`/evaluations/${selectedStudent.id}`))
        const data = await res.json()
        // 更新当前显示的评价的采纳状态
        setEvaluations(prev => prev.map(ev => {
          const updated = data.find((d: any) => d.id === ev.id)
          return updated ? { ...ev, is_adopted: updated.is_adopted } : ev
        }))
      }
    } catch { setError('采纳失败') }
  }

  const toggleSelectForBatch = (studentId: number) => {
    const newSet = new Set(selectedForBatch)
    if (newSet.has(studentId)) newSet.delete(studentId)
    else newSet.add(studentId)
    setSelectedForBatch(newSet)
  }

  const selectAllForBatch = () => {
    if (selectedForBatch.size === classStudents.length) setSelectedForBatch(new Set())
    else setSelectedForBatch(new Set(classStudents.map(s => s.id)))
  }

  const viewStudentDetail = (student: SavedStudent) => {
    setSelectedStudent(student); setPage('student-detail')
  }

  const updateStudentNote = (studentId: number, note: string) => {
    setStudentNotes(prev => ({ ...prev, [studentId]: note }))
  }

  // === CRUD handlers ===
  const openCreateClass = () => { setEditingClass(null); setClassFormGrade(''); setClassFormName(''); setShowClassModal(true) }
  const openEditClass = (c: { id: number; grade: string; name: string }) => { setEditingClass(c); setClassFormGrade(c.grade); setClassFormName(c.name); setShowClassModal(true) }
  const handleSaveClass = async () => {
    if (!classFormGrade || !classFormName) { setError('请填写完整'); return }
    setLoading(true)
    try {
      if (editingClass) {
        await fetch(API(`/class/${editingClass.id}`), { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ grade: classFormGrade, name: classFormName }) })
      } else {
        await fetch(API('/class'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ grade: classFormGrade, name: classFormName }) })
      }
      setShowClassModal(false); fetchClasses()
    } catch { setError('保存班级失败') }
    finally { setLoading(false) }
  }
  const handleDeleteClass = async (classId: number) => {
    if (!confirm('确定删除此班级吗？')) return
    try { await fetch(API(`/class/${classId}`), { method: 'DELETE' }); fetchClasses() }
    catch { setError('删除班级失败') }
  }

  const openCreateStudent = () => { setEditingStudent(null); setStudentFormName(''); setShowStudentModal(true) }
  const openEditStudent = (s: { id: number; name: string }) => { setEditingStudent(s); setStudentFormName(s.name); setShowStudentModal(true) }
  const handleSaveStudent = async () => {
    if (!studentFormName) { setError('请填写姓名'); return }
    setLoading(true)
    try {
      if (editingStudent) {
        await fetch(API(`/student/${editingStudent.id}`), { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ class_id: selectedClassId, name: studentFormName }) })
      } else {
        await fetch(API('/student'), { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ class_id: selectedClassId, name: studentFormName }) })
      }
      setShowStudentModal(false); fetchClassStudents(selectedClassId!)
    } catch { setError('保存学生失败') }
    finally { setLoading(false) }
  }
  const handleDeleteStudent = async (studentId: number) => {
    if (!confirm('确定删除此学生吗？')) return
    try { await fetch(API(`/student/${studentId}`), { method: 'DELETE' }); fetchClassStudents(selectedClassId!) }
    catch { setError('删除学生失败') }
  }

  const openAddTrait = (studentId: number) => {
    setTraitFormTrait(''); setTraitFormPct(''); setTraitFormOldName('')
    setSelectedStudent(classStudents.find(s => s.id === studentId) || null)
    setShowTraitModal(true)
  }
  const openEditTrait = (studentId: number, oldName: string, oldPct: number) => {
    setTraitFormTrait(oldName); setTraitFormPct(String(oldPct)); setTraitFormOldName(oldName)
    setSelectedStudent(classStudents.find(s => s.id === studentId) || null)
    setShowTraitModal(true)
  }
  const handleSaveTrait = async () => {
    if (!selectedStudent || !traitFormTrait || !traitFormPct) { setError('请填写完整'); return }
    setLoading(true)
    try {
      const method = traitFormOldName ? 'PUT' : 'POST'
      const url = traitFormOldName ? API(`/trait/${selectedStudent.id}/${encodeURIComponent(traitFormOldName)}`) : API(`/trait/${selectedStudent.id}`)
      await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ trait: traitFormTrait, percentage: parseInt(traitFormPct) }) })
      setShowTraitModal(false); fetchClassStudents(selectedClassId!)
    } catch { setError('保存特征失败') }
    finally { setLoading(false) }
  }
  const handleDeleteTrait = async (studentId: number, traitName: string) => {
    if (!confirm(`确定删除特征「${traitName}」吗？`)) return
    try { await fetch(API(`/trait/${studentId}/${encodeURIComponent(traitName)}`), { method: 'DELETE' }); fetchClassStudents(selectedClassId!) }
    catch { setError('删除特征失败') }
  }

  // Copy text to clipboard
  const copyText = async (text: string, evalId: number) => {
    try {
      await navigator.clipboard.writeText(text)
      await handleAdopt(evalId)
    } catch { setError('复制失败') }
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      <header className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 py-3 flex justify-between items-center">
          <h1 className="text-lg font-bold text-gray-900">课后评价生成器</h1>
          <div className="flex gap-1 items-center">
            <button onClick={() => setPage('input')} className={`text-sm px-3 py-1.5 rounded ${page === 'input' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>输入</button>
            <button onClick={() => { setPage('classes'); setSelectedClassId(null); setClassStudents([]); setSelectedStudent(null) }} className={`text-sm px-3 py-1.5 rounded ${page === 'classes' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>班级</button>
            <button onClick={() => setShowSettings(!showSettings)} className="text-gray-500 ml-1">⚙️</button>
          </div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-4 space-y-4">
        {showSettings && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <label className="block text-sm font-medium text-gray-700 mb-1">千问 API Key</label>
            <input type="password" value={apiKey} onChange={(e) => { setApiKey(e.target.value); localStorage.setItem('dashscope_api_key', e.target.value) }} placeholder="sk-..." className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            <p className="text-xs text-gray-400 mt-1">保存在浏览器本地</p>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-3">
            <p className="text-red-600 text-sm">{error}</p>
            <button onClick={() => setError('')} className="text-red-400 text-xs mt-1">关闭</button>
          </div>
        )}

        {/* Page: Input */}
        {page === 'input' && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <h2 className="text-sm font-medium text-gray-700 mb-2">输入学生信息</h2>
            <textarea value={rawText} onChange={(e) => setRawText(e.target.value)} placeholder={'例：\n八年级 a 班 5 个学生\n欧阳小明活泼好动喜欢上课跳舞\n张三丰比较安静但是做题很慢'} rows={6} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 resize-none" />
            <button onClick={handleExtract} disabled={loading} className="mt-3 w-full bg-blue-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50">
              {loading ? 'AI 分析中...' : '识别学生信息'}
            </button>
          </div>
        )}

        {/* Page: Preview */}
        {page === 'preview' && extracted && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <h2 className="text-sm font-medium text-gray-700 mb-3">{extracted.grade}{extracted.class} - {extracted.students.length} 名学生</h2>
            <div className="space-y-2 mb-4">
              {extracted.students.map((s, i) => (
                <div key={i} className="p-3 bg-gray-50 rounded-lg">
                  <div className="font-medium text-gray-900">{s.name}</div>
                  <div className="text-xs text-gray-500 mt-1">{s.traits_raw}</div>
                  {s.traits_pct && Object.keys(s.traits_pct).length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {Object.entries(s.traits_pct).map(([k, v]) => (
                        <span key={k} className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">{k} {v}%</span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <button onClick={() => setPage('input')} className="flex-1 bg-gray-200 text-gray-700 py-2.5 rounded-lg text-sm">取消</button>
              <button onClick={handleSave} disabled={loading} className="flex-1 bg-green-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50">
                {loading ? '保存中...' : '确认保存到数据库'}
              </button>
            </div>
          </div>
        )}

        {/* Page: Classes */}
        {page === 'classes' && selectedStudent === null && (
          <div className="space-y-4">
            {/* Search */}
            <div className="bg-white rounded-xl p-4 shadow-sm">
              <label className="block text-sm font-medium text-gray-700 mb-2">搜索学生</label>
              <input type="text" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="输入学生姓名..." className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
              {searchResults.length > 0 && (
                <div className="mt-3 space-y-2">
                  {searchResults.map((s) => (
                    <button key={s.id} onClick={() => { viewStudentDetail(s); setSearchQuery(''); setSearchResults([]); }} className="w-full flex justify-between items-center p-3 bg-gray-50 rounded-lg hover:bg-gray-100">
                      <span className="font-medium text-gray-900">{s.name}</span>
                      <span className="text-gray-400 text-sm">{s.class_name}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Class list */}
            {!selectedClassId && (
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <div className="flex justify-between items-center mb-3">
                  <h2 className="text-sm font-medium text-gray-700">班级列表</h2>
                  <button onClick={openCreateClass} className="text-xs bg-green-500 text-white px-3 py-1.5 rounded hover:bg-green-600">+ 新建班级</button>
                </div>
                {classes.length === 0 ? (
                  <p className="text-gray-400 text-sm text-center py-4">暂无班级</p>
                ) : classes.map((c) => (
                  <div key={c.id} className="flex justify-between items-center p-3 bg-gray-50 rounded-lg mb-2">
                    <button onClick={() => fetchClassStudents(c.id)} className="flex-1 flex justify-between items-center">
                      <span className="font-medium text-gray-900">{c.grade}{c.name}</span>
                      <span className="text-gray-400 text-sm">{c.student_count} 人</span>
                    </button>
                    <div className="flex gap-1 ml-3">
                      <button onClick={() => openEditClass(c)} className="text-xs text-blue-500 px-2 py-1 hover:bg-blue-50 rounded">编辑</button>
                      <button onClick={() => handleDeleteClass(c.id)} className="text-xs text-red-500 px-2 py-1 hover:bg-red-50 rounded">删除</button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Student list */}
            {selectedClassId && (
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <button onClick={() => { setSelectedClassId(null); setClassStudents([]) }} className="text-blue-500 text-sm mb-3 block">← 返回班级列表</button>
                <div className="flex justify-between items-center mb-3">
                  <h2 className="text-sm font-medium text-gray-700">学生列表</h2>
                  <div className="flex gap-2">
                    <button onClick={openCreateStudent} className="text-xs bg-green-500 text-white px-3 py-1.5 rounded hover:bg-green-600">+ 添加学生</button>
                    <button onClick={() => setShowBatchPanel(!showBatchPanel)} className="text-xs bg-blue-500 text-white px-3 py-1.5 rounded hover:bg-blue-600">
                      {showBatchPanel ? '关闭批量' : '批量生成'}
                    </button>
                  </div>
                </div>

                {/* Course Content */}
                <div className="mb-4 p-3 bg-amber-50 rounded-lg border border-amber-200">
                  <label className="block text-xs font-medium text-amber-700 mb-1">本节课课程内容</label>
                  <input type="text" value={lessonContent} onChange={(e) => setLessonContent(e.target.value)} placeholder="例：while 循环" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" />
                </div>

                {/* Batch Panel */}
                {showBatchPanel && (
                  <div className="mb-4 p-4 bg-blue-50 rounded-lg border border-blue-200">
                    <div className="flex justify-between items-center mb-2">
                      <span className="text-xs font-medium text-blue-700">已选 {selectedForBatch.size}/{classStudents.length}</span>
                      <button onClick={selectAllForBatch} className="text-xs text-blue-600 hover:underline">
                        {selectedForBatch.size === classStudents.length ? '全不选' : '全选'}
                      </button>
                    </div>
                    <div className="mb-3">
                      <label className="block text-xs font-medium text-blue-700 mb-1">全班整体表现（选填）</label>
                      <input type="text" value={lessonNotesGlobal} onChange={(e) => setLessonNotesGlobal(e.target.value)} placeholder="例：今天大家都很开心" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" />
                    </div>
                    <button onClick={handleBatchGenerate} disabled={loading || !lessonContent} className="w-full bg-blue-500 text-white py-2 rounded text-sm font-medium hover:bg-blue-600 disabled:opacity-50">
                      {loading ? '生成中...' : `为 ${selectedForBatch.size > 0 ? selectedForBatch.size : classStudents.length} 名学生生成`}
                    </button>
                    {batchResults.length > 0 && (
                      <div className="mt-3 space-y-2">
                        {batchResults.map(r => (
                          <div key={r.student_id} className={`text-xs p-2 rounded ${r.error ? 'bg-red-50 text-red-600' : 'bg-green-50 text-green-700'}`}>
                            {r.error ? `✗ ${r.student_name}: ${r.error}` : `✓ ${r.student_name} 已生成 ${r.evaluations?.length || 0} 份（见下方学生卡片）`}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Students */}
                <div className="space-y-3">
                  {classStudents.map((s) => {
                    const hasEvals = evaluations.length > 0
                    const studentBatch = batchResults.find(r => r.student_id === s.id)
                    const showEvals = hasEvals || !!studentBatch
                    const evals = hasEvals ? evaluations : (studentBatch?.evaluations || [])

                    return (
                      <div key={s.id} className="rounded-lg border border-gray-200 bg-white p-4">
                        {/* Name + checkbox + CRUD */}
                        <div className="flex items-start gap-2 mb-2">
                          <input type="checkbox" checked={selectedForBatch.has(s.id)} onChange={() => toggleSelectForBatch(s.id)} className="mt-1 w-4 h-4 cursor-pointer" />
                          <div className="flex-1">
                            <div className="flex items-center gap-2">
                              <span className="font-medium text-gray-900">{s.name}</span>
                              <button onClick={() => viewStudentDetail(s)} className="text-xs text-blue-500 hover:underline">详情</button>
                            </div>
                            <div className="flex flex-wrap gap-1 mt-1">
                              {s.traits.map((t) => (
                                <span key={t.trait} className="inline-flex items-center gap-0.5 text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">
                                  {t.trait} {t.percentage}%
                                  <button onClick={() => openEditTrait(s.id, t.trait, t.percentage)} className="text-blue-500 hover:text-blue-700 ml-0.5">✎</button>
                                  <button onClick={() => handleDeleteTrait(s.id, t.trait)} className="text-red-500 hover:text-red-700">×</button>
                                </span>
                              ))}
                              <button onClick={() => openAddTrait(s.id)} className="text-xs text-green-600 hover:text-green-800">+ 特征</button>
                            </div>
                          </div>
                          <div className="flex gap-1">
                            <button onClick={() => openEditStudent({ id: s.id, name: s.name })} className="text-xs text-blue-500 px-2 py-1 rounded hover:bg-blue-50">编辑</button>
                            <button onClick={() => handleDeleteStudent(s.id)} className="text-xs text-red-500 px-2 py-1 rounded hover:bg-red-50">删除</button>
                            <button onClick={() => handleGenerate(s.id)} disabled={loading || !lessonContent} className="text-xs bg-blue-500 text-white px-3 py-1.5 rounded hover:bg-blue-600 disabled:opacity-50">生成</button>
                          </div>
                        </div>

                        {/* 当堂表现 - inline text input, separate line */}
                        <div className="mt-2 pl-6">
                          <label className="block text-xs font-medium text-gray-500 mb-1">当堂表现</label>
                          <input
                            type="text"
                            value={studentNotes[s.id] || ''}
                            onChange={(e) => updateStudentNote(s.id, e.target.value)}
                            placeholder="例：今天上课积极，但不太愿意写代码"
                            className="w-full px-3 py-1.5 border border-gray-200 rounded text-sm bg-gray-50 focus:bg-white focus:ring-1 focus:ring-blue-300"
                          />
                        </div>

                        {/* Evaluations - all 3 shown at once */}
                        {showEvals && evals.length > 0 && (
                          <div className="mt-3 space-y-3 pl-6">
                            <div className="text-xs font-medium text-gray-600">生成的评价（点击「复制并采纳」即可使用）</div>
                            {evals.map((ev, idx) => (
                              <div key={ev.id || idx} className={`rounded-lg border-2 p-3 ${templateColors[ev.template] || 'border-gray-200'}`}>
                                <div className="flex justify-between items-center mb-2">
                                  <div className="flex items-center gap-2">
                                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${templateBadge[ev.template]} text-white`}>
                                      {ev.template} · {ev.label}
                                    </span>
                                    {ev.is_adopted && (
                                      <span className="text-xs bg-green-600 text-white px-2 py-0.5 rounded-full">已采纳</span>
                                    )}
                                  </div>
                                  <button
                                    onClick={() => copyText(ev.content, ev.id || idx)}
                                    className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-all ${
                                      copiedId === ev.id
                                        ? 'bg-green-500 text-white'
                                        : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
                                    }`}
                                  >
                                    {copiedId === ev.id ? '✓ 已复制并采纳' : '📋 复制并采纳'}
                                  </button>
                                </div>
                                <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed bg-white/70 rounded-lg p-3">
                                  {ev.content}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Student Detail Page */}
        {page === 'student-detail' && selectedStudent && (
          <StudentDetailPage
            student={selectedStudent}
            onBack={() => { setPage('classes'); setSelectedStudent(null) }}
          />
        )}

        {/* Modals */}
        {showClassModal && (
          <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl p-6 shadow-lg w-full max-w-sm">
              <h3 className="text-sm font-medium text-gray-700 mb-4">{editingClass ? '编辑班级' : '新建班级'}</h3>
              <div className="space-y-3">
                <div><label className="block text-xs font-medium text-gray-600 mb-1">年级</label><input type="text" value={classFormGrade} onChange={(e) => setClassFormGrade(e.target.value)} placeholder="例：八年级" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" /></div>
                <div><label className="block text-xs font-medium text-gray-600 mb-1">班级名</label><input type="text" value={classFormName} onChange={(e) => setClassFormName(e.target.value)} placeholder="例：a 班" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" /></div>
              </div>
              <div className="flex gap-2 mt-4">
                <button onClick={() => setShowClassModal(false)} className="flex-1 bg-gray-200 text-gray-700 py-2 rounded text-sm">取消</button>
                <button onClick={handleSaveClass} className="flex-1 bg-blue-500 text-white py-2 rounded text-sm hover:bg-blue-600">保存</button>
              </div>
            </div>
          </div>
        )}

        {showStudentModal && (
          <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl p-6 shadow-lg w-full max-w-sm">
              <h3 className="text-sm font-medium text-gray-700 mb-4">{editingStudent ? '编辑学生' : '添加学生'}</h3>
              <div><label className="block text-xs font-medium text-gray-600 mb-1">姓名</label><input type="text" value={studentFormName} onChange={(e) => setStudentFormName(e.target.value)} placeholder="学生姓名" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" /></div>
              <div className="flex gap-2 mt-4">
                <button onClick={() => setShowStudentModal(false)} className="flex-1 bg-gray-200 text-gray-700 py-2 rounded text-sm">取消</button>
                <button onClick={handleSaveStudent} className="flex-1 bg-blue-500 text-white py-2 rounded text-sm hover:bg-blue-600">保存</button>
              </div>
            </div>
          </div>
        )}

        {showTraitModal && selectedStudent && (
          <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl p-6 shadow-lg w-full max-w-sm">
              <h3 className="text-sm font-medium text-gray-700 mb-4">{traitFormOldName ? '编辑特征' : '添加特征'} - {selectedStudent.name}</h3>
              <div className="space-y-3">
                <div><label className="block text-xs font-medium text-gray-600 mb-1">特征名称</label><input type="text" value={traitFormTrait} onChange={(e) => setTraitFormTrait(e.target.value)} placeholder="例：活泼好动" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" /></div>
                <div><label className="block text-xs font-medium text-gray-600 mb-1">占比 (%)</label><input type="number" value={traitFormPct} onChange={(e) => setTraitFormPct(e.target.value)} placeholder="30" min="0" max="100" className="w-full px-3 py-2 border border-gray-300 rounded text-sm" /></div>
              </div>
              <div className="flex gap-2 mt-4">
                <button onClick={() => setShowTraitModal(false)} className="flex-1 bg-gray-200 text-gray-700 py-2 rounded text-sm">取消</button>
                <button onClick={handleSaveTrait} className="flex-1 bg-blue-500 text-white py-2 rounded text-sm hover:bg-blue-600">保存</button>
              </div>
            </div>
          </div>
        )}

        {loading && (
          <div className="fixed inset-0 bg-black/20 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl p-6 shadow-lg">
              <div className="animate-spin text-2xl mb-2">⏳</div>
              <p className="text-sm text-gray-600">处理中...</p>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}

// Student Detail Component
function StudentDetailPage({
  student,
  onBack,
}: {
  student: SavedStudent
  onBack: () => void
}) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [studentDetail, setStudentDetail] = useState<any>(null)
  const [copiedId, setCopiedId] = useState<number | null>(null)

  useEffect(() => { fetchStudentDetail() }, [student.id])

  const fetchStudentDetail = async () => {
    setLoading(true)
    try {
      const [traitsRes, tempTraitsRes, evalsRes] = await Promise.all([
        fetch(`/api/student/${student.id}/traits`),
        fetch(`/api/student/${student.id}/temp-traits`),
        fetch(`/api/evaluations/${student.id}`),
      ])
      setStudentDetail({
        traits: await traitsRes.json(),
        temp_traits: await tempTraitsRes.json(),
        evaluations: await evalsRes.json(),
      })
    } catch { setError('获取学生详情失败') }
    finally { setLoading(false) }
  }

  const handleAdoptAndCopy = async (evalId: number, text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      await fetch(`/api/evaluation/${evalId}/adopt`, { method: 'POST' })
      setCopiedId(evalId)
      setTimeout(() => setCopiedId(null), 2000)
      // Refresh
      const res = await fetch(`/api/evaluations/${student.id}`)
      const evals = await res.json()
      setStudentDetail((prev: any) => prev ? { ...prev, evaluations: evals } : prev)
    } catch { setError('复制失败') }
  }

  const badgeColors: Record<string, string> = {
    '80/20': 'bg-green-500', '65/35': 'bg-blue-500', '90/10': 'bg-yellow-500',
  }
  const borderColors: Record<string, string> = {
    '80/20': 'border-green-300 bg-green-50/30',
    '65/35': 'border-blue-300 bg-blue-50/30',
    '90/10': 'border-yellow-300 bg-yellow-50/30',
  }

  if (loading) return (
    <div className="bg-white rounded-xl p-4 shadow-sm text-center">
      <div className="animate-spin text-2xl mb-2">⏳</div>
      <p className="text-sm text-gray-600">加载中...</p>
    </div>
  )

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="text-blue-500 text-sm">← 返回列表</button>

      {/* Basic Info */}
      <div className="bg-white rounded-xl p-4 shadow-sm">
        <h2 className="text-sm font-medium text-gray-700 mb-3">{student.name} - 个人信息</h2>
        <div className="space-y-2">
          {student.class_name && (
            <div className="text-sm text-gray-600">班级：{student.class_name}</div>
          )}
          <div className="text-sm text-gray-600">原始特点：{student.traits_raw || '无'}</div>
          {studentDetail?.traits && studentDetail.traits.length > 0 && (
            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">长期性格画像：</div>
              <div className="flex flex-wrap gap-1">
                {studentDetail.traits.map((t: any) => (
                  <span key={t.trait} className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">{t.trait} {t.percentage}%</span>
                ))}
              </div>
            </div>
          )}
          {studentDetail?.temp_traits && studentDetail.temp_traits.length > 0 && (
            <div>
              <div className="text-xs font-medium text-gray-500 mb-1">当堂表现：</div>
              <div className="flex flex-wrap gap-1">
                {studentDetail.temp_traits.map((t: any) => (
                  <span key={t.trait} className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">{t.trait} {t.percentage}%</span>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* History Evaluations - all shown, with adopted status */}
      {studentDetail?.evaluations && studentDetail.evaluations.length > 0 && (
        <div className="bg-white rounded-xl p-4 shadow-sm">
          <h2 className="text-sm font-medium text-gray-700 mb-3">历史评价 ({studentDetail.evaluations.length})</h2>
          <div className="space-y-3">
            {studentDetail.evaluations.map((ev: any) => (
              <div key={ev.id} className={`rounded-xl p-4 border-2 ${borderColors[ev.template] || 'border-gray-200'}`}>
                <div className="flex justify-between items-center mb-3">
                  <div className="flex items-center gap-2">
                    <span className={`text-xs text-white px-2 py-1 rounded-full ${badgeColors[ev.template] || 'bg-gray-500'}`}>{ev.template}</span>
                    <span className="text-xs text-gray-500">{ev.lesson_date}</span>
                    {ev.is_adopted && (
                      <span className="text-xs bg-green-600 text-white px-2 py-0.5 rounded-full">已采纳</span>
                    )}
                  </div>
                  <button
                    onClick={() => handleAdoptAndCopy(ev.id, ev.content)}
                    className={`text-xs px-3 py-1.5 rounded-lg font-medium ${
                      copiedId === ev.id ? 'bg-green-500 text-white' : 'bg-white border border-gray-200 hover:bg-gray-50'
                    }`}
                  >
                    {copiedId === ev.id ? '✓ 已复制并采纳' : '📋 复制并采纳'}
                  </button>
                </div>
                <div className="text-sm text-gray-700 whitespace-pre-wrap bg-white/70 rounded-lg p-3">{ev.content}</div>
                {ev.lesson_content && <div className="text-xs text-gray-500 mt-2">课程内容：{ev.lesson_content}</div>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

export default App
