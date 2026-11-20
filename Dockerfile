# One container: Spring Boot serves the API and the Angular build. See docs/adr/0008-hosting-render-neon.md.

FROM node:24-bookworm-slim AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --ignore-scripts
COPY web/ ./
RUN npx ng build

FROM eclipse-temurin:25-jdk AS server
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY db/ db/
COPY ingest/ ingest/
COPY server/ server/
COPY --from=web /src/web/dist/web/browser /src/web-dist
RUN ./gradlew --no-daemon :server:bootJar -PwebDist=/src/web-dist -x test

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 atlas
USER atlas
WORKDIR /app
COPY --from=server /src/server/build/libs/server-*-SNAPSHOT.jar app.jar
EXPOSE 8080
# Use most of the container's memory for the heap; the rest is for threads, metaspace and buffers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
