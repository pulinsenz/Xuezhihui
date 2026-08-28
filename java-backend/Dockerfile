# 学智汇 Java 业务服务镜像（多阶段构建）
# 基础镜像可通过构建参数替换（国内拉取失败时配合 docker-compose.pull-mirror.yml 用 DaoCloud 代理）
# 注意：多阶段 FROM 可用的 ARG 必须在文件顶部声明
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre

# 阶段一：Maven 编译
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline -q
COPY src ./src
RUN mvn -B -DskipTests package -q

# 阶段二：运行时
FROM ${JRE_IMAGE}
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# 非 root 运行（安全基线）
RUN apt-get update \
    # Java2D 生成算术验证码图片需要字体（temurin JRE 精简镜像无字体，缺失则 drawString 画不出文字）
    && apt-get install -y --no-install-recommends fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 1001 appuser \
    && mkdir -p /app/data/files \
    # 上传文件目录归 appuser，保证共享卷可写（Docker 首次挂载空卷会继承该所有权）
    && chown -R appuser:appuser /app /app/data
USER appuser
EXPOSE 8123
# JAVA_OPTS 注入 JVM 参数（生产经 docker-compose 传 -Xms256m -Xmx512m，防止 JVM 按宿主机 25% 起堆吃满内存）
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar app.jar"]
