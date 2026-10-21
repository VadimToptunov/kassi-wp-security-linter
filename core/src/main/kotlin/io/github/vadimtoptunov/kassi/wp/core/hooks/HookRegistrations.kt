package io.github.vadimtoptunov.kassi.wp.core.hooks

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.walk

/**
 * `add_action( <hook>, <callback> )` in one file.
 *
 * @property hook the statically known part of the hook name: the full literal, or the literal prefix
 *   of a concatenation/interpolation (`'wp_ajax_' . $action` → `wp_ajax_`).
 * @property handler the callback resolved in the same file, or null.
 */
data class HookRegistration(val call: AstNode, val hook: String, val callback: AstNode, val handler: AstNode?)

object HookRegistrations {
    fun of(file: AstNode, registrars: Set<String>, callables: Callables): List<HookRegistration> =
        file.walk().filter { it.kind == AstKind.CALL && it.name?.lowercase() in registrars }.mapNotNull { call ->
            val hook = call.arguments.getOrNull(0)?.let(::staticPrefix)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val callback = call.arguments.getOrNull(1) ?: return@mapNotNull null
            HookRegistration(call, hook, callback, callables.resolve(callback, call))
        }.toList()

    /** The literal text a hook-name expression starts with. */
    fun staticPrefix(n: AstNode): String = when (n.kind) {
        AstKind.STRING_LITERAL -> n.stringValue.orEmpty()
        AstKind.INTERPOLATED_STRING -> n.stringValue.orEmpty().takeWhile { it != '$' && it != '{' }
        AstKind.CONCAT -> n.child(AstRole.LEFT)?.let { left ->
            val p = staticPrefix(left)
            // Only a fully literal left side lets the prefix continue into the right side.
            if (left.kind == AstKind.STRING_LITERAL) p + (n.child(AstRole.RIGHT)?.let(::staticPrefix) ?: "") else p
        }.orEmpty()
        else -> ""
    }
}
