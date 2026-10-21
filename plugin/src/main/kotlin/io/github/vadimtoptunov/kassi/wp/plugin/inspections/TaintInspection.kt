package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.check.Finding
import io.github.vadimtoptunov.kassi.wp.core.check.TaintCheck
import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules

/**
 * Shared shell for the "input → sink" inspections: runs the rule's [TaintCheck] once per file and
 * reports its findings. The flow logic lives in core.
 */
abstract class TaintInspection(private val ruleId: String) : LocalInspectionTool() {

    protected abstract fun createCheck(rule: Rule): TaintCheck

    protected open fun fixesFor(finding: Finding, psi: PsiElement): List<LocalQuickFix> = emptyList()

    private val check by lazy { createCheck(BuiltInRules.knowledgeBase.require(ruleId)) }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is PhpFile) return
                for (finding in check.checkFile(PhpPsiAstNode.wrap(file))) {
                    val psi = (finding.node as PhpPsiAstNode).psi
                    holder.registerProblem(
                        psi,
                        finding.message,
                        if (finding.weak) ProblemHighlightType.WEAK_WARNING else ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *fixesFor(finding, psi).toTypedArray(),
                    )
                }
            }
        }
}
