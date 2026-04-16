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

  const templateColors: Record<string, { border: string; bg: string; badge: string }> = {
    '80/20': { border: 'border-[#7a9e8a]', bg: 'bg-[#f2f8f4]', badge: 'bg-[#7a9e8a]' },
    '65/35': { border: 'border-[#4a6f8a]', bg: 'bg-[#f0f5f9]', badge: 'bg-[#4a6f8a]' },
    '90/10': { border: 'border-[#c4613a]', bg: 'bg-[#fdf5f2]', badge: 'bg-[#c4613a]' },
  }

  const traitColors = ['bg-[#c4613a]/10 text-[#c4613a]', 'bg-[#7a9e8a]/10 text-[#5d8a70]', 'bg-[#4a6f8a]/10 text-[#4a6f8a]', 'bg-[#b8860b]/10 text-[#b8860b]']

  return (
    <div className="min-h-screen relative">
      {/* Header */}
      <header className="bg-white/80 backdrop-blur-sm sticky top-0 z-10 border-b border-[#e8e2db]">
        <div className="max-w-2xl mx-auto px-5 py-3.5 flex justify-between items-center">
          <h1 className="text-xl font-semibold text-[#2d2a26]" style={{ fontFamily: "'Noto Serif SC', serif" }}>
            课后评价
          </h1>
          <div className="flex gap-1 items-center">
            <button
              onClick={() => setPage('input')}
              className={`text-sm px-3 py-1.5 rounded-full transition-all duration-200 ${
                page === 'input'
                  ? 'bg-[#2d2a26] text-white shadow-sm'
                  : 'text-[#6b6560] hover:bg-[#f5f2ee]'
              }`}
              style={{ fontFamily: "'Noto Sans SC', sans-serif" }}
            >
              输入
            </button>
            <button
              onClick={() => { setPage('classes'); setSelectedClassId(null); setClassStudents([]) }}
              className={`text-sm px-3 py-1.5 rounded-full transition-all duration-200 ${
                page === 'classes'
                  ? 'bg-[#2d2a26] text-white shadow-sm'
                  : 'text-[#6b6560] hover:bg-[#f5f2ee]'
              }`}
              style={{ fontFamily: "'Noto Sans SC', sans-serif" }}
            >
              班级
            </button>
            <button
              onClick={() => setShowSettings(!showSettings)}
              className="ml-1 w-8 h-8 flex items-center justify-center rounded-full text-[#a89f96] hover:bg-[#f5f2ee] transition-colors"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="3" />
                <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
              </svg>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-5 py-5 space-y-5">
        {/* Settings Panel */}
        {showSettings && (
          <div className="animate-slide-down bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
            <label className="block text-sm font-medium text-[#2d2a26] mb-2" style={{ fontFamily: "'Noto Serif SC', serif" }}>
              千问 API Key
            </label>
            <input
              type="password"
              value={apiKey}
              onChange={(e) => { setApiKey(e.target.value); localStorage.setItem('dashscope_api_key', e.target.value) }}
              placeholder="sk-..."
              className="w-full px-4 py-2.5 border border-[#e8e2db] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#c4613a]/30 focus:border-[#c4613a] transition-all bg-[#faf8f5]"
            />
            <p className="text-xs text-[#a89f96] mt-2">保存在浏览器本地，不会上传服务器</p>
          </div>
        )}

        {/* Error Banner */}
        {error && (
          <div className="animate-fade-in-up bg-[#fdf5f2] border border-[#c4613a]/20 rounded-2xl p-4">
            <div className="flex items-start gap-3">
              <svg className="w-5 h-5 text-[#c4613a] mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <div className="flex-1">
                <p className="text-[#c4613a] text-sm">{error}</p>
                <button onClick={() => setError('')} className="text-[#c4613a]/60 text-xs mt-1 hover:text-[#c4613a] transition-colors">
                  关闭
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Page: Input */}
        {page === 'input' && (
          <div className="animate-fade-in-up bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
            <h2 className="text-base font-medium text-[#2d2a26] mb-4" style={{ fontFamily: "'Noto Serif SC', serif" }}>
              输入学生信息
            </h2>
            <div className="bg-[#f0f5f9] rounded-xl p-3.5 mb-4 border border-[#4a6f8a]/10">
              <p className="text-xs text-[#4a6f8a] leading-relaxed">
                <span className="font-medium">格式示例：</span><br />
                八年级 a 班 5 个学生<br />
                小明活泼好动喜欢上课跳舞，同时又是一个烦人精<br />
                小红比较安静但是做题很慢<br />
                小刚特别聪明就是有点懒
              </p>
            </div>
            <textarea
              value={rawText}
              onChange={(e) => setRawText(e.target.value)}
              placeholder="在此粘贴学生描述..."
              rows={6}
              className="w-full px-4 py-3 border border-[#e8e2db] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#c4613a]/30 focus:border-[#c4613a] transition-all resize-none bg-[#faf8f5] placeholder:text-[#a89f96]"
            />
            <button
              onClick={handleExtract}
              disabled={loading}
              className="mt-3 w-full bg-[#2d2a26] text-white py-3 rounded-xl text-sm font-medium hover:bg-[#3d3a36] disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 shadow-sm hover:shadow-md"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg className="w-4 h-4 animate-spin-slow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <circle cx="12" cy="12" r="10" strokeOpacity="0.3" />
                    <path d="M12 2a10 10 0 0 1 10 10" />
                  </svg>
                  AI 分析中...
                </span>
              ) : '识别学生信息'}
            </button>
          </div>
        )}

        {/* Page: Preview */}
        {page === 'preview' && extracted && (
          <div className="animate-fade-in-up space-y-4">
            <div className="bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
              <h2 className="text-base font-medium text-[#2d2a26] mb-1" style={{ fontFamily: "'Noto Serif SC', serif" }}>
                {extracted.grade}{extracted.class}
              </h2>
              <p className="text-sm text-[#a89f96] mb-4">识别到 {extracted.students.length} 名学生</p>

              <div className="space-y-2.5 mb-5">
                {extracted.students.map((s, i) => (
                  <div key={i} className="p-3.5 bg-[#faf8f5] rounded-xl border border-[#e8e2db]/60" style={{ animationDelay: `${i * 60}ms` }}>
                    <div className="flex items-center gap-2 mb-1.5">
                      <div className="w-7 h-7 rounded-full bg-[#c4613a]/10 flex items-center justify-center text-[#c4613a] text-xs font-medium">
                        {s.name.charAt(0)}
                      </div>
                      <span className="font-medium text-[#2d2a26]">{s.name}</span>
                    </div>
                    <div className="text-xs text-[#a89f96] mt-1 leading-relaxed">{s.traits_raw}</div>
                    {s.traits_pct && Object.keys(s.traits_pct).length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mt-2.5">
                        {Object.entries(s.traits_pct).map(([k, v], idx) => (
                          <span key={k} className={`text-xs px-2 py-0.5 rounded-full ${traitColors[idx % traitColors.length]}`}>
                            {k} {v}%
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>

              <div className="flex gap-2.5">
                <button
                  onClick={() => setPage('input')}
                  className="flex-1 bg-[#f5f2ee] text-[#6b6560] py-3 rounded-xl text-sm font-medium hover:bg-[#ebe6df] transition-colors"
                >
                  取消
                </button>
                <button
                  onClick={handleSave}
                  disabled={loading}
                  className="flex-1 bg-[#7a9e8a] text-white py-3 rounded-xl text-sm font-medium hover:bg-[#6a8e7a] disabled:opacity-50 transition-all shadow-sm"
                >
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <svg className="w-4 h-4 animate-spin-slow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <circle cx="12" cy="12" r="10" strokeOpacity="0.3" />
                        <path d="M12 2a10 10 0 0 1 10 10" />
                      </svg>
                      保存中...
                    </span>
                  ) : '确认保存'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Page: Classes */}
        {page === 'classes' && (
          <div className="space-y-4">
            {/* Class list */}
            {!selectedClassId && (
              <div className="animate-fade-in-up bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
                <h2 className="text-base font-medium text-[#2d2a26] mb-4" style={{ fontFamily: "'Noto Serif SC', serif" }}>
                  班级列表
                </h2>
                {classes.length === 0 ? (
                  <div className="text-center py-8">
                    <svg className="w-12 h-12 text-[#e8e2db] mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5">
                      <path d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                    </svg>
                    <p className="text-[#a89f96] text-sm">暂无班级</p>
                    <button onClick={() => setPage('input')} className="text-[#c4613a] text-sm mt-1 hover:underline">
                      去添加学生 →
                    </button>
                  </div>
                ) : classes.map((c, i) => (
                  <button
                    key={c.id}
                    onClick={() => fetchClassStudents(c.id)}
                    className="w-full flex justify-between items-center p-3.5 bg-[#faf8f5] rounded-xl mb-2 hover:bg-[#f5f2ee] border border-[#e8e2db]/60 transition-all duration-200 group"
                    style={{ animationDelay: `${i * 50}ms` }}
                  >
                    <span className="font-medium text-[#2d2a26] group-hover:text-[#c4613a] transition-colors" style={{ fontFamily: "'Noto Serif SC', serif" }}>
                      {c.grade}{c.name}
                    </span>
                    <span className="text-xs text-[#a89f96] bg-white px-2.5 py-1 rounded-full border border-[#e8e2db]">
                      {c.student_count} 人
                    </span>
                  </button>
                ))}
              </div>
            )}

            {/* Student list */}
            {selectedClassId && !selectedStudent && (
              <div className="animate-fade-in-up bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
                <button
                  onClick={() => { setSelectedClassId(null); setClassStudents([]) }}
                  className="text-[#4a6f8a] text-sm mb-4 flex items-center gap-1 hover:underline transition-colors"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M19 12H5M12 19l-7-7 7-7" />
                  </svg>
                  返回班级列表
                </button>
                <h2 className="text-base font-medium text-[#2d2a26] mb-4" style={{ fontFamily: "'Noto Serif SC', serif" }}>
                  选择学生
                </h2>
                <div className="space-y-2">
                  {classStudents.map((s, i) => (
                    <button
                      key={s.id}
                      onClick={() => { setSelectedStudent(s); setEvaluations([]); setSelectedTemplate(null) }}
                      className="w-full text-left p-3.5 bg-[#faf8f5] rounded-xl hover:bg-[#f5f2ee] border border-[#e8e2db]/60 transition-all duration-200 group"
                      style={{ animationDelay: `${i * 40}ms` }}
                    >
                      <div className="flex justify-between items-center">
                        <div className="flex items-center gap-2.5">
                          <div className="w-8 h-8 rounded-full bg-[#4a6f8a]/10 flex items-center justify-center text-[#4a6f8a] text-sm font-medium">
                            {s.name.charAt(0)}
                          </div>
                          <span className="font-medium text-[#2d2a26]">{s.name}</span>
                        </div>
                        <span className="text-xs text-[#a89f96] group-hover:text-[#c4613a] transition-colors">生成评价 →</span>
                      </div>
                      <div className="flex flex-wrap gap-1.5 mt-2 ml-10">
                        {s.traits.map((t, idx) => (
                          <span key={t.trait} className={`text-xs px-2 py-0.5 rounded-full ${traitColors[idx % traitColors.length]}`}>
                            {t.trait} {t.percentage}%
                          </span>
                        ))}
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Generate panel */}
            {selectedStudent && (
              <div className="animate-fade-in-up space-y-4">
                <div className="bg-white rounded-2xl p-5 shadow-md border border-[#e8e2db]">
                  <div className="flex items-center gap-3 mb-4">
                    <button
                      onClick={() => { setSelectedStudent(null); setEvaluations([]) }}
                      className="text-[#4a6f8a] text-sm flex items-center gap-1 hover:underline transition-colors"
                    >
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M19 12H5M12 19l-7-7 7-7" />
                      </svg>
                      返回
                    </button>
                    <div className="flex-1" />
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-full bg-[#c4613a]/10 flex items-center justify-center text-[#c4613a] text-sm font-medium">
                        {selectedStudent.name.charAt(0)}
                      </div>
                      <span className="font-medium text-[#2d2a26]" style={{ fontFamily: "'Noto Serif SC', serif" }}>
                        {selectedStudent.name}
                      </span>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-3">
                    <div>
                      <label className="block text-xs font-medium text-[#6b6560] mb-1.5">课程内容</label>
                      <input
                        type="text"
                        value={lessonContent}
                        onChange={(e) => setLessonContent(e.target.value)}
                        placeholder="例：while 循环"
                        className="w-full px-4 py-2.5 border border-[#e8e2db] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#c4613a]/30 focus:border-[#c4613a] transition-all bg-[#faf8f5] placeholder:text-[#a89f96]"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-[#6b6560] mb-1.5">当堂表现 <span className="text-[#a89f96] font-normal">（选填）</span></label>
                      <input
                        type="text"
                        value={lessonNotes}
                        onChange={(e) => setLessonNotes(e.target.value)}
                        placeholder="例：上课积极但不愿写代码"
                        className="w-full px-4 py-2.5 border border-[#e8e2db] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#c4613a]/30 focus:border-[#c4613a] transition-all bg-[#faf8f5] placeholder:text-[#a89f96]"
                      />
                    </div>
                  </div>

                  <button
                    onClick={handleGenerate}
                    disabled={loading || !lessonContent}
                    className="w-full bg-[#2d2a26] text-white py-3 rounded-xl text-sm font-medium hover:bg-[#3d3a36] disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 shadow-sm hover:shadow-md"
                  >
                    {loading ? (
                      <span className="flex items-center justify-center gap-2">
                        <svg className="w-4 h-4 animate-spin-slow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                          <circle cx="12" cy="12" r="10" strokeOpacity="0.3" />
                          <path d="M12 2a10 10 0 0 1 10 10" />
                        </svg>
                        生成中...
                      </span>
                    ) : '生成 3 份评价'}
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
                          className={`flex-1 py-2.5 px-3 rounded-xl text-sm font-medium border-2 transition-all duration-200 ${
                            selectedTemplate === ev.template
                              ? `${templateColors[ev.template].badge} text-white border-transparent shadow-sm`
                              : 'bg-white text-[#6b6560] border-[#e8e2db] hover:border-[#d5d0c9]'
                          }`}
                        >
                          {ev.template}
                        </button>
                      ))}
                    </div>

                    {/* Selected evaluation */}
                    {evaluations.map((ev, idx) => (
                      selectedTemplate === ev.template && (
                        <div key={idx} className={`rounded-2xl border-2 ${templateColors[ev.template].border} ${templateColors[ev.template].bg} overflow-hidden`}>
                          <div className="flex justify-between items-center px-4 py-3">
                            <span className={`text-xs text-white px-2.5 py-1 rounded-full ${templateColors[ev.template].badge}`}>
                              {ev.template} · {ev.label}
                            </span>
                            <button
                              onClick={() => copyText(ev.content, idx)}
                              className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-all duration-200 ${
                                copiedIdx === idx
                                  ? 'bg-[#7a9e8a] text-white'
                                  : 'bg-white text-[#6b6560] border border-[#e8e2db] hover:bg-[#faf8f5]'
                              }`}
                            >
                              {copiedIdx === idx ? '✓ 已复制' : '复制'}
                            </button>
                          </div>
                          <div className="text-sm text-[#2d2a26] whitespace-pre-wrap leading-relaxed bg-white/80 mx-4 mb-4 rounded-xl p-4 border border-white/60">
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
      </main>

      {/* Loading overlay */}
      {loading && (
        <div className="fixed inset-0 bg-[#2d2a26]/20 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl p-6 shadow-lg border border-[#e8e2db] text-center">
            <svg className="w-8 h-8 text-[#c4613a] mx-auto mb-3 animate-spin-slow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" strokeOpacity="0.2" />
              <path d="M12 2a10 10 0 0 1 10 10" strokeLinecap="round" />
            </svg>
            <p className="text-sm text-[#6b6560]">处理中...</p>
          </div>
        </div>
      )}
    </div>
  )
}

export default App
