"""
学智汇 Python Agent 服务入口：FastAPI + LangGraph 多 Agent 协同 RAG
"""
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agent import runtime
from api import chat_api, knowledge_api


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时初始化运行时组件（LLM、向量库、检索器、Redis）
    runtime.init_runtime()
    yield


app = FastAPI(title="学智汇 Python Agent", version="1.0.0", lifespan=lifespan)

# Java 服务端调用无需 CORS，但开放便于本地联调/调试
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_api.router)
app.include_router(knowledge_api.router)


@app.get("/health")
def health():
    return {"status": "ok", "service": "xuezhihui-python-agent"}


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
