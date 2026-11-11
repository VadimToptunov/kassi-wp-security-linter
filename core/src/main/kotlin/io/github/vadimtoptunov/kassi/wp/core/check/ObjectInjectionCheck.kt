package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-009: request input (including cookies) reaches `unserialize()` / `maybe_unserialize()`
 * (params `sink_functions`). Not a sink when `allowed_classes` admits no attacker-chosen class:
 * `=> false` (no object at all) or a bounded literal list of class-name strings (only those exact
 * classes, so the payload cannot introduce a POP gadget). `=> true` or a dynamic value stays a sink.
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
            e.child(AstRole.KEY)?.stringValue == "allowed_classes" && allowedClassesSafe(e.child(AstRole.VALUE))
        }

    /**
     * `allowed_classes` admits no attacker-chosen class when it is `false` (no objects at all) OR a
     * bounded literal list of class-name strings: only those exact classes can be instantiated, so the
     * payload cannot introduce an arbitrary POP gadget. `true` (every class) or a dynamic value
     * (variable / call) stays a sink.
     */
    private fun allowedClassesSafe(value: AstNode?): Boolean {
        if (value == null) return false
        if (value.kind == AstKind.CONSTANT && value.name.equals("false", ignoreCase = true)) return true
        if (value.kind == AstKind.ARRAY_LITERAL) {
            return value.arguments.all { el ->
                val v = el.child(AstRole.VALUE) ?: el
                v.kind == AstKind.STRING_LITERAL
            }
        }
        return false
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) reaches ${sink.name}(): PHP object injection. Use json_decode(), " +
            "or unserialize() with ['allowed_classes' => false]"
}
