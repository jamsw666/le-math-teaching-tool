# 多阶段构建：构建阶段用完整 JDK + Maven，运行阶段只带 JRE，镜像从 ~700MB 降到 ~250MB
# 构建：docker build -t java-learning:1.0.0 .
# 运行：docker run --rm java-learning:1.0.0 com.javalearning.basics.OopDemo

# ---------- 阶段 1：构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷 pom 并下载依赖：只要 pom 不变，这一层缓存可复用，源码改动不会触发重新下载依赖
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests \
    && mvn -q -B dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime

# ---------- 阶段 2：运行 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 不用 root 运行
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/java-learning-1.0.0.jar app.jar
COPY --from=build /build/target/lib ./lib

# 容器感知：JVM 17+ 默认按 cgroup 限制计算堆（MaxRAMPercentage），显式写出便于理解
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# 示例工程没有 HTTP 端口；Web 服务在此声明 EXPOSE 8080 并配置 HEALTHCHECK
# HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp app.jar:lib/* com.javalearning.Main \"$@\"", "--"]
CMD ["--list"]
