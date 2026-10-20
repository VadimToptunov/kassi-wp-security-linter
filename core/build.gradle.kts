import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Pure Kotlin. MUST NOT depend on IntelliJ Platform / PHP plugin artifacts, so the same engine
// can run over a headless PHP parser. Enforced by ModuleBoundaryTest.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
    compilerOptions {
        val api = KotlinVersion.fromVersion(providers.gradleProperty("kotlinApiVersion").get())
        apiVersion.set(api)
        languageVersion.set(api)
    }
}

dependencies {
    // Not bundled into the plugin (the IDE provides stdlib); compileOnly keeps it off the runtime
    // classpath the plugin ships, while tests still get it below.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
    // ModuleBoundaryTest scans the module's own sources.
    systemProperty("kassi.core.srcDir", layout.projectDirectory.dir("src/main").asFile.absolutePath)
}
