# 使用Java 1.8作为基础镜像
FROM amazoncorretto:21-alpine

# 设置工作目录为/app
WORKDIR /app

# 复制构建好的Online Judge System后端jar包到镜像中
COPY my-oj-0.0.1.jar app.jar

# 暴露8101端口
EXPOSE 8101

# 使用java -jar命令启动后端服务
ENTRYPOINT ["java", "-jar", "app.jar"]
