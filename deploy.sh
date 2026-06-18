#!/bin/bash
set -e

echo "========== 一键部署脚本 =========="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
  echo "请先安装 Docker 和 Docker Compose"
  exit 1
fi

# 2. 检查 .env
if [ ! -f .env ]; then
  echo "请先创建 .env 文件（参考 .env.example）"
  exit 1
fi

# 3. 构建并启动
echo ">>> 构建并启动所有服务..."
docker compose up -d --build

# 4. 等待服务就绪
echo ">>> 等待服务启动..."
sleep 10

# 5. 检查状态
echo ">>> 服务状态："
docker compose ps

echo ""
echo "========== 部署完成 =========="
echo "前端: http://服务器IP"
echo "后端: http://服务器IP:8080"
echo ""
echo "常用命令："
echo "  docker compose logs -f    # 查看日志"
echo "  docker compose restart    # 重启服务"
echo "  docker compose down       # 停止服务"
