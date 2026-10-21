package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Function
import io.github.vadimtoptunov.kassi.wp.core.check.Finding
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.ProjectRuleBase
import io.github.vadimtoptunov.kassi.wp.plugin.settings.KassiSettings

/**
 * Shared shell for every rule: runs the rule's core check (from the project's rule base, i.e.
 * built-in rules plus an optional rule pack) once per PHP file that is not excluded in settings,
 * and reports its findings. All detection logic lives in core.
 */
abstract class RuleInspection(val ruleId: String) : LocalInspectionTool() {

    protected open fun fixesFor(finding: Finding, psi: PsiElement): List<LocalQuickFix> = emptyList()

    /** A function/method is highlighted on its name; a closure on its `function` keyword. */
    protected open fun anchor(psi: PsiElement): PsiElement = when (psi) {
        is Function -> psi.nameIdentifier ?: psi.firstChild ?: psi
        else -> psi
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is PhpFile || isExcluded(file)) return
                val check = ProjectRuleBase.getInstance(file.project).check(ruleId) ?: return
                for (finding in check.checkFile(PhpPsiAstNode.wrap(file))) {
                    val psi = (finding.node as PhpPsiAstNode).psi
                    holder.registerProblem(
                        anchor(psi),
                        finding.message,
                        if (finding.weak) ProblemHighlightType.WEAK_WARNING else ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *fixesFor(finding, psi).toTypedArray(),
                    )
                }
            }
        }

    companion object {
        fun relativePath(project: Project, file: PsiFile): String? {
            val vf = file.originalFile.virtualFile ?: return null
            val base = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
            return base?.let { VfsUtilCore.getRelativePath(vf, it) } ?: vf.path
        }

        fun isExcluded(file: PsiFile): Boolean {
            val path = relativePath(file.project, file) ?: return false
            return KassiSettings.getInstance(file.project).exclusions.isExcluded(path)
        }
    }
}
