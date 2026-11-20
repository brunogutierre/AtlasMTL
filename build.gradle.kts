plugins {
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", "docs/**/*.md", ".gitignore", ".github/**/*.yml", "compose.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
