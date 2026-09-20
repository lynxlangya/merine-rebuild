# syntax=docker/dockerfile:1
# 交付形态的 API 镜像：构建阶段用 Maven，运行阶段只留 JRE 与分层后的 jar。
#
# 与开发镜像（infra/docker/api.Dockerfile：maven + spring-boot:run + 挂载源码）的区别：
#   * 镜像里没有 Maven、没有源码，只有编译产物；
#   * 用 Spring Boot 的 layers.idx 分层 COPY，依赖层不变时重建只推 application 层（约 1 MB）；
#   * 非 root 用户运行，JVM 内存按容器限额取 75%，OOM 直接退出交给编排重启。

# --- 构建阶段 ---
FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /src/apps/api
# 先只放构建描述文件，让依赖下载独立成层：改业务代码不会重新拉依赖
COPY apps/api/.mvn/ .mvn/
COPY apps/api/mvnw apps/api/pom.xml ./
# Maven 仓库走 BuildKit 缓存：重建镜像不会重新下载依赖（缓存不在镜像层里，不进最终体积）
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests dependency:go-offline
COPY apps/api/src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    # 必须带 --launcher：否则 spring-boot-loader 层是空的，运行阶段找不到 JarLauncher
    && java -Djarmode=tools -jar "$(ls target/*.jar | head -1)" extract --layers --launcher --destination /src/layers

# --- 运行阶段 ---
# 纯 Java 栈（Tomcat / MyBatis / MySQL Connector 都没有本地库依赖），因此用 Alpine 版 JRE：
# 比 glibc 版小约 130 MB；如果以后引入本地库依赖，这里要换回 eclipse-temurin:21-jre。
FROM eclipse-temurin:21-jre-alpine@sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52 AS runtime
RUN addgroup -S merine && adduser -S -G merine -h /app merine
WORKDIR /app
COPY --from=build --chown=merine:merine /src/layers/dependencies/ ./
COPY --from=build --chown=merine:merine /src/layers/spring-boot-loader/ ./
COPY --from=build --chown=merine:merine /src/layers/snapshot-dependencies/ ./
COPY --from=build --chown=merine:merine /src/layers/application/ ./
USER merine
EXPOSE 9002
ENV TZ=UTC
# 分层解压后的入口：类路径就是当前目录（spring-boot-loader 与 BOOT-INF 都平铺在这里）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
            "org.springframework.boot.loader.launch.JarLauncher"]
