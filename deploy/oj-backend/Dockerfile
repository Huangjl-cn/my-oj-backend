FROM amazoncorretto:21-alpine

WORKDIR /app

RUN addgroup -S oj && \
    adduser -S oj -G oj && \
    mkdir -p /app/logs && \
    chown -R oj:oj /app

COPY --chown=oj:oj my-oj.jar /app/app.jar

USER oj

# 镜像只用于生产：固定 prod profile，不依赖启动参数（application.yml 不设默认 profile）
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8101

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
