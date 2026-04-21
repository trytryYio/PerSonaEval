import { saveExtracted } from '../api'
import type { ExtractedData } from '../types'

interface PreviewPageProps {
  extracted: ExtractedData
  onSaveSuccess: () => void
  onCancel: () => void
  onError: (msg: string) => void
  loading: boolean
  setLoading: (v: boolean) => void
  apiKey: string
}

export default function PreviewPage({
  extracted, onSaveSuccess, onCancel, onError,
  loading, setLoading, apiKey,
}: PreviewPageProps) {
  const handleSave = async () => {
    setLoading(true)
    try {
      await saveExtracted({ ...extracted, total_count: extracted.students.length }, apiKey)
      onSaveSuccess()
    } catch (e: any) {
      onError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
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
        <button onClick={onCancel} className="flex-1 bg-gray-200 text-gray-700 py-2.5 rounded-lg text-sm">取消</button>
        <button onClick={handleSave} disabled={loading} className="flex-1 bg-green-500 text-white py-2.5 rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50">
          {loading ? '保存中...' : '确认保存到数据库'}
        </button>
      </div>
    </div>
  )
}
