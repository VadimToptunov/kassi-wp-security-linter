package io.github.vadimtoptunov.kassi.wp.plugin.report

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.report.Report
import io.github.vadimtoptunov.kassi.wp.core.report.ReportEntry
import io.github.vadimtoptunov.kassi.wp.plugin.inspections.Inspections
import io.github.vadimtoptunov.kassi.wp.plugin.inspections.RuleInspection
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.ProjectRuleBase
import java.io.File

/**
 * Tools | Kassi WP Security Report: runs every rule enabled in the current inspection profile over
 * the project's PHP files (minus excluded paths) and writes `kassi-wp-security-report.md` and `.json`
 * to the project root. Deterministic output, no network.
 */
class GenerateReportAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        object : Task.Backgroundable(project, "Kassi WP security report", true) {
            override fun run(indicator: ProgressIndicator) {
                val report = buildReport(project, indicator)
                if (report.filesScanned == 0) {
                    ApplicationManager.getApplication().invokeLater {
                        ProjectRuleBase.notifyInfo(
                            project,
                            "No PHP files to scan: open a folder with your plugin or theme as the project " +
                                "(File | Open…), and check the excluded paths in Settings | Tools | Kassi WordPress Security.",
                            NotificationType.WARNING,
                        )
                    }
                    return
                }
                val base = File(project.basePath ?: return)
                val md = File(base, "$FILE_NAME.md").apply { writeText(report.markdown()) }
                File(base, "$FILE_NAME.json").writeText(report.json())
                ApplicationManager.getApplication().invokeLater {
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(md)?.let {
                        FileEditorManager.getInstance(project).openFile(it, true)
                    }
                    ProjectRuleBase.notifyInfo(
                        project,
                        "Report written: ${report.entries.size} finding(s) in ${report.filesScanned} file(s) → $FILE_NAME.md / .json",
                        NotificationType.INFORMATION,
                    )
                }
            }
        }.queue()
    }

    companion object {
        const val FILE_NAME = "kassi-wp-security-report"

        /**
         * PHP files to scan: everything under the project's content roots (walked directly, so it works
         * while indexing is still running), plus PHP files open in the editor that live outside the
         * project (a single file opened with File | Open).
         */
        fun phpFiles(project: Project): List<VirtualFile> = ApplicationManager.getApplication().runReadAction(
            Computable {
                val found = linkedSetOf<VirtualFile>()
                ProjectRootManager.getInstance(project).fileIndex.iterateContent { vf ->
                    if (!vf.isDirectory && vf.extension.equals("php", ignoreCase = true)) found += vf
                    true
                }
                FileEditorManager.getInstance(project).openFiles
                    .filter { !it.isDirectory && it.extension.equals("php", ignoreCase = true) }
                    .forEach { found += it }
                found.sortedBy { it.path }
            },
        )

        fun buildReport(project: Project, indicator: ProgressIndicator?): Report {
            val rules = ProjectRuleBase.getInstance(project)
            val app = ApplicationManager.getApplication()
            val files = phpFiles(project)
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            // Rules that are enabled in the inspection profile and implemented: listed even when no file
            // was scanned, so an empty report still says what would have been checked.
            val enabled = Inspections.byRuleId.filter { (ruleId, tool) ->
                val key = HighlightDisplayKey.find(tool.shortName)
                (key == null || profile.isToolEnabled(key)) && rules.rule(ruleId) != null && rules.check(ruleId) != null
            }
            val entries = mutableListOf<ReportEntry>()
            var scanned = 0
            files.forEachIndexed { i, vf ->
                indicator?.checkCanceled()
                indicator?.fraction = i.toDouble() / files.size.coerceAtLeast(1)
                app.runReadAction {
                    val psi = PsiManager.getInstance(project).findFile(vf) as? PhpFile ?: return@runReadAction
                    if (RuleInspection.isExcluded(psi)) return@runReadAction
                    scanned++
                    val path = RuleInspection.relativePath(project, psi) ?: vf.path
                    val lines = StringUtil.splitByLines(psi.viewProvider.contents.toString(), false)
                    for ((ruleId, tool) in enabled) {
                        val key = HighlightDisplayKey.find(tool.shortName)
                        if (key != null && !profile.isToolEnabled(key, psi)) continue
                        val rule = rules.rule(ruleId) ?: continue
                        val check = rules.check(ruleId) ?: continue
                        for (f in check.checkFile(PhpPsiAstNode.wrap(psi))) {
                            val line = f.node.location.line
                            entries += ReportEntry(rule, path, line, lines.getOrNull(line - 1).orEmpty().trim().take(160), f.message, f.weak)
                        }
                    }
                }
            }
            val version = GenerateReportAction::class.java.classLoader.getResource("kassi/plugin-version.txt")?.readText()?.trim() ?: "dev"
            val ruleList = enabled.keys.sorted().mapNotNull { rules.rule(it) }
            return Report(version, ruleList, entries, scanned)
        }
    }
}
