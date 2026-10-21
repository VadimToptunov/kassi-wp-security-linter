package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.FunctionReference
import io.github.vadimtoptunov.kassi.wp.core.check.Finding
import io.github.vadimtoptunov.kassi.wp.plugin.fixes.UseSafeRedirectFix

/** WP-008 (CWE-601): `wp_redirect()` to an input-controlled URL. Logic: core `UnsafeRedirectCheck`. */
class WP008UnsafeRedirectInspection : RuleInspection(RULE_ID) {

    override fun fixesFor(finding: Finding, psi: PsiElement): List<LocalQuickFix> {
        if (finding.quickFix != UseSafeRedirectFix.ID) return emptyList()
        val call = PsiTreeUtil.getParentOfType(psi, FunctionReference::class.java) ?: return emptyList()
        return listOf(UseSafeRedirectFix(call))
    }

    companion object {
        const val RULE_ID = "WP-008"
    }
}
