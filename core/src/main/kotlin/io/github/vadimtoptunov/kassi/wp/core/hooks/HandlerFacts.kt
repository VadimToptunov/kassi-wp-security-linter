package io.github.vadimtoptunov.kassi.wp.core.hooks

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole

/**
 * The calls a handler makes: its own body plus one level of same-file helpers it calls
 * (`$this->save()`, `self::check()`, `my_helper()`). Nested closures are not part of the handler.
 */
class HandlerFacts(val handler: AstNode, callables: Callables) {
    val ownCalls: List<AstNode> = callsIn(handler)

    val calls: List<AstNode> = ownCalls + ownCalls.mapNotNull { callables.resolveCall(it) }
        .filter { it != handler }.distinct().flatMap(::callsIn)

    fun firstMatch(targets: List<CallTarget>, exclude: (AstNode) -> Boolean = { false }): AstNode? =
        calls.firstOrNull { call -> !exclude(call) && targets.any { it.matches(call) } }

    fun anyMatch(targets: List<CallTarget>): Boolean = calls.any { call -> targets.any { it.matches(call) } }

    companion object {
        fun callsIn(fn: AstNode): List<AstNode> {
            val out = mutableListOf<AstNode>()
            fun visit(n: AstNode) {
                if (n != fn && n.kind.isFunctionLike) return
                if (n.kind == AstKind.CALL || n.kind == AstKind.METHOD_CALL || n.kind == AstKind.STATIC_CALL) out += n
                n.children.forEach(::visit)
            }
            (fn.child(AstRole.BODY) ?: fn).let(::visit)
            return out
        }
    }
}
