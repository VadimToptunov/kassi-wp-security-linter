package io.github.vadimtoptunov.kassi.wp.plugin.psi

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.parser.PhpElementTypes
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.BinaryExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.ForeachStatement
import com.jetbrains.php.lang.psi.elements.Function
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.GroupStatement
import com.jetbrains.php.lang.psi.elements.If
import com.jetbrains.php.lang.psi.elements.Include
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.NewExpression
import com.jetbrains.php.lang.psi.elements.Parameter
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpExpression
import com.jetbrains.php.lang.psi.elements.PhpPrintExpression
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.SelfAssignmentExpression
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.elements.TernaryExpression
import com.jetbrains.php.lang.psi.elements.UnaryExpression
import com.jetbrains.php.lang.psi.elements.Variable
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.SourceLocation

/**
 * Adapter: IntelliJ PHP PSI → [AstNode]. The only place in the plugin that knows both worlds.
 * Inspections get findings back as [AstNode]s and unwrap [psi] to register problems.
 *
 * Wrappers are cheap and created on demand; equality is by the wrapped PSI element.
 */
class PhpPsiAstNode private constructor(val psi: PsiElement) : AstNode {

    override val kind: AstKind by lazy { kindOf(psi) }

    override val name: String? by lazy { nameOf(psi, kind) }

    override val text: String get() = psi.text

    override val location: SourceLocation by lazy {
        val file = psi.containingFile
        val range = psi.textRange
        val line = StringUtil.offsetToLineNumber(file.viewProvider.contents, range.startOffset) + 1
        SourceLocation(pathOf(file), line, range.startOffset, range.endOffset)
    }

    override val parent: AstNode?
        get() {
            if (psi is PsiFile) return null
            val p = psi.parent ?: return null
            // Skip the transparent closure-expression wrapper (see [wrap]) so parent/child stay symmetric.
            return wrap(if (transparentInner(p) != null) p.parent ?: return null else p)
        }

    override val children: List<AstNode> by lazy { significantChildren(psi).map(::wrap) }

    override val arguments: List<AstNode> by lazy {
        val args: List<PsiElement> = when (psi) {
            is FunctionReference -> psi.parameters.toList() // also MethodReference
            is NewExpression -> psi.parameters.toList()
            is PhpEchoStatement -> psi.arguments.toList()
            is PhpPrintExpression, is Include -> significantChildren(psi).take(1)
            is ArrayCreationExpression -> significantChildren(psi).filter { isArrayEntry(it) }
            else -> emptyList()
        }
        args.map(::wrap)
    }

    override fun child(role: AstRole): AstNode? {
        val e: PsiElement? = when (role) {
            AstRole.RECEIVER -> when (psi) {
                is MethodReference -> psi.classReference
                is FieldReference -> psi.classReference
                is ClassConstantReference -> psi.classReference
                is ArrayAccessExpression -> psi.value
                else -> null
            }
            AstRole.KEY -> when {
                psi is ArrayAccessExpression -> psi.index?.value
                psi is ArrayHashElement -> unwrapEntryPart(psi.key)
                psi is ForeachStatement -> psi.key
                else -> null
            }
            AstRole.VALUE -> when {
                psi is AssignmentExpression -> psi.value // also SelfAssignmentExpression
                psi is ForeachStatement -> psi.array
                psi is ArrayHashElement -> unwrapEntryPart(psi.value)
                isListArrayValue(psi) -> significantChildren(psi).firstOrNull()
                psi is UnaryExpression && kind == AstKind.CAST -> psi.value
                else -> null
            }
            AstRole.TARGET -> when (psi) {
                is AssignmentExpression -> psi.variable
                is ForeachStatement -> psi.value
                else -> null
            }
            AstRole.LEFT -> when (psi) {
                is BinaryExpression -> psi.leftOperand
                is TernaryExpression -> psi.trueVariant
                else -> null
            }
            AstRole.RIGHT -> when (psi) {
                is BinaryExpression -> psi.rightOperand
                is TernaryExpression -> psi.falseVariant
                else -> null
            }
            AstRole.CONDITION -> when (psi) {
                is TernaryExpression -> psi.condition
                is If -> psi.condition
                else -> null
            }
            AstRole.BODY -> when (psi) {
                // Block body, or for an arrow function (`fn () => expr`) the expression.
                is Function -> significantChildren(psi).let { kids ->
                    kids.firstOrNull { it is GroupStatement } ?: kids.lastOrNull { it is PhpExpression }
                }
                is If -> psi.statement
                else -> null
            }
        }
        return e?.let(::wrap)
    }

    override val stringValue: String?
        get() = (psi as? StringLiteralExpression)?.contents

    override fun equals(other: Any?): Boolean = other is PhpPsiAstNode && other.psi == psi
    override fun hashCode(): Int = psi.hashCode()
    override fun toString(): String = "PhpPsiAstNode($kind${name?.let { " $it" } ?: ""} @${location.line})"

