FROM eclipse-temurin:17-jdk-alpine@sha256:b455964e5e139d42554cc33981435aec8b6dfa4c1c528bdc5c27973e8b28ca05

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# 创建工作目录及日志目录（logback 写入 logs/xxx.log，需保证 appuser 可写）
RUN mkdir -p /data /data/logs

# 安装基本工具及 su-exec（用于以非 root 身份启动 Java）
RUN apk add --no-cache curl jq su-exec

# 安装字体支持库（freetype、fontconfig）和默认字体包
RUN apk add --no-cache freetype fontconfig ttf-dejavu

# 安装中文字体 font-wqy-zenhei（从Alpine edge/community仓库添加，绕过签名检查）
RUN apk add --no-cache --allow-untrusted font-wqy-zenhei --repository https://dl-cdn.alpinelinux.org/alpine/edge/community \
    && fc-cache -f -v
# 如果需要安装SimSun字体（假设simsun.ttc文件在宿主机项目目录的./fonts/下）  
# 先在Dockerfile同级目录创建fonts文件夹，并放入simsun.ttc（从Windows或合法来源获取）
# COPY ./fonts/simsun.ttc /usr/share/fonts/truetype/
# RUN mkfontscale /usr/share/fonts/truetype/ \
#     && mkfontdir /usr/share/fonts/truetype/ \
#     && fc-cache -f -v
# 注意：安装mkfontscale和mkfontdir需额外RUN apk add --no-cache mkfontscale mkfontdir

# 设置工作目录
WORKDIR /data

# 暴露端口
EXPOSE 9999

# 添加JAR文件
ADD ./target/jeecg-cloud-gateway-3.7.0.jar ./


# 关键步骤：使用 echo 命令将脚本内容逐行写入 /data/entrypoint.sh
# 注：entrypoint 需以 root 运行以便在卷挂载后修复 logs 目录权限，再 su 到 appuser 启动 Java
RUN echo '#!/bin/sh' > /data/entrypoint.sh && \
    echo 'set -e' >> /data/entrypoint.sh && \
    echo '# 确保 logs 目录存在且 appuser 可写（应对卷挂载覆盖 /data 导致权限丢失）' >> /data/entrypoint.sh && \
    echo 'if [ "$(id -u)" = "0" ]; then mkdir -p /data/logs /data/logs/nacos /data/logs/csp /home/appuser/logs/nacos /home/appuser/logs/csp && chown -R appuser:appuser /data /home/appuser/logs; fi' >> /data/entrypoint.sh && \
    echo 'echo "Container started, initializing network configuration..."' >> /data/entrypoint.sh && \
    echo '# 尝试获取宿主机IP（适用于host网络模式）' >> /data/entrypoint.sh && \
    echo 'HOST_IP=$(ip route get 8.8.8.8 2>/dev/null | awk '\''NR==1 {print $7}'\'')' >> /data/entrypoint.sh && \
    echo '# 如果上述方法失败，使用hostname -i作为备选' >> /data/entrypoint.sh && \
    echo 'if [ -z "$HOST_IP" ] || [ "$HOST_IP" = "127.0.0.1" ]; then' >> /data/entrypoint.sh && \
    echo '    HOST_IP=$(hostname -i 2>/dev/null | awk '\''{print $1}'\'')' >> /data/entrypoint.sh && \
    echo 'fi' >> /data/entrypoint.sh && \
    echo '# 仅 root 可写 /etc/hosts；非 root 时跳过' >> /data/entrypoint.sh && \
    echo 'if [ "$(id -u)" = "0" ]; then' >> /data/entrypoint.sh && \
    echo '  if [ -n "$HOST_IP" ] && [ "$HOST_IP" != "127.0.0.1" ]; then' >> /data/entrypoint.sh && \
    echo '    CURRENT_HOSTNAME=$(hostname)' >> /data/entrypoint.sh && \
    echo '    echo "127.0.0.1    localhost" > /etc/hosts' >> /data/entrypoint.sh && \
    echo '    echo "::1          localhost ip6-localhost ip6-loopback" >> /etc/hosts' >> /data/entrypoint.sh && \
    echo '    echo "$HOST_IP    $CURRENT_HOSTNAME" >> /etc/hosts' >> /data/entrypoint.sh && \
    echo '    echo "Updated /etc/hosts with host IP: $HOST_IP for hostname: $CURRENT_HOSTNAME"' >> /data/entrypoint.sh && \
    echo '  fi' >> /data/entrypoint.sh && \
    echo 'fi' >> /data/entrypoint.sh && \
    echo 'echo "Starting Java application..."' >> /data/entrypoint.sh && \
    echo '# 已是 appuser(1000) 时直接 exec java，避免 su-exec 触发 setgroups 报错；root 时用 su-exec 降权' >> /data/entrypoint.sh && \
    echo 'if [ "$(id -u)" = "1000" ]; then' >> /data/entrypoint.sh && \
    echo '  exec java $JAVA_OPTS -Dfile.encoding=utf-8 -Djava.security.egd=file:/dev/./urandom -jar jeecg-cloud-gateway-3.7.0.jar "$@"' >> /data/entrypoint.sh && \
    echo 'else' >> /data/entrypoint.sh && \
    echo '  exec su-exec appuser java $JAVA_OPTS -Dfile.encoding=utf-8 -Djava.security.egd=file:/dev/./urandom -jar jeecg-cloud-gateway-3.7.0.jar "$@"' >> /data/entrypoint.sh && \
    echo 'fi' >> /data/entrypoint.sh

# 确保脚本有执行权限[1,3,4](@ref)
RUN chmod +x /data/entrypoint.sh

# 创建非 root 用户并设置 /data 及 logs 归属（安全合规：不以 root 运行应用）
RUN addgroup -g 1000 appuser && \
    adduser -D -G appuser -u 1000 appuser && \
    chown -R appuser:appuser /data

# entrypoint 以 root 运行以便修复挂载目录权限；再通过 su-exec 降权启动 Java
USER root
ENTRYPOINT ["/data/entrypoint.sh"]
