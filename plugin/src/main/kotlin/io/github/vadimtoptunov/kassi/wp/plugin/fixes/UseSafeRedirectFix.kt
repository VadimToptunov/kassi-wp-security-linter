package io.github.vadimtoptunov.kassi.wp.plugin.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.jetbrains.php.lang.psi.elements.FunctionReference

/**
 * `wp_redirect(...)` → `wp_safe_redirect(...)`: same signature, but only allowed hosts are followed
 * (others fall back to admin_url()). This is the fix the WordPress docs prescribe for input-controlled
 * URLs. Only offered where the URL is proven to come from the request.
 */
class UseSafeRedirectFix(call: FunctionReference) : LocalQuickFix {
    private val pointer = SmartPointerManager.createPointer(call)

    override fun getFamilyName(): String = "Use wp_safe_redirect()"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = pointer.element ?: return
        val nameNode = call.nameNode ?: return
        val docManager = PsiDocumentManager.getInstance(project)
        val document = docManager.getDocument(call.containingFile) ?: return
        docManager.doPostponedOperationsAndUnblockDocument(document)
        document.replaceString(nameNode.textRange.startOffset, nameNode.textRange.endOffset, "wp_safe_redirect")
        docManager.commitDocument(document)
    }

    companion object {
        const val ID = "use_wp_safe_redirect"
    }
}
