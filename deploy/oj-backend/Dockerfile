FROM amazoncorretto:21-alpine

WORKDIR /app

RUN addgroup -S oj && \
    adduser -S oj -G oj && \
    mkdir -p /app/logs && \
    chown -R oj:oj /app

COPY --chown=oj:oj my-oj.jar /app/app.jar

USER oj

EXPOSE 8101

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
