"""
学智汇 Python Agent 服务入口：FastAPI + LangGraph 多 Agent 协同 RAG
生产化：内部鉴权（X-Agent-Token）、全局异常统一响应、启动配置校验。
"""
import hmac
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from agent import runtime
from api import chat_api, knowledge_api
from config import settings, validate_settings
from storage.task_store import TaskStore
from utils.logger_util import get_logger
from worker.vectorize_worker import VectorizeWorker

logger = get_logger("main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 生产启动校验：缺 key / 缺 token 直接终止，杜绝静默降级上线
    validate_settings()
    runtime.init_runtime()
    # 长任务消费者：Redis List 消息队列，Java 提交、本服务消费执行向量化
    worker = VectorizeWorker(TaskStore())
    worker.start()
    yield
    worker.stop()


app = FastAPI(
    title="学智汇 Python Agent",
    version="1.0.0",
    lifespan=lifespan,
    docs_url=None,       # 生产关闭 Swagger 文档
    redoc_url=None,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def agent_auth_middleware(request: Request, call_next):
    """内部调用鉴权：除健康检查外，所有请求必须携带 X-Agent-Token"""
    if request.method == "OPTIONS":
        return await call_next(request)
    if request.url.path != "/health":
        token = request.headers.get("X-Agent-Token", "")
        if not settings.agent_token or not hmac.compare_digest(token, settings.agent_token):
            return JSONResponse(
                status_code=401,
                content={"code": 40100, "message": "未授权", "data": None},
            )
    return await call_next(request)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    """业务异常统一响应（与 Java Result 对齐：code/message/data）"""
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.status_code, "message": str(exc.detail), "data": None},
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    """兜底异常：记录完整堆栈，对外返回统一 500 结构"""
    logger.error("未处理异常: %s %s", request.method, request.url.path, exc_info=exc)
    return JSONResponse(
        status_code=500,
        content={"code": 50000, "message": "系统内部异常", "data": None},
    )


app.include_router(chat_api.router)
app.include_router(knowledge_api.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": "xuezhihui-python-agent"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
