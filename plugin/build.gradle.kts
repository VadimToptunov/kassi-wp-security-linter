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

tasks.test {
    useJUnitPlatform()
}
