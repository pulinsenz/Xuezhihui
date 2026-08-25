# 学智汇 Python Agent 服务镜像
# 基础镜像 / pip 源均可通过构建参数替换（国内网络下 PyPI 直连 metadata 拉取不稳定，
# 会让 pip 误报 "No matching distribution"，故默认走清华镜像，可按需覆盖为官方源）
ARG PYTHON_IMAGE=python:3.12-slim
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
FROM ${PYTHON_IMAGE}

# FROM 前的 ARG 仅对 FROM 生效，需在 build stage 内重新声明才能在 RUN 中使用
ARG PIP_INDEX_URL

WORKDIR /app

# 依赖分层缓存（依赖均提供 amd64 预编译 wheel，无需 gcc/g++；
# 移除 apt 以规避 deb.debian.org 在部分网络下 apt update/install 拉取失败）
COPY requirements.txt .
RUN pip install --no-cache-dir -i ${PIP_INDEX_URL} --timeout 60 --retries 5 -r requirements.txt

# 应用代码
COPY . .

# 非 root 运行（安全基线）
# 预建 HF 缓存挂载点并归属 agentuser：named volume 挂到镜像中不存在的路径时卷根会以 root 创建，
# 导致 agentuser 无写权限（embedding 模型下载 Permission denied）
RUN useradd -m -u 1001 agentuser \
    && mkdir -p /home/agentuser/.cache/huggingface \
    && chown -R agentuser:agentuser /app /home/agentuser
USER agentuser

EXPOSE 8000

# 生产通过环境变量注入 DEEPSEEK_API_KEY / AGENT_TOKEN 等
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
