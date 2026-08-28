#!/usr/bin/env bash
# ============================================================
# 学智汇 服务器一键初始化（腾讯云轻量 Ubuntu 22.04 / Debian 12，2C4G 建议 +4G swap）
# 幂等：swap / Docker / 代码 已就绪则跳过，可反复执行。
# 用法：bash scripts/init-server.sh
# 流程：swap → Docker → 拉代码 → 准备 .env → 部署（deploy.sh）
# ============================================================
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/pulinsenz/Xuezhihui.git}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/xuezhihui}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"

# 需要 root：非 root 时用 sudo 重新执行（apt/docker/swap 都需要提权）
# 显式把 DEPLOY_DIR 传给 root 上下文，避免 sudo 重置 HOME 导致部署目录漂移到 /root/xuezhihui
if [ "$(id -u)" -ne 0 ]; then
  echo ">> 需要 root，正在用 sudo 重新执行（DEPLOY_DIR=$DEPLOY_DIR 已按你的用户目录确定，不会变）..."
  exec sudo DEPLOY_DIR="$DEPLOY_DIR" bash "$0" "$@"
fi

echo "==> [1/5] 系统基础依赖"
apt-get update -y
apt-get install -y curl git ca-certificates

echo "==> [2/5] 创建 ${SWAP_SIZE_GB}G swap（幂等，2C4G 跑 Milvus 全家桶必配）"
if [ ! -f /swapfile ]; then
  fallocate -l "${SWAP_SIZE_GB}G" /swapfile 2>/dev/null \
    || dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_SIZE_GB * 1024))
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon /swapfile 2>/dev/null || true
grep -q '^/swapfile ' /etc/fstab 2>/dev/null || echo '/swapfile none swap sw 0 0' >> /etc/fstab

echo "==> [3/5] 安装 Docker + Compose 插件"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker
docker compose version >/dev/null 2>&1 || apt-get install -y docker-compose-plugin

echo "==> [4/5] 拉取代码到 ${DEPLOY_DIR}"
if [ ! -d "$DEPLOY_DIR/.git" ]; then
  git clone "$REPO_URL" "$DEPLOY_DIR"
else
  git -C "$DEPLOY_DIR" pull --ff-only || echo "!! 代码拉取失败（可能本地有改动），已跳过，继续部署"
fi
cd "$DEPLOY_DIR"

echo "==> [5/5] 准备 .env"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "!! 已生成 ${DEPLOY_DIR}/.env（模板）。请编辑填入密钥后重新运行本脚本："
  echo "   DEEPSEEK_API_KEY / USER_API_KEY / AGENT_TOKEN / JWT_SECRET"
  echo "   （注册防刷为自研算术验证码，自托管，无需额外密钥）"
  echo "   生产强烈建议一并设置 REDIS_PASSWORD / ADMIN_PASSWORD（见 .env.example 注释）"
  exit 1
fi

echo "==> 部署（deploy.sh 会校验密钥并拉起全部容器）"
bash deploy.sh

echo "============================================"
echo "部署完成。启用 HTTPS（需域名已解析到本机公网 IP）："
echo "  bash ${DEPLOY_DIR}/scripts/enable-https.sh"
echo "============================================"