    companion object {
        /**
         * A closure used as a value is parsed as an expression node wrapping the [Function]; that
         * wrapper carries no meaning of its own, so it is made transparent: wrapping it yields the
         * closure itself.
         */
        fun wrap(psi: PsiElement): PhpPsiAstNode = PhpPsiAstNode(transparentInner(psi) ?: psi)

        /**
         * Wrapper nodes that carry no meaning of its own, made transparent (wrapping one yields its content):
         *  - a closure used as a value: an expression node around the [Function];
         *  - an expression embedded in a string (`"{$a['k']}"`, `"$a[1]"`, `"{$o->p}"`): a nameless
         *    [Variable] around the real expression.
         */
        private fun transparentInner(psi: PsiElement): PsiElement? {
            if (psi is Function) return null
            val only = significantChildren(psi).singleOrNull() ?: return null
            return when {
                only is Function -> only.takeIf { it.isClosure && it.textRange == psi.textRange }
                psi is Variable && psi.parent is StringLiteralExpression && only is PhpExpression -> only
                else -> null
            }
        }

        private fun significantChildren(psi: PsiElement): List<PsiElement> =
            psi.children.filter { it !is PsiWhiteSpace && it !is PsiComment && it !is LeafPsiElement }

        private fun kindOf(psi: PsiElement): AstKind = when (psi) {
            is PhpFile -> AstKind.FILE
            is Method -> AstKind.METHOD
            is Function -> if (psi.isClosure) AstKind.CLOSURE else AstKind.FUNCTION
            is MethodReference -> if (psi.isStatic) AstKind.STATIC_CALL else AstKind.METHOD_CALL
            is FunctionReference -> AstKind.CALL
            is NewExpression -> AstKind.NEW
            is Variable -> AstKind.VARIABLE
            is ArrayAccessExpression -> AstKind.ARRAY_ACCESS
            is FieldReference -> AstKind.PROPERTY_ACCESS
            is SelfAssignmentExpression ->
                if (psi.operationType == PhpTokenTypes.opCONCAT_ASGN) AstKind.CONCAT_ASSIGNMENT else AstKind.ASSIGNMENT
            is AssignmentExpression -> AstKind.ASSIGNMENT
            is BinaryExpression -> if (psi.operationType == PhpTokenTypes.opCONCAT) AstKind.CONCAT else AstKind.BINARY
            is TernaryExpression -> AstKind.TERNARY
            is UnaryExpression -> if (isCast(psi)) AstKind.CAST else AstKind.OTHER
            is StringLiteralExpression ->
                if (significantChildren(psi).isEmpty()) AstKind.STRING_LITERAL else AstKind.INTERPOLATED_STRING
            is ClassConstantReference -> AstKind.CLASS_CONSTANT
            is ClassReference -> AstKind.CLASS_REFERENCE
            is ConstantReference -> AstKind.CONSTANT
            is ArrayCreationExpression -> AstKind.ARRAY_LITERAL
            is ArrayHashElement -> AstKind.ARRAY_ENTRY
            is PhpEchoStatement -> AstKind.ECHO
            is PhpPrintExpression -> AstKind.PRINT
            is Include -> AstKind.INCLUDE
            is PhpReturn -> AstKind.RETURN
            is If -> AstKind.IF
            is ForeachStatement -> AstKind.FOREACH
            is GroupStatement -> AstKind.BLOCK
            is Parameter -> AstKind.PARAMETER
            else -> when {
                psi.node?.elementType == PhpElementTypes.NUMBER -> AstKind.NUMBER_LITERAL
                isListArrayValue(psi) -> AstKind.ARRAY_ENTRY
                else -> AstKind.OTHER
            }
        }

        private fun nameOf(psi: PsiElement, kind: AstKind): String? = when (kind) {
            AstKind.FUNCTION, AstKind.METHOD -> (psi as Function).name
            AstKind.CALL, AstKind.METHOD_CALL, AstKind.STATIC_CALL -> (psi as FunctionReference).name
            AstKind.NEW -> (psi as NewExpression).classReference?.name
            AstKind.VARIABLE -> (psi as Variable).name.takeIf { it.isNotEmpty() }
            AstKind.PROPERTY_ACCESS -> (psi as FieldReference).name
            AstKind.CONSTANT -> (psi as ConstantReference).name
            AstKind.CLASS_CONSTANT -> (psi as ClassConstantReference).name
            AstKind.CLASS_REFERENCE -> (psi as ClassReference).name
            AstKind.PARAMETER -> (psi as Parameter).name
            AstKind.BINARY -> (psi as BinaryExpression).operation?.text
            AstKind.CAST -> (psi as UnaryExpression).operation?.text?.trim('(', ')', ' ', '\t')?.lowercase()
            AstKind.INCLUDE -> psi.firstChild?.text?.lowercase()
            else -> null
        }

        private fun isCast(psi: UnaryExpression): Boolean =
            psi.operation?.node?.elementType?.let { PhpTokenTypes.tsCAST_OPS.contains(it) } == true

        private fun isListArrayValue(psi: PsiElement): Boolean =
            psi.node?.elementType == PhpElementTypes.ARRAY_VALUE && psi.parent is ArrayCreationExpression

        private fun isArrayEntry(psi: PsiElement): Boolean = psi is ArrayHashElement || isListArrayValue(psi)

        /** `ArrayHashElement` key/value may be the ARRAY_KEY / ARRAY_VALUE wrapper; return the expression. */
        private fun unwrapEntryPart(psi: PsiElement?): PsiElement? {
            if (psi == null) return null
            val type = psi.node?.elementType
            return if (type == PhpElementTypes.ARRAY_KEY || type == PhpElementTypes.ARRAY_VALUE) {
                significantChildren(psi).firstOrNull()
            } else {
                psi
            }
        }

        private fun pathOf(file: PsiFile): String {
            val vf = file.viewProvider.virtualFile
            val base = file.project.basePath
            val path = vf.path
            return if (base != null && path.startsWith("$base/")) path.removePrefix("$base/") else path
        }
    }
}
