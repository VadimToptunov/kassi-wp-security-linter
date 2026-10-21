package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.jetbrains.php.lang.PhpFileType

/** A finding from a headless run: 1-based line, the trimmed line text, and the message. */
data class RunFinding(val line: Int, val lineText: String, val message: String)

/**
 * Runs one inspection's own visitor over PHP text parsed without indexing (real-world files can trip
 * the stub builder in test mode): exactly the shipped code path, minus the editor.
 */
fun runInspection(project: Project, tool: LocalInspectionTool, name: String, text: String): List<RunFinding> {
    val file = PsiFileFactory.getInstance(project).createFileFromText(name, PhpFileType.INSTANCE, text, System.currentTimeMillis(), true)
    val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
    val visitor = tool.buildVisitor(holder, false)
    file.accept(object : PsiRecursiveElementWalkingVisitor() {
        override fun visitElement(element: PsiElement) {
            element.accept(visitor)
            super.visitElement(element)
        }
    })
    val lines = text.lines()
    return holder.results.map { p ->
        val line = text.substring(0, p.psiElement.textRange.startOffset).count { it == '\n' } + 1
        RunFinding(line, lines[line - 1].trim(), p.descriptionTemplate)
    }
}
