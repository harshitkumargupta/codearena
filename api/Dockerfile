# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY worker/pom.xml worker/
COPY integration-tests/pom.xml integration-tests/
COPY common/src common/src
COPY api/src api/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl api -am package -DskipTests \
    && cp api/target/api-*-exec.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10002 app \
    && useradd --system --uid 10002 --gid app --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER 10002:10002
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-jar", "application.jar"]
