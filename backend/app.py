"""
FastAPI 后端服务 - 提供 REST API 接口
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List

from extractor import extract_all
from generator import generate_evaluation, batch_generate
from humanizer import humanize, humanize_batch

app = FastAPI(
    title="课后评价生成系统",
    description="从文本中识别学生信息，调用千问 AI 生成课后评价",
    version="0.1.0",
)

# CORS - 允许前端跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # PWA 前端
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# === Request/Response Models ===

class ExtractRequest(BaseModel):
    text: str
    api_key: Optional[str] = None


class ExtractResponse(BaseModel):
    grade: str
    class_name: str
    total_count: int
    students: List[dict]


class GenerateRequest(BaseModel):
    student: dict
    lesson_content: str
    grade: Optional[str] = None
    class_name: Optional[str] = None
    api_key: Optional[str] = None


class GenerateResponse(BaseModel):
    name: str
    evaluation: str
    humanized: str


class BatchGenerateRequest(BaseModel):
    students: List[dict]
    lesson_content: str
    grade: Optional[str] = None
    class_name: Optional[str] = None
    api_key: Optional[str] = None


class BatchGenerateResponse(BaseModel):
    evaluations: List[dict]


# === API Endpoints ===

@app.get("/health")
def health_check():
    """健康检查"""
    return {"status": "ok"}


@app.post("/api/extract", response_model=ExtractResponse)
def extract_students(req: ExtractRequest):
    """
    从原始文本中提取学生信息

    输入示例：
    "一年级三班 54 人 有张三、李四、王五...一共 54 个"
    """
    try:
        result = extract_all(req.text)

        return ExtractResponse(
            grade=result["grade"],
            class_name=result["class"],
            total_count=result["total_count"],
            students=result["students"],
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/api/generate", response_model=GenerateResponse)
def generate_single(req: GenerateRequest):
    """
    生成单个学生的课后评价

    返回包含原始 AI 生成结果和去 AI 痕迹后的人性化版本
    """
    # 调用千问 API 生成评价
    raw_evaluation = generate_evaluation(
        student=req.student,
        lesson_content=req.lesson_content,
        grade=req.grade or "",
        class_name=req.class_name or "",
        api_key=req.api_key,
    )

    # 去 AI 痕迹处理
    humanized = humanize(raw_evaluation)

    return GenerateResponse(
        name=req.student.get("name", ""),
        evaluation=raw_evaluation,
        humanized=humanized,
    )


@app.post("/api/batch-generate", response_model=BatchGenerateResponse)
def generate_batch(req: BatchGenerateRequest):
    """
    批量生成学生评价
    """
    results = batch_generate(
        students=req.students,
        lesson_content=req.lesson_content,
        grade=req.grade or "",
        class_name=req.class_name or "",
        api_key=req.api_key,
    )

    # 对每个评价进行人性化处理
    evaluations = []
    for item in results:
        humanized = humanize(item["evaluation"])
        evaluations.append({
            "name": item["name"],
            "evaluation": item["evaluation"],
            "humanized": humanized,
        })

    return BatchGenerateResponse(evaluations=evaluations)


# === Main ===

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
