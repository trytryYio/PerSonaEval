import { useState, useEffect } from 'react'
import { fetchStudentDetail, createTrait, updateTrait, deleteTrait } from '../api'
import type { StudentDetail } from '../types'

interface StudentDetailPageProps {
  studentId: number
  onBack: () => void
  onError: (msg: string) => void
}

const templateBadge: Record<string, string> = {
  '80/20': 'bg-green-500',
  '65/35': 'bg-blue-500',
  '90/10': 'bg-yellow-500',
}

export default function StudentDetailPage({ studentId, onBack, onError }: StudentDetailPageProps) {
  const [detail, setDetail] = useState<StudentDetail | null>(null)
  const [showTraitForm, setShowTraitForm] = useState(false)
  const [newTraitName, setNewTraitName] = useState('')
  const [newTraitPct, setNewTraitPct] = useState(0)
  const [editingTraitId, setEditingTraitId] = useState<number | null>(null)
  const [editingTraitName, setEditingTraitName] = useState('')
  const [editingTraitPct, setEditingTraitPct] = useState(0)

  const loadDetail = () => {
    fetchStudentDetail(studentId)
      .then(setDetail)
      .catch((e) => onError(e.message))
  }

  useEffect(() => {
    loadDetail()
  }, [studentId])

  const handleAddTrait = async () => {
    if (!newTraitName.trim()) { onError('请填写特点名称'); return }
    try {
      await createTrait(studentId, newTraitName, newTraitPct)
      setNewTraitName('')
      setNewTraitPct(0)
      setShowTraitForm(false)
      loadDetail()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleEditTrait = async (id: number) => {
    if (!editingTraitName.trim()) return
    try {
      await updateTrait(studentId, id, { trait: editingTraitName, percentage: editingTraitPct })
      setEditingTraitId(null)
      loadDetail()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleDeleteTrait = async (id: number) => {
    if (!confirm('确定要删除这个特点吗？')) return
    try {
      await deleteTrait(studentId, id)
      loadDetail()
    } catch (e: any) {
      onError(e.message)
    }
  }

  if (!detail) return <div className="text-center py-8 text-gray-400">加载中...</div>

  return (
    <div className="space-y-4">
      {/* Back */}
      <button onClick={onBack} className="text-blue-500 text-sm block">← 返回</button>

      {/* Basic info */}
      <div className="bg-white rounded-xl p-4 shadow-sm">
        <h2 className="text-base font-bold text-gray-900 mb-2">{detail.name}</h2>
        <div className="grid grid-cols-2 gap-2 text-sm text-gray-600">
          <div>班级：{detail.class_grade}{detail.class_name}</div>
          <div>创建：{detail.created_at?.split('T')[0] || '-'}</div>
        </div>
        {detail.traits_raw && (
          <div className="mt-2 text-xs text-gray-500 bg-gray-50 rounded-lg p-2">{detail.traits_raw}</div>
        )}
      </div>

      {/* Long-term traits with CRUD */}
      <div className="bg-white rounded-xl p-4 shadow-sm">
        <div className="flex justify-between items-center mb-3">
          <h3 className="text-sm font-medium text-gray-700">长期性格画像</h3>
          <button onClick={() => setShowTraitForm(!showTraitForm)} className="text-xs bg-blue-500 text-white px-2 py-1 rounded hover:bg-blue-600">
            {showTraitForm ? '取消' : '+ 添加'}
          </button>
        </div>

        {/* Add form */}
        {showTraitForm && (
          <div className="mb-3 p-3 bg-blue-50 rounded-lg flex gap-2 items-center">
            <input type="text" value={newTraitName} onChange={(e) => setNewTraitName(e.target.value)} placeholder="特点名称" className="flex-1 px-2 py-1 border border-gray-300 rounded text-sm" />
            <input type="number" value={newTraitPct} onChange={(e) => setNewTraitPct(Number(e.target.value))} min={0} max={100} className="w-16 px-2 py-1 border border-gray-300 rounded text-sm" />
            <span className="text-xs text-gray-500">%</span>
            <button onClick={handleAddTrait} className="bg-blue-500 text-white px-3 py-1 rounded text-sm">确认</button>
          </div>
        )}

        {detail.long_term_traits.length === 0 ? (
          <p className="text-gray-400 text-sm text-center py-4">暂无性格特点</p>
        ) : (
          <div className="space-y-3">
            {detail.long_term_traits.map((t) => {
              const isEditing = editingTraitId === t.id
              return (
                <div key={t.id} className="p-2 bg-gray-50 rounded-lg">
                  {isEditing ? (
                    <div className="flex gap-2 items-center">
                      <input type="text" value={editingTraitName} onChange={(e) => setEditingTraitName(e.target.value)} className="w-24 px-2 py-1 border border-gray-300 rounded text-sm" />
                      <input type="number" value={editingTraitPct} onChange={(e) => setEditingTraitPct(Number(e.target.value))} min={0} max={100} className="w-16 px-2 py-1 border border-gray-300 rounded text-sm" />
                      <span className="text-xs text-gray-500">%</span>
                      <button onClick={() => handleEditTrait(t.id)} className="text-green-500 text-xs">保存</button>
                      <button onClick={() => setEditingTraitId(null)} className="text-gray-400 text-xs">取消</button>
                    </div>
                  ) : (
                    <div>
                      <div className="flex justify-between items-center mb-1">
                        <div className="flex items-center gap-1">
                          <span className="text-sm text-gray-700">{t.trait}</span>
                          <span className="text-xs text-gray-400">{t.percentage}%</span>
                        </div>
                        <div className="flex gap-1">
                          <button onClick={() => { setEditingTraitId(t.id); setEditingTraitName(t.trait); setEditingTraitPct(t.percentage) }} className="text-gray-400 text-xs hover:text-blue-500">✏️</button>
                          <button onClick={() => handleDeleteTrait(t.id)} className="text-gray-400 text-xs hover:text-red-500">🗑️</button>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <div className="flex-1 bg-gray-200 rounded-full h-2 overflow-hidden">
                          <div className="bg-blue-500 h-full rounded-full transition-all" style={{ width: `${t.percentage}%` }} />
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Temp traits */}
      {detail.temp_traits.length > 0 && (
        <div className="bg-white rounded-xl p-4 shadow-sm">
          <h3 className="text-sm font-medium text-gray-700 mb-3">当堂临时性格</h3>
          <div className="space-y-2">
            {detail.temp_traits.map((t) => (
              <div key={t.trait} className="flex items-center gap-2">
                <span className="text-xs text-gray-600 w-20 truncate">{t.trait}</span>
                <div className="flex-1 bg-gray-100 rounded-full h-3 overflow-hidden">
                  <div className="bg-yellow-500 h-full rounded-full" style={{ width: `${t.percentage}%` }} />
                </div>
                <span className="text-xs text-gray-500 w-10 text-right">{t.percentage}%</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Evaluation history */}
      {detail.evaluations.length > 0 && (
        <div className="bg-white rounded-xl p-4 shadow-sm">
          <h3 className="text-sm font-medium text-gray-700 mb-3">历史评价 ({detail.evaluations.length} 条)</h3>
          {(() => {
            const grouped: Record<string, typeof detail.evaluations> = {}
            for (const ev of detail.evaluations) {
              if (!grouped[ev.lesson_date]) grouped[ev.lesson_date] = []
              grouped[ev.lesson_date].push(ev)
            }
            return Object.entries(grouped).map(([date, evals]) => (
              <div key={date} className="mb-4 last:mb-0">
                <div className="text-xs font-medium text-gray-500 mb-2">{date} · {evals[0]?.lesson_content}</div>
                <div className="space-y-2">
                  {evals.map((ev) => (
                    <div key={ev.id} className="p-3 bg-gray-50 rounded-lg">
                      <span className={`text-xs text-white px-2 py-0.5 rounded-full ${templateBadge[ev.template]}`}>
                        {ev.template}
                      </span>
                      <div className="text-sm text-gray-700 mt-1 whitespace-pre-wrap">{ev.content}</div>
                    </div>
                  ))}
                </div>
              </div>
            ))
          })()}
        </div>
      )}
    </div>
  )
}
