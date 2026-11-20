// Convention plugin shared by every AtlasMTL Java module.
plugins {
    java
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror", "-parameters"))
}

dependencies {
    // Spring Boot and Spring AI manage every library version, tests included.
    "implementation"(platform(libs.findLibrary("spring-boot-bom").get()))
    "implementation"(platform(libs.findLibrary("spring-ai-bom").get()))
    // Null-marked packages, as in Spring 7.
    "implementation"("org.jspecify:jspecify")
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat()
        formatAnnotations()
        removeUnusedImports()
    }
}
