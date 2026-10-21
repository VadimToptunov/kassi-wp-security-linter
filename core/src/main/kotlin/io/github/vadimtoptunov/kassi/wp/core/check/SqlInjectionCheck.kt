package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-005: request input reaches the SQL text of a `$wpdb` query method without `$wpdb->prepare()`
 * placeholders or a YAML-listed sanitizer. `prepare()`'s own first argument is a sink too: input
 * concatenated into the format string is not escaped by it.
 *
 * Params: `receivers` (variable/property names holding wpdb, e.g. `wpdb`), `sink_methods`.
 */
class SqlInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val receivers = rule.params.strings("receivers").map { it.lowercase() }.toSet()
    private val sinkMethods = rule.params.strings("sink_methods").map { it.lowercase() }.toSet()

    override fun sinkArguments(node: AstNode): List<AstNode> {
        if (node.kind != AstKind.METHOD_CALL || node.name?.lowercase() !in sinkMethods) return emptyList()
        val receiver = node.child(AstRole.RECEIVER) ?: return emptyList()
        // $wpdb->query(...) or $this->wpdb->query(...)
        val holder = when (receiver.kind) {
            AstKind.VARIABLE, AstKind.PROPERTY_ACCESS -> receiver.name?.lowercase()
            else -> null
        }
        if (holder !in receivers) return emptyList()
        return listOfNotNull(node.arguments.firstOrNull())
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "SQL built from request data (${taint.label}) reaches \$wpdb->${sink.name}() without " +
            "\$wpdb->prepare() placeholders"
}
