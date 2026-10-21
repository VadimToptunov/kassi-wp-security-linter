package io.github.vadimtoptunov.kassi.wp.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import io.github.vadimtoptunov.kassi.wp.core.config.PathExclusions

/**
 * Project settings, stored in `.idea/kassi-wp-security.xml` so a team (and CI) shares them.
 * Rule toggles and severities are not here: they are the IDE's inspection profile.
 */
@Service(Service.Level.PROJECT)
@State(name = "KassiWpSecurity", storages = [Storage("kassi-wp-security.xml")])
class KassiSettings : SimplePersistentStateComponent<KassiSettings.State>(State()) {

    class State : BaseState() {
        /** One pattern per line; see [PathExclusions]. */
        var excludedPaths by string(PathExclusions.DEFAULT.joinToString("\n"))

        /** Optional project folder with a rule pack (YAML in the kassi-wp-rules format). */
        var rulePackDir by string("")
    }

    val exclusions: PathExclusions get() = PathExclusions(state.excludedPaths.orEmpty().lines())

    companion object {
        fun getInstance(project: Project): KassiSettings = project.getService(KassiSettings::class.java)
    }
}
