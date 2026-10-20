plugins {
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.intellij.platform") apply false
}

allprojects {
    group = providers.gradleProperty("pluginGroup").get()
    // Overridable at release time: ./gradlew publishPlugin -PpluginVersion=0.1.0
    version = providers.gradleProperty("pluginVersion").get()
}
