import { useState, useEffect } from 'react'

interface Student {
  name: string
  traits_raw: string
  traits_pct: Record<string, number>
}

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

type Page = 'input' | 'preview' | 'classes' | 'student'

function App() {
  const [page, setPage] = useState<Page>('input')
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('dashscope_api_key') || '')
  const [rawText, setRawText] = useState('')
  const [extracted, setExtracted] = useState<{ grade: string; class: string; students: Student[] } | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [classes, setClasses] = useState<{ id: number; grade: string; name: string; student_count: number }[]>([])
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null)
  const [classStudents, setClassStudents] = useState<SavedStudent[]>([])
  const [selectedStudent, setSelectedStudent] = useState<SavedStudent | null>(null)
  const [lessonContent, setLessonContent] = useState('')
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
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
      const data = await res.json()
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

  const handleGenerate = async (studentId: number) => {
    if (!lessonContent.trim()) { setError('请输入课程内容'); return }
    setLoading(true); setError(''); setEvaluations([])

    try {
      const res = await fetch(API('/generate'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          student_id: studentId,
          lesson_content: lessonContent,
          api_key: apiKey || undefined,
        }),
      })
      if (!res.ok) {
        const err = await res.json()
        throw new Error(err.detail || '生成失败')
      }
      const data = await res.json()
      setEvaluations(data)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const copyText = (text: string) => {
    navigator.clipboard.writeText(text)
  }

  const templateColors: Record<string, string> = {
    '80/20': 'bg-green-50 border-green-300',
    '65/35': 'bg-blue-50 border-blue-300',
    '90/10': 'bg-yellow-50 border-yellow-300',
  }

  const templateBadges: Record<string, string> = {
    '80/20': 'bg-green-500',
    '65/35': 'bg-blue-500',
    '90/10': 'bg-yellow-500',
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      {/* Header */}
      <header className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-lg mx-auto px-4 py-3 flex justify-between items-center">
          <h1 className="text-lg font-bold text-gray-900">课后评价生成器</h1>
          <div className="flex gap-2">
            <button onClick={() => setPage('input')} className={`text-sm px-3 py-1 rounded ${page === 'input' ? 'bg-blue-500 text-white' : 'text-gray-600'}`}>输入</button>
            <button onClick={() => setPage('classes')} className={`text-sm px-3 py-1 rounded ${page === 'classes' || page === 'student' ? 'bg-blue-500 text-white' : 'text-gray-600'}`}>班级</button>
            <button onClick={() => setShowSettings(!showSettings)} className="text-gray-500">⚙️</button>
          </div>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-4 py-4 space-y-4">
        {/* Settings */}
        {showSettings && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <label className="block text-sm font-medium text-gray-700 mb-1">千问 API Key</label>
            <input type="password" value={apiKey} onChange={(e) => { setApiKey(e.target.value); localStorage.setItem('dashscope_api_key', e.target.value) }} placeholder="sk-..." className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500" />
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
          <div className="space-y-4">
            <div className="bg-white rounded-xl p-4 shadow-sm">
              <label className="block text-sm font-medium text-gray-700 mb-2">输入学生信息</label>
              <textarea value={rawText} onChange={(e) => setRawText(e.target.value)} placeholder={'例：\n八年级 a 班 5 个学生\n小明活泼好动喜欢上课跳舞，同时还是一个烦人精\n小红比较安静但是做题很慢\n小刚特别聪明就是有点懒'} rows={6} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 resize-none" />
              <button onClick={handleExtract} disabled={loading} className="mt-3 w-full bg-blue-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50">
                {loading ? 'AI 分析中...' : '识别学生信息'}
              </button>
            </div>
          </div>
        )}

        {/* Page: Preview */}
        {page === 'preview' && extracted && (
          <div className="space-y-4">
            <div className="bg-white rounded-xl p-4 shadow-sm">
              <h2 className="text-sm font-medium text-gray-700 mb-2">{extracted.grade}{extracted.class} - {extracted.students.length} 名学生</h2>
              <div className="space-y-2">
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
              <div className="flex gap-2 mt-4">
                <button onClick={() => setPage('input')} className="flex-1 bg-gray-200 text-gray-700 py-2.5 rounded-lg text-sm">取消</button>
                <button onClick={handleSave} disabled={loading} className="flex-1 bg-green-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50">
                  {loading ? '保存中...' : '确认保存到数据库'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Page: Classes */}
        {page === 'classes' && (
          <div className="space-y-4">
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

            {selectedClassId && (
              <div className="bg-white rounded-xl p-4 shadow-sm">
                <div className="flex justify-between items-center mb-3">
                  <button onClick={() => { setSelectedClassId(null); setClassStudents([]) }} className="text-blue-500 text-sm">← 返回</button>
                  <div className="flex-1 mx-4">
                    <input type="text" value={lessonContent} onChange={(e) => setLessonContent(e.target.value)} placeholder="输入本节课内容..." className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
                  </div>
                </div>
                <div className="space-y-2">
                  {classStudents.map((s) => (
                    <div key={s.id} className="p-3 bg-gray-50 rounded-lg">
                      <div className="flex justify-between items-center">
                        <div>
                          <span className="font-medium text-gray-900">{s.name}</span>
                          <div className="flex flex-wrap gap-1 mt-1">
                            {s.traits.map((t) => (
                              <span key={t.trait} className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">{t.trait} {t.percentage}%</span>
                            ))}
                          </div>
                        </div>
                        <button onClick={() => { setSelectedStudent(s); handleGenerate(s.id) }} disabled={loading || !lessonContent} className="bg-blue-500 text-white px-3 py-1.5 rounded-lg text-xs disabled:opacity-50">
                          生成评价
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Evaluations */}
            {evaluations.length > 0 && (
              <div className="space-y-3">
                {evaluations.map((ev, i) => (
                  <div key={i} className={`rounded-xl p-4 border ${templateColors[ev.template] || 'bg-white border-gray-200'}`}>
                    <div className="flex justify-between items-center mb-2">
                      <span className={`text-xs text-white px-2 py-0.5 rounded ${templateBadges[ev.template]}`}>{ev.template} {ev.label}</span>
                      <button onClick={() => copyText(ev.content)} className="text-gray-400 hover:text-gray-600 text-xs">复制</button>
                    </div>
                    <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">{ev.content}</div>
                  </div>
                ))}
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
