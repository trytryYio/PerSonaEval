export type Page = 'input' | 'preview' | 'classes' | 'class-detail' | 'student-detail'

export interface SavedStudent {
  id: number
  name: string
  traits_raw: string
  traits: { trait: string; percentage: number }[]
}

export interface Evaluation {
  template: string
  label: string
  content: string
}

export interface ExtractedStudent {
  name: string
  traits_raw: string
  traits_pct: Record<string, number>
}

export interface ExtractedData {
  grade: string
  class: string
  students: ExtractedStudent[]
  total_count: number
}

export interface ClassInfo {
  id: number
  grade: string
  name: string
  student_count: number
  created_at?: string
}

export interface StudentWithClass extends SavedStudent {
  class_id: number
  class_name: string
}

export interface StudentDetail {
  id: number
  name: string
  class_id: number
  class_grade: string
  class_name: string
  traits_raw: string
  long_term_traits: { id: number; trait: string; percentage: number; updated_at?: string }[]
  temp_traits: { id: number; trait: string; percentage: number; created_at?: string }[]
  evaluations: { id: number; template: string; content: string; lesson_content: string; lesson_date: string; created_at: string }[]
  created_at: string
}

export interface BatchGenerateRequest {
  class_id: int
  student_ids?: number[]
  lesson_content: string
  lesson_notes?: string
  api_key?: string
}

export interface BatchGenerateResult {
  student_id: number
  student_name: string
  evaluations?: Evaluation[]
  error?: string
}

export interface BatchGenerateResponse {
  results: BatchGenerateResult[]
}

export interface TemplatePattern {
  placeholder: string
  description: string
  example?: string
}

export interface TemplateAnalysisResult {
  common_structures: {
    opening: string
    transition: string
    closing: string
  }
  replaceable_parts: TemplatePattern[]
  suggested_templates: string[]
}
