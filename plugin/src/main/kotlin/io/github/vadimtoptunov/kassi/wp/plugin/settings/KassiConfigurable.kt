package io.github.vadimtoptunov.kassi.wp.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import io.github.vadimtoptunov.kassi.wp.plugin.rules.ProjectRuleBase

/** Settings | Tools | Kassi WordPress Security. */
class KassiConfigurable(private val project: Project) : BoundConfigurable("Kassi WordPress Security") {
    private val state get() = KassiSettings.getInstance(project).state

    override fun createPanel(): DialogPanel = panel {
        row {
            comment(
                "Enable, disable and set the severity of each rule in Settings | Editor | Inspections, " +
                    "group \"Kassi WordPress Security\".",
            )
        }
        group("Excluded paths") {
            row {
                textArea()
                    .rows(5)
                    .align(AlignX.FILL)
                    .bindText({ state.excludedPaths.orEmpty() }, { state.excludedPaths = it })
                    .comment(
                        "One per line, relative to the project root. <code>vendor/</code> excludes every vendor " +
                            "directory; <code>src/legacy</code> is a prefix; <code>*.min.php</code> is a glob.",
                    )
            }
        }
        group("Rule pack") {
            row("Folder:") {
                textField()
                    .align(AlignX.FILL)
                    .bindText({ state.rulePackDir.orEmpty() }, { state.rulePackDir = it.trim() })
                    .comment(
                        "Optional. A folder in this project (committed with it) with rule YAML in the kassi-wp-rules " +
                            "format. Its rules replace built-in rules with the same id. Nothing is downloaded.",
                    )
            }
        }
    }

    override fun apply() {
        super.apply()
        ProjectRuleBase.getInstance(project).reset()
    }
}
