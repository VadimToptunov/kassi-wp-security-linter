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
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.jetbrains.php.lang.PhpFileType
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

        fun buildReport(project: Project, indicator: ProgressIndicator?): Report {
            val rules = ProjectRuleBase.getInstance(project)
            val app = ApplicationManager.getApplication()
            val files = app.runReadAction(
                Computable { FileTypeIndex.getFiles(PhpFileType.INSTANCE, GlobalSearchScope.projectScope(project)).toList() },
            ).sortedBy { it.path }
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            val entries = mutableListOf<ReportEntry>()
            val checked = linkedSetOf<String>()
            var scanned = 0
            files.forEachIndexed { i, vf ->
                indicator?.checkCanceled()
                indicator?.fraction = i.toDouble() / files.size.coerceAtLeast(1)
                app.runReadAction {
                    val psi = PsiManager.getInstance(project).findFile(vf) as? PhpFile ?: return@runReadAction
                    if (RuleInspection.isExcluded(psi)) return@runReadAction
                    scanned++
                    val path = RuleInspection.relativePath(project, psi) ?: vf.path
                    val text = psi.viewProvider.contents
                    for ((ruleId, tool) in Inspections.byRuleId) {
                        val key = HighlightDisplayKey.find(tool.shortName)
                        if (key != null && !profile.isToolEnabled(key, psi)) continue
                        val rule = rules.rule(ruleId) ?: continue
                        val check = rules.check(ruleId) ?: continue
                        checked += ruleId
                        for (f in check.checkFile(PhpPsiAstNode.wrap(psi))) {
                            val line = f.node.location.line
                            val lineText = StringUtil.splitByLines(text.toString(), false).getOrNull(line - 1).orEmpty().trim()
                            entries += ReportEntry(rule, path, line, lineText.take(160), f.message, f.weak)
                        }
                    }
                }
            }
            val version = GenerateReportAction::class.java.classLoader.getResource("kassi/plugin-version.txt")?.readText()?.trim() ?: "dev"
            val ruleList = checked.sorted().mapNotNull { rules.rule(it) }
            return Report(version, ruleList, entries, scanned)
        }
    }
}
