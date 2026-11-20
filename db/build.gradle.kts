plugins {
    id("atlas.java")
    `java-library`
    `java-test-fixtures`
}

// The schema: Flyway migrations shared by the server and the ingest job, plus a PostGIS
// Testcontainers fixture for their tests.
dependencies {
    testFixturesApi(platform(libs.spring.boot.bom))
    testFixturesApi("org.testcontainers:testcontainers-postgresql")

    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework:spring-jdbc")
    testRuntimeOnly("org.postgresql:postgresql")
}
