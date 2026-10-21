package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-011 (CWE-434). Logic: core `UnsafeUploadCheck`. */
class WP011UnsafeFileUploadInspection : RuleInspection(RULE_ID) {
    /** Highlight the function name of the storing call. */
    override fun anchor(psi: com.intellij.psi.PsiElement): com.intellij.psi.PsiElement =
        (psi as? com.jetbrains.php.lang.psi.elements.FunctionReference)?.nameNode?.psi ?: psi

    companion object {
        const val RULE_ID = "WP-011"
    }
}
