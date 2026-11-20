plugins {
    id("atlas.spring-boot")
}

// Downloads the open datasets, normalizes them and loads them into PostGIS.
// Runs as a command-line job; see docs/adr/0005-ingest-as-ci-job.md.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-csv")
    runtimeOnly(project(":db"))
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(testFixtures(project(":db")))
}
