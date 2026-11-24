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
    testImplementation("tools.jackson.dataformat:jackson-dataformat-yaml")
}

// Generates the Angular API client and event types from the compiled controllers with
// RecordBridge (docs/adr/0003-sse-agent-events.md): ./gradlew :server:generateTypeScript
recordBridge {
    basePackages = listOf("io.github.brunogutierre.atlasmtl.api")
    outputDir = rootProject.file("web/src/app/api")
    unmarkedNullness = "NON_NULL"
}

// The eval suite asks the real model (costs tokens), so it only runs on demand:
// ANTHROPIC_API_KEY=… DATABASE_URL=… ./gradlew :server:evals
tasks.test {
    useJUnitPlatform { excludeTags("live") }
}

val evals by tasks.registering(Test::class) {
    group = "verification"
    description = "Asks the golden questions to the real model and writes build/reports/evals."
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
    outputs.upToDateWhen { false }
    systemProperty(
        "evals.reports",
        layout.buildDirectory
            .dir("reports/evals")
            .get()
            .asFile.absolutePath,
    )
}
