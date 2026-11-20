plugins {
    id("atlas.spring-boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    runtimeOnly(project(":db"))
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(testFixtures(project(":db")))
}

// Generates the Angular API client and event types from the compiled controllers with
// RecordBridge (docs/adr/0003-sse-agent-events.md). Until its Gradle plugin is approved on the
// Plugin Portal, a small main class calls the generator library from Maven Central.
val codegen =
    sourceSets.create("codegen") {
        compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
        runtimeClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    }

dependencies {
    "codegenImplementation"(libs.recordbridge.generator)
}

tasks.register<JavaExec>("generateTypeScript") {
    group = "build"
    description = "Writes the Angular API client to web/src/app/api."
    classpath = codegen.runtimeClasspath
    mainClass = "io.github.brunogutierre.atlasmtl.codegen.GenerateApi"
    args(
        rootProject.layout.projectDirectory
            .dir("web/src/app/api")
            .asFile.absolutePath,
        sourceSets.main
            .get()
            .output.classesDirs.asPath,
    )
}
