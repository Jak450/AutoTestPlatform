#!/bin/bash
# AutoTestPlatform Agent 后端启动脚本（生产）
set -e

APP_DIR=/opt/autotest
JAR=$APP_DIR/app.jar

if [ ! -f "$JAR" ]; then
  echo "未找到 $JAR，请先构建部署"
  exit 1
fi

# 环境变量（生产必须显式提供，勿在此提交真实密钥）
: "${DEEPSEEK_API_KEY:?请设置 DEEPSEEK_API_KEY}"
: "${JWT_SECRET:?请设置 JWT_SECRET（>=32字节）}"

cd "$APP_DIR"
nohup java -jar app.jar \
  --spring.profiles.active=prod \
  --DB_HOST=${DB_HOST:-localhost} \
  --DB_PORT=${DB_PORT:-3306} \
  --DB_NAME=${DB_NAME:-app_test} \
  --DB_USERNAME=${DB_USERNAME:-root} \
  --DB_PASSWORD=${DB_PASSWORD:-123456} \
  --REDIS_HOST=${REDIS_HOST:-localhost} \
  --REDIS_PORT=${REDIS_PORT:-6379} \
  > "$APP_DIR/app.log" 2>&1 &
echo $! > "$APP_DIR/app.pid"
echo "Agent 后端已启动，PID=$(cat "$APP_DIR/app.pid")，日志：$APP_DIR/app.log"
