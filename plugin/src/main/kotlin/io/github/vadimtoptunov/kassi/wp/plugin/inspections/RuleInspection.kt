package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Function
import io.github.vadimtoptunov.kassi.wp.core.check.Checks
import io.github.vadimtoptunov.kassi.wp.core.check.FileCheck
import io.github.vadimtoptunov.kassi.wp.core.check.Finding
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules

/**
 * Shared shell for file-level rules: runs the rule's core [FileCheck] (from the [Checks] registry)
 * once per PHP file and reports its findings. All detection logic lives in core.
 */
abstract class RuleInspection(private val ruleId: String) : LocalInspectionTool() {

    protected open fun fixesFor(finding: Finding, psi: PsiElement): List<LocalQuickFix> = emptyList()

    private val check: FileCheck by lazy {
        Checks.forRule(BuiltInRules.knowledgeBase.require(ruleId)) ?: error("$ruleId has no check in core")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is PhpFile) return
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

    /** A function/method is highlighted on its name; a closure on its `function` keyword. */
    private fun anchor(psi: PsiElement): PsiElement = when (psi) {
        is Function -> psi.nameIdentifier ?: psi.firstChild ?: psi
        else -> psi
    }
}
