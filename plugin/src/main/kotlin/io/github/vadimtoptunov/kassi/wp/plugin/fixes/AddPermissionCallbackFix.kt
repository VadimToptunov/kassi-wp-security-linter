package io.github.vadimtoptunov.kassi.wp.plugin.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement

/**
 * Adds `'permission_callback' => '__return_true'` to a read-only endpoint that has none.
 *
 * Offered only where it is behaviour-preserving: WordPress already treats a missing callback as
 * public, so this makes the existing exposure explicit and silences core's _doing_it_wrong notice.
 * It is never offered on write routes, where the right callback depends on the plugin's roles.
 */
class AddPermissionCallbackFix(endpoint: ArrayCreationExpression) : LocalQuickFix {
    private val pointer = SmartPointerManager.createPointer(endpoint)

    override fun getFamilyName(): String = "Declare 'permission_callback' => '__return_true' (route stays public)"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val array = pointer.element ?: return
        val file = array.containingFile ?: return
        val docManager = PsiDocumentManager.getInstance(project)
        val document = docManager.getDocument(file) ?: return
        docManager.doPostponedOperationsAndUnblockDocument(document)

        val text = document.charsSequence
        val start = array.textRange.startOffset
        val end = array.textRange.endOffset
        val closing = end - 1 // ')' or ']'
        var last = closing - 1
        while (last > start && text[last].isWhitespace()) last--
        val hasTrailingComma = text[last] == ','
        val entry = "'permission_callback' => '__return_true'"

        val lastEntry = PsiTreeUtil.getChildrenOfType(array, ArrayHashElement::class.java)?.lastOrNull()
        val multiline = text.subSequence(start, end).contains('\n')
        val insertion = if (multiline && lastEntry != null) {
            val lineStart = document.getLineStartOffset(document.getLineNumber(lastEntry.textRange.startOffset))
            val indent = text.subSequence(lineStart, lastEntry.textRange.startOffset).toString()
            (if (hasTrailingComma) "" else ",") + "\n" + indent + entry + ","
        } else {
            (if (hasTrailingComma) " " else ", ") + entry
        }
        document.insertString(last + 1, insertion)
        docManager.commitDocument(document)
    }

    companion object {
        const val ID = "add_permission_callback"
    }
}
