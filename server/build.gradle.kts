plugins {
    id("atlas.spring-boot")
    alias(libs.plugins.recordbridge)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation(libs.bucket4j)
    implementation("com.github.ben-manes.caffeine:caffeine")
    runtimeOnly(project(":db"))
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(testFixtures(project(":db")))
}

// Generates the Angular API client and event types from the compiled controllers with
// RecordBridge (docs/adr/0003-sse-agent-events.md): ./gradlew :server:generateTypeScript
recordBridge {
    basePackages = listOf("io.github.brunogutierre.atlasmtl.api")
    outputDir = rootProject.file("web/src/app/api")
    unmarkedNullness = "NON_NULL"
}
