package io.github.vadimtoptunov.kassi.wp.plugin.rules

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.github.vadimtoptunov.kassi.wp.core.check.Checks
import io.github.vadimtoptunov.kassi.wp.core.check.FileCheck
import io.github.vadimtoptunov.kassi.wp.core.kb.KnowledgeBase
import io.github.vadimtoptunov.kassi.wp.core.kb.KnowledgeBaseException
import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import io.github.vadimtoptunov.kassi.wp.plugin.settings.KassiSettings
import java.io.File

/**
 * The rule base in effect for a project: the built-in rules, with the project's optional rule pack
 * layered on top. Read from disk only (no network). An invalid pack is reported once and ignored,
 * so a broken pack never silently weakens analysis: the built-in rules keep running.
 */
@Service(Service.Level.PROJECT)
class ProjectRuleBase(private val project: Project) {
    private class Loaded(val kb: KnowledgeBase, val checks: Map<String, FileCheck>)

    @Volatile
    private var loaded: Loaded? = null

    fun reset() {
        loaded = null
    }

    val knowledgeBase: KnowledgeBase get() = get().kb

    fun rule(id: String): Rule? = get().kb.rule(id)

    fun check(id: String): FileCheck? = get().checks[id]

    private fun get(): Loaded = loaded ?: synchronized(this) { loaded ?: load().also { loaded = it } }

    private fun load(): Loaded {
        var kb = BuiltInRules.knowledgeBase
        val dir = KassiSettings.getInstance(project).state.rulePackDir.orEmpty().trim()
        if (dir.isNotEmpty()) {
            val folder = File(dir).let { if (it.isAbsolute) it else File(project.basePath ?: ".", dir) }
            try {
                val pack = KnowledgeBase.loadDirectory(folder)
                kb = kb.withOverrides(pack)
                val unsupported = Checks.unimplemented(kb)
                if (unsupported.isNotEmpty()) {
                    notify("Rule pack rules need a newer plugin version and are skipped: ${unsupported.joinToString()}", NotificationType.WARNING)
                }
            } catch (e: KnowledgeBaseException) {
                notify("Rule pack in '$dir' is invalid and was ignored:\n" + e.errors.take(5).joinToString("\n"), NotificationType.ERROR)
            } catch (e: IllegalArgumentException) {
                notify("Rule pack folder '$dir' was not found and was ignored.", NotificationType.ERROR)
            }
        }
        val checks = Checks.forRules(kb).mapKeys { it.key.id }
        return Loaded(kb, checks)
    }

    private fun notify(text: String, type: NotificationType) = notifyInfo(project, text, type)

    companion object {
        const val NOTIFICATION_GROUP = "Kassi WordPress Security"

        fun getInstance(project: Project): ProjectRuleBase = project.getService(ProjectRuleBase::class.java)

        fun notifyInfo(project: Project, text: String, type: NotificationType) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("Kassi WordPress Security", text, type)
                .notify(project)
        }
    }
}
