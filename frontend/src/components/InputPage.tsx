import { extractText } from '../api'
import type { ExtractedData } from '../types'

interface InputPageProps {
  rawText: string
  onRawTextChange: (text: string) => void
  onExtracted: (data: ExtractedData) => void
  onError: (msg: string) => void
  loading: boolean
  setLoading: (v: boolean) => void
  apiKey: string
}

export default function InputPage({
  rawText, onRawTextChange, onExtracted, onError,
  loading, setLoading, apiKey,
}: InputPageProps) {
  const handleExtract = async () => {
    if (!rawText.trim()) { onError('请输入学生信息'); return }
    setLoading(true)
    try {
      const data = await extractText(rawText, apiKey)
      onExtracted(data)
      if (data.students.length === 0) onError('未识别到学生信息')
    } catch (e: any) {
      onError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      {/* Step header */}
      <div className="bg-white rounded-xl p-4 shadow-sm">
        <div className="flex items-center gap-2 mb-3">
          <span className="w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center text-xs font-bold">1</span>
          <h2 className="text-sm font-medium text-gray-700">输入学生信息</h2>
        </div>

        <div className="bg-blue-50 border border-blue-100 rounded-lg p-3 mb-3">
          <p className="text-xs font-medium text-blue-700 mb-1">示例格式：</p>
          <pre className="text-xs text-blue-600 whitespace-pre-wrap font-sans">八年级 a 班 5 个学生
小明活泼好动喜欢上课跳舞，同时还是一个烦人精
小红比较安静但是做题很慢
小刚特别聪明就是有点懒</pre>
        </div>

        <textarea
          value={rawText}
          onChange={(e) => onRawTextChange(e.target.value)}
          placeholder="在这里粘贴学生信息..."
          rows={6}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 resize-none"
        />

        <button
          onClick={handleExtract}
          disabled={loading}
          className="mt-3 w-full bg-blue-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-blue-600 disabled:opacity-50"
        >
          {loading ? 'AI 分析中...' : '识别学生信息'}
        </button>
      </div>
    </div>
  )
}
