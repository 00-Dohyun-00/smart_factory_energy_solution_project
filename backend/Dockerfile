# 1단계: Gradle로 jar 빌드 (팀원 PC에 Java/Gradle 없어도 docker만 있으면 빌드됨)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 빌드된 jar만 가지고 실제로 실행 (이미지 용량을 줄이기 위해 JDK가 아닌 JRE 사용)
FROM eclipse-temurin:17-jre
WORKDIR /app
# 헬스체크(curl)용. apt 패키지라 애플리케이션 라이브러리는 아님
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
