# 物资管理系统后端
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY backend/target/wuzi-admin-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
