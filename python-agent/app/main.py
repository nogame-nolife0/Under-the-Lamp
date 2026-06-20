from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.health_router import router as health_router
from app.api.parse_router import router as parse_router
from app.api.rag_router import router as rag_router
from app.config import get_settings

settings = get_settings()

app = FastAPI(
    title="Paper Generator Agent",
    description="试卷出题系统 Python Agent 服务",
    version=settings.agent_version,
    serialize_by_alias=True,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(parse_router)
app.include_router(rag_router)
