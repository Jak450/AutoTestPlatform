#!/bin/bash
set -e

echo "========== 服务器初始化 =========="

# 1. 安装 MySQL 和 Redis（Docker）
echo ">>> 启动 MySQL..."
docker rm -f autotest-mysql 2>/dev/null || true
docker run -d --name autotest-mysql \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=app_test \
  -p 3306:3306 \
  -v mysql-data:/var/lib/mysql \
  mysql:8.0

echo ">>> 启动 Redis..."
docker rm -f autotest-redis 2>/dev/null || true
docker run -d --name autotest-redis \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine

# 2. 创建部署目录
mkdir -p /opt/autotest

# 3. 安装 Nginx（前端）
echo ">>> 安装 Nginx..."
apt-get update -qq && apt-get install -y -qq nginx unzip

# 配置 Nginx
cat > /etc/nginx/sites-available/autotest << 'EOF'
server {
    listen 80;
    server_name _;

    root /opt/autotest/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
EOF

ln -sf /etc/nginx/sites-available/autotest /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl restart nginx

# 4. 等待 MySQL 就绪
echo ">>> 等待 MySQL 就绪..."
for i in $(seq 1 30); do
  if docker exec autotest-mysql mysql -uroot -p123456 -e "SELECT 1" &>/dev/null; then
    echo "MySQL 就绪"
    break
  fi
  sleep 2
done

# 5. 初始化数据库
echo ">>> 初始化数据库表..."
docker exec -i autotest-mysql mysql -uroot -p123456 app_test < /root/AutoTestPlatform/backed/init.sql

echo ""
echo "========== 初始化完成 =========="
echo "MySQL: 端口 3306"
echo "Redis: 端口 6379"
echo "Nginx: 端口 80"
echo ""
echo "GitHub Actions 推送后会自动部署后端 jar 到 /opt/autotest/"
