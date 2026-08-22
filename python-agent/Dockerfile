# 学智汇 Python Agent 服务镜像
# 基础镜像可通过构建参数替换（国内拉取失败时配合 docker-compose.pull-mirror.yml 用 DaoCloud 代理）
ARG PYTHON_IMAGE=python:3.11-slim
FROM ${PYTHON_IMAGE}

WORKDIR /app

# 系统依赖（sentence-transformers 等可能用到编译，保持精简）
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc g++ \
    && rm -rf /var/lib/apt/lists/*

# 依赖分层缓存
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 应用代码
COPY . .

# 非 root 运行（安全基线）
RUN useradd -m -u 1001 agentuser && chown -R agentuser:agentuser /app
USER agentuser

EXPOSE 8000

# 生产通过环境变量注入 DEEPSEEK_API_KEY / AGENT_TOKEN 等
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
