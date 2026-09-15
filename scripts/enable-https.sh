#!/usr/bin/env bash
# ============================================================
# 学智汇 启用 HTTPS（域名 xuezhihui.site）
# 前置：deploy.sh 已成功（frontend 在 80 提供 /.well-known/acme-challenge/ 应答）、
#       域名 A 记录已解析到本机公网 IP、国内服务器需已备案且 80 端口可被公网访问。
# 幂等：证书已签发 / 配置已放开则跳过。用法：bash scripts/enable-https.sh
# 流程：检查域名 → acme.sh 签发（webroot）→ 装证书 → 放开 nginx/compose HTTPS → 重建 frontend
# ============================================================
set -euo pipefail

DOMAIN="${DOMAIN:-xuezhihui.site}"
ACME_WEBROOT="${ACME_WEBROOT:-/var/www/acme-challenge}"
CERT_DIR="/etc/letsencrypt/live/$DOMAIN"

# 需要 root：acme.sh 装到 /root/.acme.sh、证书写 /etc/letsencrypt，都要 root
if [ "$(id -u)" -ne 0 ]; then
  echo ">> 需要 root，正在用 sudo 重新执行..."
  exec sudo bash "$0" "$@"
fi
# 定位到仓库根目录，保证下方相对路径与 $PWD（reloadcmd 用）正确
cd "$(dirname "$0")/.."

echo "==> [1/4] 检查域名解析"
if ! getent hosts "$DOMAIN" >/dev/null 2>&1; then
  echo "!! $DOMAIN 未解析到本机。请先在 DNS 处把 $DOMAIN 的 A 记录指向本机公网 IP，再运行本脚本。"
  exit 1
fi

echo "==> [2/4] 安装 acme.sh 并签发证书（webroot: $ACME_WEBROOT）"
mkdir -p "$ACME_WEBROOT"
if [ ! -f "$HOME/.acme.sh/acme.sh" ]; then
  curl -fsSL https://get.acme.sh | sh
fi
# SAN 证书同时覆盖 apex 与 www（www 的 ACME 应答由 80 段唯一 server 块按 Host 兜住）
"$HOME/.acme.sh/acme.sh" --issue -d "$DOMAIN" -d "www.$DOMAIN" -w "$ACME_WEBROOT" --webroot || {
  echo "!! 签发失败。排查："
  echo "   1) curl -I http://$DOMAIN/.well-known/acme-challenge/probe 应返回 404 而非拒绝连接"
  echo "   2) 80 端口是否被安全组/云防火墙放行"
  echo "   3) 备案是否完成（国内服务器未备案 80 会被云厂商拦截）"
  exit 1
}

echo "==> [3/4] 安装证书到 $CERT_DIR"
mkdir -p "$CERT_DIR"
"$HOME/.acme.sh/acme.sh" --install-cert -d "$DOMAIN" \
  --key-file "$CERT_DIR/privkey.pem" \
  --fullchain-file "$CERT_DIR/fullchain.pem" \
  --reloadcmd "cd $PWD && docker compose -f docker-compose.yml -f docker-compose.prod.yml exec frontend nginx -s reload"

echo "==> [4/4] 放开 HTTPS 配置并重建 frontend"
# 取消 nginx.conf 末尾「HTTPS 443 server」注释块（该块在文件末尾）
sed -i '/^# server {$/,$ s/^# //' frontend/nginx.conf
# 取消 docker-compose.prod.yml frontend 的证书挂载与 443 端口注释（均幂等）
sed -i \
  -e "/^      # - \\/etc\\/letsencrypt\\/live\\/${DOMAIN}\\/fullchain\\.pem:.*$/s/^      # /      /" \
  -e "/^      # - \\/etc\\/letsencrypt\\/live\\/${DOMAIN}\\/privkey\\.pem:.*$/s/^      # /      /" \
  -e '/^    # ports:$/s/^    # /    /' \
  -e '/^    #   - "443:443"$/s/^    # /    /' \
  docker-compose.prod.yml

echo "==> 重建 frontend（加载 443 + 证书）"
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build frontend

echo "============================================"
echo "HTTPS 已启用：https://${DOMAIN}"
echo "  80 仍可访问；需要强制跳转 HTTPS，取消 frontend/nginx.conf 中 80 段的 301 注释即可。"
echo "============================================"
