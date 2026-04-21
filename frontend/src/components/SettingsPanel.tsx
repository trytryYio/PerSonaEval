interface SettingsPanelProps {
  apiKey: string
  onApiKeyChange: (key: string) => void
}

export default function SettingsPanel({ apiKey, onApiKeyChange }: SettingsPanelProps) {
  return (
    <div className="bg-white rounded-xl p-4 shadow-sm">
      <label className="block text-sm font-medium text-gray-700 mb-1">千问 API Key</label>
      <input
        type="password"
        value={apiKey}
        onChange={(e) => { onApiKeyChange(e.target.value); localStorage.setItem('dashscope_api_key', e.target.value) }}
        placeholder="sk-..."
        className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500"
      />
      <p className="text-xs text-gray-400 mt-1">保存在浏览器本地</p>
    </div>
  )
}
