import { useState, useEffect } from 'react'
import { fetchClasses, createClass, updateClass, deleteClass } from '../api'
import type { ClassInfo } from '../types'

interface ClassListPageProps {
  apiKey: string
  onSelectClass: (classId: number, name: string) => void
  onError: (msg: string) => void
}

export default function ClassListPage({ onSelectClass, onError }: ClassListPageProps) {
  const [classes, setClasses] = useState<ClassInfo[]>([])
  const [showAddForm, setShowAddForm] = useState(false)
  const [editId, setEditId] = useState<number | null>(null)
  const [editGrade, setEditGrade] = useState('')
  const [editName, setEditName] = useState('')
  const [newGrade, setNewGrade] = useState('')
  const [newName, setNewName] = useState('')

  useEffect(() => {
    loadClasses()
  }, [])

  const loadClasses = () => {
    fetchClasses().then(setClasses).catch(() => onError('获取班级失败'))
  }

  const handleAdd = async () => {
    if (!newGrade.trim() || !newName.trim()) { onError('请填写完整'); return }
    try {
      await createClass(newGrade, newName)
      setNewGrade('')
      setNewName('')
      setShowAddForm(false)
      loadClasses()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleEdit = async (id: number) => {
    if (!editGrade.trim() || !editName.trim()) return
    try {
      await updateClass(id, { grade: editGrade, name: editName })
      setEditId(null)
      loadClasses()
    } catch (e: any) {
      onError(e.message)
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定要删除这个班级吗？该班学生也会被隐藏。')) return
    try {
      await deleteClass(id)
      loadClasses()
    } catch (e: any) {
      onError(e.message)
    }
  }

  return (
    <div className="bg-white rounded-xl p-4 shadow-sm">
      <div className="flex justify-between items-center mb-3">
        <h2 className="text-sm font-medium text-gray-700">班级列表</h2>
        <button onClick={() => setShowAddForm(!showAddForm)} className="text-xs bg-blue-500 text-white px-3 py-1.5 rounded-lg hover:bg-blue-600">
          {showAddForm ? '取消' : '+ 添加班级'}
        </button>
      </div>

      {/* Add form */}
      {showAddForm && (
        <div className="mb-3 p-3 bg-blue-50 rounded-lg">
          <div className="grid grid-cols-2 gap-2 mb-2">
            <input type="text" value={newGrade} onChange={(e) => setNewGrade(e.target.value)} placeholder="年级（如：八年级）" className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            <input type="text" value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="班级（如：a 班）" className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          </div>
          <button onClick={handleAdd} className="bg-blue-500 text-white px-4 py-1.5 rounded-lg text-sm hover:bg-blue-600">确认添加</button>
        </div>
      )}

      {/* Class list */}
      {classes.length === 0 ? (
        <p className="text-gray-400 text-sm text-center py-4">暂无班级，请先输入学生信息或添加班级</p>
      ) : classes.map((c) => (
        <div key={c.id} className="flex justify-between items-center p-3 bg-gray-50 rounded-lg mb-2">
          {editId === c.id ? (
            <div className="flex gap-2 flex-1">
              <input type="text" value={editGrade} onChange={(e) => setEditGrade(e.target.value)} className="w-24 px-2 py-1 border border-gray-300 rounded text-sm" />
              <input type="text" value={editName} onChange={(e) => setEditName(e.target.value)} className="w-20 px-2 py-1 border border-gray-300 rounded text-sm" />
              <button onClick={() => handleEdit(c.id)} className="text-green-500 text-sm">保存</button>
              <button onClick={() => setEditId(null)} className="text-gray-400 text-sm">取消</button>
            </div>
          ) : (
            <>
              <button onClick={() => onSelectClass(c.id, `${c.grade}${c.name}`)} className="flex-1 text-left">
                <span className="font-medium text-gray-900">{c.grade}{c.name}</span>
                <span className="text-gray-400 text-sm ml-2">{c.student_count} 人</span>
              </button>
              <div className="flex gap-1 ml-2">
                <button onClick={() => { setEditId(c.id); setEditGrade(c.grade); setEditName(c.name) }} className="text-gray-400 text-xs px-2 py-1 hover:text-blue-500">✏️</button>
                <button onClick={() => handleDelete(c.id)} className="text-gray-400 text-xs px-2 py-1 hover:text-red-500">🗑️</button>
              </div>
            </>
          )}
        </div>
      ))}
    </div>
  )
}
