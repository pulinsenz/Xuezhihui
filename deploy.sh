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
: "${USER_API_KEY:?请在 .env 中设置 USER_API_KEY（普通用户 LLM 密钥，compose 必填）}"
: "${USER_LLM_BASE_URL:?请在 .env 中设置 USER_LLM_BASE_URL（普通用户 LLM 网关地址，代码不再内置默认值）}"
: "${AGENT_TOKEN:?请在 .env 中设置 AGENT_TOKEN}"
: "${JWT_SECRET:?请在 .env 中设置 JWT_SECRET}"

# 2.5 证书预检：prod override 已挂载 /etc/letsencrypt 证书并开放 443，
#     证书文件不存在时 frontend 容器起不来（HTTPS 成为硬前置）
if [ ! -f "/etc/letsencrypt/live/xuezhihui.site/fullchain.pem" ]; then
  echo "!! 未找到证书 /etc/letsencrypt/live/xuezhihui.site/fullchain.pem。"
  echo "   首次部署（尚未签证书）请先注释 docker-compose.prod.yml 中 frontend 的证书挂载与 443 端口，"
  echo "   或先完成 bash scripts/enable-https.sh 签发证书后再跑本脚本。"
  exit 1
fi

# 3.5 安全建议（非阻塞）：生产强烈建议配置 Redis 鉴权与初始管理员密码
if [ -z "${REDIS_PASSWORD:-}" ]; then
  echo ">> 提示: 未设置 REDIS_PASSWORD，Redis 将无鉴权运行（生产建议：openssl rand -hex 16 生成并设置）"
fi
if [ -z "${ADMIN_PASSWORD:-}" ]; then
  echo ">> 提示: 未设置 ADMIN_PASSWORD，admin 密码将由 Java 启动时随机生成并打印到日志（生产建议显式设置）"
fi

# 3. 预检：version + config（config 会真实解析 ports: !reset 等语法，失败提前暴露）
docker compose version
$COMPOSE config --quiet

# 4. 构建并后台启动（--build：代码变更后重跑本脚本即重建）
$COMPOSE up -d --build

# 5. 等待所有带健康检查的服务收敛（最多 ~3 分钟；首启含 MySQL 初始化 / embedding 下载）
echo ">> 等待服务健康（首次启动可能较久，请耐心）..."
converged=0
for i in $(seq 1 36); do
  bad=$($COMPOSE ps --format '{{.Service}} {{.Health}}' | awk '$2=="unhealthy"' | wc -l)
  starting=$($COMPOSE ps --format '{{.Service}} {{.Health}}' | awk '$2=="starting"' | wc -l)
  if [ "$bad" -eq 0 ] && [ "$starting" -eq 0 ]; then
    converged=1
    break
  fi
  sleep 5
done

# 5.5 超时即失败退出（旧实现打完日志继续走完，给人已成功的错觉）
if [ "$converged" -ne 1 ]; then
  echo "!! 180s 内服务未全部健康，诊断："
  $COMPOSE ps
  $COMPOSE logs --tail=30 2>/dev/null | tail -50
  exit 1
fi

# 6. 结果展示
$COMPOSE ps
echo "-------------------------------------------"
echo ">> 前端入口： http://<服务器公网IP>/   （需安全组放行 80）"
if command -v curl >/dev/null 2>&1; then
  echo ">> 本地探测： $(curl -fsS -o /dev/null -w 'HTTP %{http_code}' http://127.0.0.1/ || echo '失败（nginx 未就绪）')"
fi
