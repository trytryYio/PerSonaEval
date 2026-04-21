import { useState, useEffect } from 'react'
import { generateEvaluation } from '../api'
import type { SavedStudent, Evaluation } from '../types'

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

interface StudentCardProps {
  student: SavedStudent
  lessonContent: string
  classAtmosphere: string
  onGenerate: (studentId: number, evaluations: Evaluation[]) => void
  onNavigateDetail: (studentId: number) => void
  apiKey: string
  generating: boolean
  setGenerating: (v: boolean) => void
  onError: (msg: string) => void
}

export default function StudentCard({
  student, lessonContent, classAtmosphere, onGenerate, onNavigateDetail,
  apiKey, generating, setGenerating, onError,
}: StudentCardProps) {
  const [performanceNotes, setPerformanceNotes] = useState('')
  const [evaluations, setEvaluations] = useState<Evaluation[]>(() => {
    const cached = localStorage.getItem(`evals_${student.id}`)
    return cached ? JSON.parse(cached) : []
  })
  const [acceptedTemplate, setAcceptedTemplate] = useState<string | null>(() => {
    return localStorage.getItem(`accepted_${student.id}`) || null
  })
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null)

  // Persist evaluations to localStorage
  useEffect(() => {
    if (evaluations.length > 0) {
      localStorage.setItem(`evals_${student.id}`, JSON.stringify(evaluations))
    }
  }, [evaluations, student.id])

  const handleGenerate = async () => {
    if (!lessonContent.trim()) { onError('请先输入课程内容'); return }
    setGenerating(true)
    try {
      const notes = performanceNotes + (classAtmosphere ? `\n课堂整体氛围：${classAtmosphere}` : '')
      const evals = await generateEvaluation(student.id, lessonContent, notes, apiKey)
      setEvaluations(evals)
      onGenerate(student.id, evals)
    } catch (e: any) {
      onError(e.message)
    } finally {
      setGenerating(false)
    }
  }

  const handleAdoptAndCopy = (text: string, template: string, idx: number) => {
    navigator.clipboard.writeText(text)
    localStorage.setItem(`accepted_${student.id}`, template)
    setAcceptedTemplate(template)
    setCopiedIdx(idx)
    setTimeout(() => setCopiedIdx(null), 1500)
  }

  return (
    <div className="bg-white rounded-xl p-4 shadow-sm">
      {/* Student header */}
      <div className="flex justify-between items-center mb-2">
        <div>
          <button onClick={() => onNavigateDetail(student.id)} className="font-medium text-gray-900 hover:text-blue-500 underline decoration-dotted underline-offset-2">
            {student.name}
          </button>
          <div className="flex flex-wrap gap-1 mt-1">
            {student.traits.map((t) => (
              <span key={t.trait} className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">{t.trait} {t.percentage}%</span>
            ))}
          </div>
        </div>
        <button
          onClick={handleGenerate}
          disabled={generating || !lessonContent}
          className="bg-blue-500 text-white px-3 py-1.5 rounded-lg text-xs font-medium hover:bg-blue-600 disabled:opacity-50 shrink-0 ml-2"
        >
          {generating ? '生成中...' : '生成'}
        </button>
      </div>

      {/* Per-student performance notes */}
      <div className="mb-3">
        <label className="block text-xs font-medium text-gray-600 mb-1">当堂表现</label>
        <input
          type="text"
          value={performanceNotes}
          onChange={(e) => setPerformanceNotes(e.target.value)}
          placeholder="例：今天上课积极但不愿写代码"
          className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* Evaluations - horizontal */}
      {evaluations.length > 0 && (
        <div className="grid grid-cols-3 gap-2 mt-3">
          {evaluations.map((ev, idx) => (
            <div key={idx} className={`rounded-xl p-3 border-2 transition-all ${
              acceptedTemplate === ev.template
                ? 'border-green-500 ring-2 ring-green-200 bg-green-50'
                : templateColors[ev.template]
            }`}>
              <div className="flex justify-between items-center mb-2">
                <span className={`text-xs text-white px-2 py-1 rounded-full ${templateBadge[ev.template]}`}>
                  {ev.template} · {ev.label}
                </span>
                <button
                  onClick={() => handleAdoptAndCopy(ev.content, ev.template, idx)}
                  className={`text-xs px-2 py-1 rounded-lg font-medium transition-all ${
                    copiedIdx === idx
                      ? 'bg-green-500 text-white'
                      : acceptedTemplate === ev.template
                        ? 'bg-green-100 text-green-700 border border-green-300'
                        : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
                  }`}
                >
                  {acceptedTemplate === ev.template && copiedIdx !== idx ? '✓ 已采纳' : copiedIdx === idx ? '✓ 已复制' : '采纳并复制'}
                </button>
              </div>
              <div className="text-xs text-gray-700 whitespace-pre-wrap leading-relaxed bg-white rounded-lg p-2 max-h-48 overflow-y-auto">
                {ev.content}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
