import { useState, useEffect } from 'react'

interface SavedStudent {
  id: number
  name: string
  traits_raw: string
  traits: { trait: string; percentage: number }[]
}

interface Evaluation {
  template: string
  label: string
  content: string
}

type Page = 'input' | 'preview' | 'classes'

function App() {
  const [page, setPage] = useState<Page>('input')
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('dashscope_api_key') || '')
  const [rawText, setRawText] = useState('')
  const [extracted, setExtracted] = useState<{ grade: string; class: string; students: { name: string; traits_raw: string; traits_pct: Record<string, number> }[] } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [classes, setClasses] = useState<{ id: number; grade: string; name: string; student_count: number }[]>([])
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null)
  const [classStudents, setClassStudents] = useState<SavedStudent[]>([])
  const [selectedStudent, setSelectedStudent] = useState<SavedStudent | null>(null)
  const [lessonContent, setLessonContent] = useState('')
  const [lessonNotes, setLessonNotes] = useState('')
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null)
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null)
  const [showSettings, setShowSettings] = useState(false)

  const API = (path: string) => `/api${path}`

  useEffect(() => {
    if (page === 'classes') fetchClasses()
  }, [page])

  const fetchClasses = async () => {
    try {
      const res = await fetch(API('/classes'))
      const data = await res.json()
      setClasses(data)
    } catch {
      setError('获取班级失败')
    }
  }

  const fetchClassStudents = async (classId: number) => {
    setLoading(true)
    try {
      const res = await fetch(API(`/students/${classId}`))
      const data = await res.json()
      setClassStudents(data)
      setSelectedClassId(classId)
      setSelectedStudent(null)
      setEvaluations([])
      setSelectedTemplate(null)
    } catch {
      setError('获取学生失败')
    } finally {
      setLoading(false)
    }
  }

  const handleExtract = async () => {
    if (!rawText.trim()) { setError('请输入学生信息'); return }
    setLoading(true); setError('')

    try {
      const res = await fetch(API('/extract'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: rawText, api_key: apiKey || undefined }),
      })
      if (!res.ok) throw new Error('提取失败')
      const data = await res.json()
      setExtracted(data)
      if (data.students.length === 0) setError('未识别到学生信息')
      else setPage('preview')
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleSave = async () => {
    if (!extracted) return
    setLoading(true); setError('')

    try {
      const res = await fetch(API('/save'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          grade: extracted.grade,
          class_name: extracted.class,
          students: extracted.students,
          total_count: extracted.total_count,
          api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) throw new Error('保存失败')
      setExtracted(null)
      setRawText('')
      setPage('classes')
      fetchClasses()
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleGenerate = async () => {
    if (!selectedStudent) { setError('请先选择学生'); return }
    if (!lessonContent.trim()) { setError('请输入课程内容'); return }
    setLoading(true); setError(''); setEvaluations([]); setSelectedTemplate(null)

    try {
      const res = await fetch(API('/generate'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          student_id: selectedStudent.id,
          lesson_content: lessonContent,
          lesson_notes: lessonNotes || undefined,
          api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) {
        const err = await res.json()
        throw new Error(err.detail || '生成失败')
      }
      const data = await res.json()
      setEvaluations(data)
      setSelectedTemplate(data[0]?.template || null)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const copyText = (text: string, idx: number) => {
    navigator.clipboard.writeText(text)
    setCopiedIdx(idx)
    setTimeout(() => setCopiedIdx(null), 1500)
  }

  const templateColors: Record<string, string> = {
    '80/20': 'border-green-400 bg-green-50/50',
    '65/35': 'border-blue-400 bg-blue-50/50',
    '90/10': 'border-yellow-400 bg-yellow-50/50',
  }

  const templateBadge: Record<string, string> = {
    '80/20': 'bg-green-500',
    '65/35': 'bg-blue-500',
    '90/10': 'bg-yellow-500',
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      {/* Header */}
      <header className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 py-3 flex justify-between items-center">
          <h1 className="text-lg font-bold text-gray-900">课后评价生成器</h1>
          <div className="flex gap-1 items-center">
            <button onClick={() => setPage('input')} className={`text-sm px-3 py-1.5 rounded ${page === 'input' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>输入</button>
            <button onClick={() => { setPage('classes'); setSelectedClassId(null); setClassStudents([]) }} className={`text-sm px-3 py-1.5 rounded ${page === 'classes' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>班级</button>
            <button onClick={() => setShowSettings(!showSettings)} className="text-gray-500 ml-1">⚙️</button>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 py-4 space-y-4">
        {/* Settings */}
        {showSettings && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <label className="block text-sm font-medium text-gray-700 mb-1">千问 API Key</label>
            <input type="password" value={apiKey} onChange={(e) => { setApiKey(e.target.value); localStorage.setItem('dashscope_api_key', e.target.value) }} placeholder="sk-..." className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
            <p className="text-xs text-gray-400 mt-1">保存在浏览器本地</p>
          </div>
        )}

        {/* Error */}
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
            <textarea value={rawText} onChange={(e) => setRawText(e.target.value)} placeholder={'例：\n八年级 a 班 5 个学生\n小明活泼好动喜欢上课跳舞，同时还是一个烦人精\n小红比较安静但是做题很慢\n小刚特别聪明就是有点懒'} rows={6} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 resize-none" />
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
        {page === 'classes' && (
          <div className="space-y-4">
            {/* Class list */}
            {!selectedClassId && (
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <h2 className="text-sm font-medium text-gray-700 mb-3">班级列表</h2>
                {classes.length === 0 ? (
                  <p className="text-gray-400 text-sm text-center py-4">暂无班级，请先输入学生信息</p>
                ) : classes.map((c) => (
                  <button key={c.id} onClick={() => fetchClassStudents(c.id)} className="w-full flex justify-between items-center p-3 bg-gray-50 rounded-lg mb-2 hover:bg-gray-100">
                    <span className="font-medium text-gray-900">{c.grade}{c.name}</span>
                    <span className="text-gray-400 text-sm">{c.student_count} 人</span>
                  </button>
                ))}
              </div>
            )}

            {/* Student list + generation */}
            {selectedClassId && !selectedStudent && (
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <button onClick={() => { setSelectedClassId(null); setClassStudents([]) }} className="text-blue-500 text-sm mb-3 block">← 返回班级列表</button>
                <h2 className="text-sm font-medium text-gray-700 mb-3">选择学生</h2>
                <div className="space-y-2">
                  {classStudents.map((s) => (
                    <button key={s.id} onClick={() => { setSelectedStudent(s); setEvaluations([]); setSelectedTemplate(null) }} className="w-full text-left p-3 bg-gray-50 rounded-lg hover:bg-gray-100">
                      <div className="flex justify-between items-center">
                        <span className="font-medium text-gray-900">{s.name}</span>
                        <span className="text-xs text-gray-400">点击生成评价</span>
                      </div>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {s.traits.map((t) => (
                          <span key={t.trait} className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">{t.trait} {t.percentage}%</span>
                        ))}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Generate panel */}
            {selectedStudent && (
              <div className="space-y-4">
                <div className="bg-white rounded-xl p-4 shadow-sm">
                  <div className="flex justify-between items-center mb-3">
                    <button onClick={() => { setSelectedStudent(null); setEvaluations([]) }} className="text-blue-500 text-sm">← 返回列表</button>
                    <span className="font-medium text-gray-900">{selectedStudent.name}</span>
                  </div>

                  {/* Input row: 课程内容 + 课堂表现 */}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">课程内容</label>
                      <input type="text" value={lessonContent} onChange={(e) => setLessonContent(e.target.value)} placeholder="例：while 循环" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">当堂表现（选填）</label>
                      <input type="text" value={lessonNotes} onChange={(e) => setLessonNotes(e.target.value)} placeholder="例：今天上课积极但不愿写代码" className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
                    </div>
                  </div>

                  <button onClick={handleGenerate} disabled={loading || !lessonContent} className="w-full bg-blue-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50">
                    {loading ? '生成中...' : '生成 3 份评价'}
                  </button>
                </div>

                {/* Template selector + evaluations */}
                {evaluations.length > 0 && (
                  <div className="space-y-3">
                    {/* Template tabs */}
                    <div className="flex gap-2">
                      {evaluations.map((ev) => (
                        <button
                          key={ev.template}
                          onClick={() => setSelectedTemplate(ev.template)}
                          className={`flex-1 py-2 px-3 rounded-lg text-sm font-medium border-2 transition-all ${
                            selectedTemplate === ev.template
                              ? `${templateBadge[ev.template]} text-white border-transparent shadow-sm`
                              : 'bg-white text-gray-600 border-gray-200 hover:border-gray-300'
                          }`}
                        >
                          {ev.template} {ev.label}
                        </button>
                      ))}
                    </div>

                    {/* Selected evaluation */}
                    {evaluations.map((ev, idx) => (
                      selectedTemplate === ev.template && (
                        <div key={idx} className={`rounded-xl p-4 border-2 ${templateColors[ev.template]}`}>
                          <div className="flex justify-between items-center mb-3">
                            <span className={`text-xs text-white px-2 py-1 rounded-full ${templateBadge[ev.template]}`}>
                              {ev.template} · {ev.label}
                            </span>
                            <button
                              onClick={() => copyText(ev.content, idx)}
                              className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-all ${
                                copiedIdx === idx
                                  ? 'bg-green-500 text-white'
                                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
                              }`}
                            >
                              {copiedIdx === idx ? '✓ 已复制' : '📋 复制'}
                            </button>
                          </div>
                          <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed bg-white rounded-lg p-3">
                            {ev.content}
                          </div>
                        </div>
                      )
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* Loading overlay */}
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

export default App
