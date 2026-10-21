package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-009: request input (including cookies) reaches `unserialize()` / `maybe_unserialize()`
 * (params `sink_functions`). `unserialize($x, ['allowed_classes' => false])` is not a sink:
 * without classes no object, so no POP chain.
 */
class ObjectInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinks = rule.params.strings("sink_functions").map { it.lowercase() }.toSet()

    override fun sinkArguments(node: AstNode): List<AstNode> {
        if (node.kind != AstKind.CALL || node.name?.lowercase() !in sinks) return emptyList()
        if (disallowsClasses(node.arguments.getOrNull(1))) return emptyList()
        return listOfNotNull(node.arguments.firstOrNull())
    }

    private fun disallowsClasses(options: AstNode?): Boolean =
        options?.kind == AstKind.ARRAY_LITERAL && options.arguments.any { e ->
            e.child(AstRole.KEY)?.stringValue == "allowed_classes" &&
                e.child(AstRole.VALUE)?.let { it.kind == AstKind.CONSTANT && it.name.equals("false", ignoreCase = true) } == true
        }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) reaches ${sink.name}(): PHP object injection. Use json_decode(), " +
            "or unserialize() with ['allowed_classes' => false]"
}
