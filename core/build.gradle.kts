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
    // YAML 1.2 loader for the rule base. Bundled with the plugin: the plugin classloader resolves its
    // own jars first, so the copy the IDE ships (different version) is never mixed in.
    // Source: repo1.maven.org org/snakeyaml/snakeyaml-engine, 3.2 published 2026-10-03.
    implementation("org.snakeyaml:snakeyaml-engine:3.2")
    // Not bundled into the plugin (the IDE provides stdlib); compileOnly keeps it off the runtime
    // classpath the plugin ships, while tests still get it below.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    // Test-only: proves the hand-written report JSON parses with a real parser.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
    // ModuleBoundaryTest scans the module's own sources.
    systemProperty("kassi.core.srcDir", layout.projectDirectory.dir("src/main").asFile.absolutePath)
    // RuleBaseTest validates every rule in the kassi-wp-rules submodule.
    val rulesDir = rootProject.layout.projectDirectory.dir("rules/rules")
    inputs.dir(rulesDir).withPropertyName("rules")
    systemProperty("kassi.rules.dir", rulesDir.asFile.absolutePath)
}
