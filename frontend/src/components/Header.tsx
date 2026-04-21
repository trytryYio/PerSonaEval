import type { Page } from '../types'

interface HeaderProps {
  page: Page
  onNavigate: (page: Page) => void
  onToggleSettings: () => void
  onBack?: () => void
}

export default function Header({ page, onNavigate, onToggleSettings, onBack }: HeaderProps) {
  const showNav = page === 'input' || page === 'preview' || page === 'classes'

  return (
    <header className="bg-white shadow-sm sticky top-0 z-10">
      <div className="max-w-4xl mx-auto px-4 py-3 flex justify-between items-center">
        <div className="flex items-center gap-2">
          {onBack && (
            <button onClick={onBack} className="text-blue-500 text-sm mr-1">←</button>
          )}
          <h1 className="text-lg font-bold text-gray-900">课后评价生成器</h1>
        </div>
        {showNav && (
          <div className="flex gap-1 items-center">
            <button onClick={() => onNavigate('input')} className={`text-sm px-3 py-1.5 rounded ${page === 'input' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>输入</button>
            <button onClick={() => onNavigate('classes')} className={`text-sm px-3 py-1.5 rounded ${page === 'classes' ? 'bg-blue-500 text-white' : 'text-gray-600 hover:bg-gray-100'}`}>班级</button>
            <button onClick={onToggleSettings} className="text-gray-500 ml-1">⚙️</button>
          </div>
        )}
        {!showNav && (
          <button onClick={onToggleSettings} className="text-gray-500">⚙️</button>
        )}
      </div>
    </header>
  )
}
