import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
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
    implementation(project(":core"))

    intellijPlatform {
        // Build/test against the compatibility floor (2025.3); newer IDEs are covered by the verifier.
        phpstorm(providers.gradleProperty("platformVersion"))
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
    }

    // BasePlatformTestCase is JUnit3/4-style; run it on the JUnit Platform via vintage.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

intellijPlatform {
    // Distribution zip / sandbox name (defaults to the module name "plugin").
    projectName = rootProject.name

    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Open-ended: no until-build.
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // Both ends of the range: the 2025.3 floor and the newest PhpStorm release.
            providers.gradleProperty("verifierIdeVersions").get().split(',').map(String::trim).forEach { v ->
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm, v)
            }
        }
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INVALID_PLUGIN,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
        )
    }
}

// Built-in rule base: copy the YAML rules from the kassi-wp-rules submodule into the plugin's
// resources under kassi/wp-rules/, plus an index (jar resources cannot be listed reliably).
val rulesSourceDir = rootProject.layout.projectDirectory.dir("rules/rules")
val generatedRuleResources = layout.buildDirectory.dir("generated/rule-resources")
val generateRuleResources by tasks.registering {
    description = "Copies kassi-wp-rules YAML into plugin resources (kassi/wp-rules/) and records the plugin version."
    inputs.dir(rulesSourceDir).withPropertyName("rules")
    outputs.dir(generatedRuleResources)
    val src = rulesSourceDir.asFile
    val out = generatedRuleResources.map { it.dir("kassi/wp-rules").asFile }
    // The report prints the plugin version; baked in so no plugin-descriptor API is needed at runtime.
    val pluginVersion = providers.gradleProperty("pluginVersion")
    inputs.property("pluginVersion", pluginVersion)
    doLast {
        val target = out.get()
        target.deleteRecursively()
        target.mkdirs()
        val files = src.walkTopDown().filter { it.isFile && it.extension == "yml" }.sortedBy { it.name }.toList()
        require(files.isNotEmpty()) { "No rules in $src. Run: git submodule update --init" }
        require(files.map { it.name }.toSet().size == files.size) { "Duplicate rule file names in $src" }
        files.forEach { it.copyTo(target.resolve(it.name)) }
        target.resolve("index.txt").writeText(files.joinToString("\n", postfix = "\n") { it.name })
        target.parentFile.resolve("plugin-version.txt").writeText(pluginVersion.get())
    }
}
sourceSets.main {
    resources.srcDir(generateRuleResources)
}

tasks.test {
    useJUnitPlatform()
    // Corpus acceptance tests read the kassi-wp-corpus submodule directly.
    val corpusDir = rootProject.layout.projectDirectory.dir("corpus/cases")
    inputs.dir(corpusDir).withPropertyName("corpus")
    systemProperty("kassi.corpus.dir", corpusDir.asFile.absolutePath)
    // Opt-in false-positive run over the popular-plugin corpus (corpus/fetch-popular.sh).
    if (providers.gradleProperty("kassi.fprun").isPresent) {
        systemProperty("kassi.fprun.dir", rootProject.layout.projectDirectory.dir("corpus/plugins").asFile.absolutePath)
        systemProperty("kassi.fprun.report", layout.buildDirectory.file("reports/fp-run.md").get().asFile.absolutePath)
        outputs.upToDateWhen { false }
    }
}
