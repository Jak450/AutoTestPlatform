# ELK 监控部署说明

## 架构
```
服务器 A (39.103.63.178)：Java 应用 + Filebeat (Docker)
服务器 B (123.57.138.249)：Elasticsearch + Kibana（待部署）
```

## 文件说明

| 文件 | 用途 | 部署位置 |
|------|------|----------|
| `docker-compose.yml` | Filebeat 容器编排 | 服务器 A |
| `filebeat.yml` | Filebeat 配置 | 服务器 A，被 docker-compose 挂载 |

## 服务器 B 部署（监控端）

### 1. 阿里云安全组先开端口

进阿里云控制台 → 服务器 B → 安全组规则，添加入方向：

| 端口 | 授权对象 | 说明 |
|------|---------|------|
| 9200 | 39.103.63.178/32 | ES，仅允许 A 推日志 |
| 5601 | 你电脑公网 IP/32 | Kibana，浏览器访问 |

### 2. 系统调优（B 上执行）

```bash
sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
```

### 3. 装 Docker（如果还没装）

```bash
curl -fsSL https://get.docker.com | bash -s docker
systemctl start docker
systemctl enable docker
```

### 4. 准备 ES + Kibana 的 docker-compose

```bash
mkdir -p /opt/elk
cd /opt/elk
cat > docker-compose.yml << 'EOF'
services:
  elasticsearch:
    image: elasticsearch:8.11.0
    container_name: es
    restart: always
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
      - bootstrap.memory_lock=true
    ulimits:
      memlock:
        soft: -1
        hard: -1
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 10

  kibana:
    image: kibana:8.11.0
    container_name: kibana
    restart: always
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - I18N_LOCALE=zh-CN
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy

volumes:
  es-data:
EOF
```

### 5. 启动

```bash
docker compose up -d
```

### 6. 等待并验证（约 2 分钟）

```bash
# 等 ES 就绪
docker compose ps

# 测试 ES
curl http://localhost:9200

# 应该返回类似：
# {
#   "name": "...",
#   "version": { "number": "8.11.0" },
#   ...
# }
```

### 7. 浏览器访问 Kibana

打开 `http://123.57.138.249:5601`，看到 Kibana 首页就成功。

## 服务器 A 部署（应用端）

### 1. 创建目录
```bash
mkdir -p /opt/filebeat
```

### 2. 上传配置（本地执行）
```bash
scp backed/AI_Study_Notes/elk/docker-compose.yml root@39.103.63.178:/opt/filebeat/
scp backed/AI_Study_Notes/elk/filebeat.yml root@39.103.63.178:/opt/filebeat/
```

### 3. 启动（服务器 A 上执行）
```bash
cd /opt/filebeat
docker compose up -d
```

### 4. 验证
```bash
docker logs filebeat
# 看到 "Connection to ... established" 就成功
```

## Kibana 配置（B 部署完后）

1. 浏览器打开 `http://123.57.138.249:5601`
2. Stack Management → Index Patterns → 新建 `autotest-*`，时间字段 `@timestamp`
3. Discover → 选刚建的 pattern → 看日志

## 常用查询

| 需求 | 查询 |
|------|------|
| 只看 ERROR | `log.level: ERROR` |
| 只看 SQL | `message: *Preparing*` |
| 只看异常堆栈 | `message: *Exception*` |
| 时间范围 | 右上角时间选择器 |

## 维护

### 重启 Filebeat
```bash
cd /opt/filebeat
docker compose restart
```

### 查看 Filebeat 日志
```bash
docker logs -f filebeat
```

### 删除老索引（B 上执行）
```bash
curl -X DELETE "localhost:9200/autotest-$(date -d '30 days ago' +%Y.%m.%d)"
```
