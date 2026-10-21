package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import io.github.vadimtoptunov.kassi.wp.core.check.Finding
import io.github.vadimtoptunov.kassi.wp.plugin.fixes.AddPermissionCallbackFix

/** WP-004 (CWE-862): REST route without a real permission_callback. Logic: core `RestRouteCheck`. */
class WP004UnprotectedRestRouteInspection : RuleInspection(RULE_ID) {

    /** Highlight the function name for a call, the opening token for an endpoint array, else the node. */
    override fun anchor(psi: PsiElement): PsiElement = when (psi) {
        is FunctionReference -> psi.nameNode?.psi ?: psi
        is ArrayCreationExpression -> psi.firstChild ?: psi
        else -> psi
    }

    override fun fixesFor(finding: Finding, psi: PsiElement): List<LocalQuickFix> {
        if (finding.quickFix != AddPermissionCallbackFix.ID) return emptyList()
        val endpoint = when (psi) {
            is ArrayCreationExpression -> psi
            is FunctionReference -> psi.parameters.getOrNull(2) as? ArrayCreationExpression
            else -> null
        }
        return listOfNotNull(endpoint?.let(::AddPermissionCallbackFix))
    }

    companion object {
        const val RULE_ID = "WP-004"
    }
}
