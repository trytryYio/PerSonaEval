import { useState } from 'react'

interface Student {
  name: string
  personality: string
  notes: string
}

interface Evaluation {
  name: string
  evaluation: string
  humanized: string
}

function App() {
  const [apiKey, setApiKey] = useState(() => localStorage.getItem('dashscope_api_key') || '')
  const [showSettings, setShowSettings] = useState(false)
  const [rawText, setRawText] = useState('')
  const [lessonContent, setLessonContent] = useState('')
  const [students, setStudents] = useState<Student[]>([])
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleExtract = async () => {
    if (!rawText.trim()) {
      setError('请输入学生信息')
      return
    }
    setLoading(true)
    setError('')
    setEvaluations([])

    try {
      const res = await fetch('/api/extract', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: rawText }),
      })

      if (!res.ok) throw new Error('提取失败')
      const data = await res.json()
      setStudents(data.students)

      if (data.students.length === 0) {
        setError('未识别到学生信息，请检查输入格式')
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '请求失败，请检查后端服务')
    } finally {
      setLoading(false)
    }
  }

  const handleGenerate = async (student?: Student) => {
    if (!lessonContent.trim()) {
      setError('请输入课程内容')
      return
    }
    setLoading(true)
    setError('')

    const targets = student ? [student] : students
    if (targets.length === 0) {
      setError('请先提取学生信息')
      setLoading(false)
      return
    }

    try {
      const results: Evaluation[] = []
      for (const s of targets) {
        const res = await fetch('/api/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            student: s,
            lesson_content: lessonContent,
            api_key: apiKey || undefined,
          }),
        })

        if (!res.ok) throw new Error(`${s.name} 生成失败`)
        const data = await res.json()
        results.push(data)
      }

      setEvaluations(results)
    } catch (e) {
      setError(e instanceof Error ? e.message : '生成失败')
    } finally {
      setLoading(false)
    }
  }

  const saveApiKey = (key: string) => {
    setApiKey(key)
    localStorage.setItem('dashscope_api_key', key)
  }

  const copyEvaluation = (text: string) => {
    navigator.clipboard.writeText(text)
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      {/* Header */}
      <header className="bg-white shadow-sm sticky top-0 z-10">
        <div className="max-w-lg mx-auto px-4 py-3 flex justify-between items-center">
          <h1 className="text-lg font-bold text-gray-900">课后评价生成器</h1>
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="text-gray-500 hover:text-gray-700 p-2"
          >
            ⚙️
          </button>
        </div>
      </header>

      <main className="max-w-lg mx-auto px-4 py-4 space-y-4">
        {/* Settings Panel */}
        {showSettings && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              千问 API Key
            </label>
            <input
              type="password"
              value={apiKey}
              onChange={(e) => saveApiKey(e.target.value)}
              placeholder="sk-..."
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-xs text-gray-400 mt-1">保存在本地，不会上传到服务器</p>
          </div>
        )}

        {/* Input Section */}
        <div className="bg-white rounded-xl p-4 shadow-sm">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            学生信息
          </label>
          <textarea
            value={rawText}
            onChange={(e) => setRawText(e.target.value)}
            placeholder={'例：一年级三班 54 人 有张三性格活泼上课积极发言、李四比较内向...一共 54 个'}
            rows={4}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
          <button
            onClick={handleExtract}
            disabled={loading}
            className="mt-2 w-full bg-blue-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50"
          >
            {loading ? '提取中...' : '识别学生信息'}
          </button>
        </div>

        {/* Lesson Content */}
        <div className="bg-white rounded-xl p-4 shadow-sm">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            本节课内容
          </label>
          <input
            type="text"
            value={lessonContent}
            onChange={(e) => setLessonContent(e.target.value)}
            placeholder="例：while 循环 / C++ for 循环"
            className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* Student List */}
        {students.length > 0 && (
          <div className="bg-white rounded-xl p-4 shadow-sm">
            <div className="flex justify-between items-center mb-3">
              <h2 className="text-sm font-medium text-gray-700">
                识别到 {students.length} 名学生
              </h2>
              <button
                onClick={() => handleGenerate()}
                disabled={loading || !lessonContent}
                className="bg-green-500 text-white px-4 py-1.5 rounded-lg text-xs font-medium hover:bg-green-600 disabled:opacity-50"
              >
                {loading ? '生成中...' : '全部生成'}
              </button>
            </div>
            <div className="space-y-2">
              {students.map((s, i) => (
                <div
                  key={i}
                  className="flex justify-between items-center p-3 bg-gray-50 rounded-lg"
                >
                  <div>
                    <span className="font-medium text-gray-900">{s.name}</span>
                    {s.personality && (
                      <span className="text-xs text-gray-500 ml-2">{s.personality}</span>
                    )}
                  </div>
                  <button
                    onClick={() => handleGenerate(s)}
                    disabled={loading || !lessonContent}
                    className="text-blue-500 text-xs font-medium disabled:opacity-50"
                  >
                    生成评价
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4">
            <p className="text-red-600 text-sm">{error}</p>
          </div>
        )}

        {/* Evaluations */}
        {evaluations.length > 0 && (
          <div className="space-y-4">
            <h2 className="text-sm font-medium text-gray-700 px-1">
              生成结果 ({evaluations.length} 条)
            </h2>
            {evaluations.map((ev, i) => (
              <div key={i} className="bg-white rounded-xl p-4 shadow-sm">
                <div className="flex justify-between items-center mb-3">
                  <h3 className="font-medium text-gray-900">{ev.name}</h3>
                  <button
                    onClick={() => copyEvaluation(ev.humanized)}
                    className="text-gray-400 hover:text-gray-600 text-xs"
                  >
                    复制
                  </button>
                </div>
                <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">
                  {ev.humanized}
                </div>
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

export default App
