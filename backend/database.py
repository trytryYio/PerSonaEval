"""
数据库模型 - MySQL (root/123123)
表：class, student, student_traits, temp_traits, evaluation
"""

from datetime import datetime
from sqlalchemy import (
    create_engine, Column, Integer, String, Text, Float, Date, DateTime, ForeignKey
)
from sqlalchemy.orm import sessionmaker, relationship, declarative_base

DATABASE_URL = "mysql+pymysql://root:123123@localhost:3306/persoona_eval"

engine = create_engine(DATABASE_URL, echo=False)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


class Class(Base):
    __tablename__ = "class"

    id = Column(Integer, primary_key=True, autoincrement=True)
    grade = Column(String(20), nullable=False)
    name = Column(String(20), nullable=False)
    student_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.now)

    students = relationship("Student", back_populates="class_obj")


class Student(Base):
    __tablename__ = "student"

    id = Column(Integer, primary_key=True, autoincrement=True)
    class_id = Column(Integer, ForeignKey("class.id"), nullable=False)
    name = Column(String(20), nullable=False)
    traits = Column(Text, default="")  # 原始特点文本
    created_at = Column(DateTime, default=datetime.now)

    class_obj = relationship("Class", back_populates="students")
    traits_list = relationship("StudentTraits", back_populates="student", cascade="all, delete-orphan")
    temp_traits_list = relationship("TempTraits", back_populates="student", cascade="all, delete-orphan")
    evaluations = relationship("Evaluation", back_populates="student", cascade="all, delete-orphan")


class StudentTraits(Base):
    """长期性格画像 - 累积迭代"""
    __tablename__ = "student_traits"

    id = Column(Integer, primary_key=True, autoincrement=True)
    student_id = Column(Integer, ForeignKey("student.id"), nullable=False)
    trait = Column(String(20), nullable=False)
    percentage = Column(Float, nullable=False)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)

    student = relationship("Student", back_populates="traits_list")


class TempTraits(Base):
    """当堂临时性格 - 每次覆盖"""
    __tablename__ = "temp_traits"

    id = Column(Integer, primary_key=True, autoincrement=True)
    student_id = Column(Integer, ForeignKey("student.id"), nullable=False)
    trait = Column(String(20), nullable=False)
    percentage = Column(Float, nullable=False)
    created_at = Column(DateTime, default=datetime.now)

    student = relationship("Student", back_populates="temp_traits_list")


class Evaluation(Base):
    __tablename__ = "evaluation"

    id = Column(Integer, primary_key=True, autoincrement=True)
    student_id = Column(Integer, ForeignKey("student.id"), nullable=False)
    lesson_content = Column(String(200), nullable=False)
    lesson_date = Column(Date, default=datetime.now().date)
    performance_notes = Column(Text, default="")
    template = Column(String(10), nullable=False)  # "80/20", "65/35", "90/10"
    content = Column(Text, nullable=False)
    created_at = Column(DateTime, default=datetime.now)

    student = relationship("Student", back_populates="evaluations")


def init_db():
    """创建所有表"""
    Base.metadata.create_all(bind=engine)


def get_db():
    """获取数据库会话"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def iterate_traits(student_id: int, temp_traits: dict, alpha: float = 0.7) -> dict:
    """
    加权移动平均迭代性格画像
    新值 = 历史值 × α + 当堂值 × (1 - α)
    """
    db = SessionLocal()
    try:
        # 获取现有 traits
        existing = db.query(StudentTraits).filter_by(student_id=student_id).all()
        old_traits = {t.trait: t.percentage for t in existing}

        # 合并所有 trait 键
        all_keys = set(old_traits.keys()) | set(temp_traits.keys())

        # 计算新值
        new_traits = {}
        for trait in all_keys:
            old_val = old_traits.get(trait, 0)
            temp_val = temp_traits.get(trait, 0)
            new_traits[trait] = old_val * alpha + temp_val * (1 - alpha)

        # 归一化到 100%
        total = sum(new_traits.values())
        if total > 0:
            for trait in new_traits:
                new_traits[trait] = round(new_traits[trait] / total * 100, 1)

        # 更新数据库
        db.query(StudentTraits).filter_by(student_id=student_id).delete()
        for trait, pct in new_traits.items():
            db.add(StudentTraits(
                student_id=student_id,
                trait=trait,
                percentage=pct,
                updated_at=datetime.now(),
            ))
        db.commit()

        return new_traits
    finally:
        db.close()


def save_temp_traits(student_id: int, traits: dict):
    """保存当堂临时性格（覆盖旧数据）"""
    db = SessionLocal()
    try:
        db.query(TempTraits).filter_by(student_id=student_id).delete()
        for trait, pct in traits.items():
            db.add(TempTraits(
                student_id=student_id,
                trait=trait,
                percentage=pct,
                created_at=datetime.now(),
            ))
        db.commit()
    finally:
        db.close()


def get_traits(student_id: int) -> list[dict]:
    """获取学生长期性格画像"""
    db = SessionLocal()
    try:
        traits = db.query(StudentTraits).filter_by(student_id=student_id).all()
        return [{"trait": t.trait, "percentage": t.percentage} for t in traits]
    finally:
        db.close()


def get_temp_traits(student_id: int) -> list[dict]:
    """获取当堂临时性格"""
    db = SessionLocal()
    try:
        traits = db.query(TempTraits).filter_by(student_id=student_id).all()
        return [{"trait": t.trait, "percentage": t.percentage} for t in traits]
    finally:
        db.close()


if __name__ == "__main__":
    init_db()
    print("Database initialized successfully!")
