#!/usr/bin/env bash
# ============================================================
# 学智汇 一键上线脚本（云服务器单机 Docker Compose）
# 前置：Docker Engine 24+ / Docker Compose v2（docker compose 子命令）
# 用法：bash deploy.sh
# 流程：.env 准备 → 密钥校验 → compose 预检 → 构建启动 → 健康收敛 → 验证
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

# 1. 准备 .env
if [ ! -f .env ]; then
  echo ">> 未发现 .env，已从 .env.example 复制。请编辑填入密钥后重新执行本脚本"
  cp .env.example .env
  exit 1
fi

# 2. 导出并校验必填密钥
set -a; source .env; set +a
: "${DEEPSEEK_API_KEY:?请在 .env 中设置 DEEPSEEK_API_KEY}"
: "${AGENT_TOKEN:?请在 .env 中设置 AGENT_TOKEN}"
: "${JWT_SECRET:?请在 .env 中设置 JWT_SECRET}"
: "${TURNSTILE_SECRET_KEY:?请在 .env 中设置 TURNSTILE_SECRET_KEY（Cloudflare Turnstile 人机验证，防批量注册薅 LLM）}"

# 3. 预检：version + config（config 会真实解析 ports: !reset 等语法，失败提前暴露）
docker compose version
$COMPOSE config --quiet

# 4. 构建并后台启动（--build：代码变更后重跑本脚本即重建）
$COMPOSE up -d --build

# 5. 等待所有带健康检查的服务收敛（最多 ~3 分钟；首启含 MySQL 初始化 / embedding 下载）
echo ">> 等待服务健康（首次启动可能较久，请耐心）..."
for i in $(seq 1 36); do
  bad=$($COMPOSE ps --format '{{.Service}} {{.Health}}' | awk '$2=="unhealthy"' | wc -l)
  starting=$($COMPOSE ps --format '{{.Service}} {{.Health}}' | awk '$2=="starting"' | wc -l)
  if [ "$bad" -eq 0 ] && [ "$starting" -eq 0 ]; then
    break
  fi
  sleep 5
done

# 6. 结果展示
$COMPOSE ps
echo "-------------------------------------------"
echo ">> 前端入口： http://<服务器公网IP>/   （需安全组放行 80）"
if command -v curl >/dev/null 2>&1; then
  echo ">> 本地探测： $(curl -fsS -o /dev/null -w 'HTTP %{http_code}' http://127.0.0.1/ || echo '失败（nginx 未就绪）')"
fi
