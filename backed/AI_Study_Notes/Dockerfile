# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY lib/ lib/
COPY src/ src/

# 安装本地 selenium jar 到本地 Maven 仓库
RUN mvn install:install-file \
  -Dfile=lib/selenium-server-4.18.1.jar \
  -DgroupId=lib \
  -DartifactId=selenium-server-4.18.1 \
  -Dversion=1.0 \
  -Dpackaging=jar \
  -DlocalRepositoryPath=/root/.m2/repository

RUN mvn clean package -DskipTests -q

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app

# 安装 Chrome + Chromedriver（Selenium UI 测试需要）
RUN apt-get update && apt-get install -y \
  google-chrome-stable \
  chromium-chromedriver \
  && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
