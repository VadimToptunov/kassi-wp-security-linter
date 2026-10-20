package io.github.vadimtoptunov.kassi.wp.core.ast

/** Pre-order walk of this node and all its descendants. */
fun AstNode.walk(): Sequence<AstNode> = sequence {
    val stack = ArrayDeque<AstNode>()
    stack.addLast(this@walk)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        yield(node)
        for (i in node.children.indices.reversed()) stack.addLast(node.children[i])
    }
}

fun AstNode.descendantsOfKind(kind: AstKind): Sequence<AstNode> = walk().drop(1).filter { it.kind == kind }

fun AstNode.ancestors(): Sequence<AstNode> = generateSequence(parent) { it.parent }

/** The nearest enclosing function, method or closure, or null at file level. */
fun AstNode.enclosingFunction(): AstNode? = ancestors().firstOrNull { it.kind.isFunctionLike }

/** A plain function call to [name] (case-insensitive, as PHP function names are). */
fun AstNode.isCallTo(name: String): Boolean = kind == AstKind.CALL && this.name.equals(name, ignoreCase = true)
