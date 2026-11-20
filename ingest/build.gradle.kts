plugins {
    id("atlas.java")
}

// Downloads the open datasets, normalizes them and loads them into PostGIS.
// See docs/adr/0005-ingest-as-ci-job.md.
dependencies {
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.dataformat:jackson-dataformat-csv")
}
