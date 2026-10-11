FROM eclipse-temurin:17-jre-alpine@sha256:a2e1cd0a400a97cf772159f5fff4e05088b4d20097379624cab1874a381ff6f1

LABEL maintainer="jeecgos@163.com"

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# 创建工作目录及日志目录（logback 写入 logs/xxl-job-admin.log，需保证 appuser 可写）
RUN mkdir -p /data /data/logs

# 安装基本工具及 su-exec（用于以非 root 身份启动 Java）
RUN apk add --no-cache curl jq su-exec

# 设置工作目录
WORKDIR /data

# 暴露端口（与 application.yml 中 server.port: 9000 一致）
EXPOSE 9000

# 添加 JAR 文件
ADD ./target/jeecg-cloud-xxljob-3.7.0.jar ./

# 使用 entrypoint 脚本：以 root 运行以便在卷挂载后修复 logs 权限，再通过 su-exec 切换 appuser 启动 Java
RUN echo '#!/bin/sh' > /data/entrypoint.sh && \
    echo 'set -e' >> /data/entrypoint.sh && \
    echo '# 确保 logs 目录存在且 appuser 可写（应对卷挂载覆盖 /data 导致权限丢失）' >> /data/entrypoint.sh && \
    echo 'if [ "$(id -u)" = "0" ]; then mkdir -p /data/logs && chown -R appuser:appuser /data; fi' >> /data/entrypoint.sh && \
    echo 'echo "Starting XXL-JOB Admin..."' >> /data/entrypoint.sh && \
    echo '# 已是 appuser(1000) 时直接 exec java，避免 su-exec 触发 setgroups 报错；root 时用 su-exec 降权' >> /data/entrypoint.sh && \
    echo 'if [ "$(id -u)" = "1000" ]; then' >> /data/entrypoint.sh && \
    echo '  exec java $JAVA_OPTS -Dfile.encoding=utf-8 -Djava.security.egd=file:/dev/./urandom -Dlog.path=/data/logs/xxl-job-admin.log -jar jeecg-cloud-xxljob-3.7.0.jar "$@"' >> /data/entrypoint.sh && \
    echo 'else' >> /data/entrypoint.sh && \
    echo '  exec su-exec appuser java $JAVA_OPTS -Dfile.encoding=utf-8 -Djava.security.egd=file:/dev/./urandom -Dlog.path=/data/logs/xxl-job-admin.log -jar jeecg-cloud-xxljob-3.7.0.jar "$@"' >> /data/entrypoint.sh && \
    echo 'fi' >> /data/entrypoint.sh

RUN chmod +x /data/entrypoint.sh

# 创建非 root 用户并设置 /data 及 logs 归属（安全合规：不以 root 运行应用）
RUN addgroup -g 1000 appuser && \
    adduser -D -G appuser -u 1000 appuser && \
    chown -R appuser:appuser /data

# 合规：容器内不以 root 启动，entrypoint 以 appuser 运行；已是 appuser 时直接 exec java 避免 su-exec setgroups 报错
USER appuser
ENTRYPOINT ["/data/entrypoint.sh"]
