rootProject.name = "kassi-wp-security-linter"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // Versions come from gradle.properties (single source of truth, with provenance comments).
    val kotlinVersion: String by settings
    val intellijPlatformGradlePluginVersion: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.intellij.platform") version intellijPlatformGradlePluginVersion
    }
}

plugins {
    // Toolchain auto-provisioning so CI/dev machines don't need a matching local JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// core   — pure Kotlin, zero IntelliJ dependencies (rule model, YAML KB, taint flow over core.ast).
// plugin — the IntelliJ plugin: PSI → core.ast adapter, inspections, fixes, report, settings.
include("core", "plugin")
