package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import io.github.vadimtoptunov.kassi.wp.core.check.RestRouteCheck
import io.github.vadimtoptunov.kassi.wp.plugin.fixes.AddPermissionCallbackFix
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules

/** WP-004 (CWE-862): REST route without a real permission_callback. Logic: [RestRouteCheck]. */
class WP004UnprotectedRestRouteInspection : LocalInspectionTool() {

    private val check by lazy { RestRouteCheck(BuiltInRules.knowledgeBase.require(RULE_ID)) }
    private val registrars by lazy { BuiltInRules.knowledgeBase.require(RULE_ID).source.map { it.lowercase() }.toSet() }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PhpElementVisitor() {
            override fun visitPhpFunctionCall(reference: FunctionReference) {
                // Cheap name filter before building the AST view.
                if (reference.name?.lowercase() !in registrars) return
                for (finding in check.check(PhpPsiAstNode.wrap(reference))) {
                    val psi = (finding.node as PhpPsiAstNode).psi
                    val fixes = buildList<LocalQuickFix> {
                        if (finding.quickFix == AddPermissionCallbackFix.ID) {
                            endpointArray(psi)?.let { add(AddPermissionCallbackFix(it)) }
                        }
                    }
                    holder.registerProblem(
                        anchor(psi),
                        finding.message,
                        if (finding.weak) ProblemHighlightType.WEAK_WARNING else ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *fixes.toTypedArray(),
                    )
                }
            }
        }

    /** Highlight the function name for a call, the opening token for an endpoint array, else the node. */
    private fun anchor(psi: PsiElement): PsiElement = when (psi) {
        is FunctionReference -> psi.nameNode?.psi ?: psi
        is ArrayCreationExpression -> psi.firstChild ?: psi
        else -> psi
    }

    private fun endpointArray(psi: PsiElement): ArrayCreationExpression? = when (psi) {
        is ArrayCreationExpression -> psi
        is FunctionReference -> psi.parameters.getOrNull(2) as? ArrayCreationExpression
        else -> null
    }

    companion object {
        const val RULE_ID = "WP-004"
    }
}
