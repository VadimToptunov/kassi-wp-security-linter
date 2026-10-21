package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-007: request input reaches the path argument of a file-system function or of
 * include/require without `sanitize_file_name()` / `basename()` / a `realpath()` or
 * `validate_file()` check.
 *
 * Params: `sink_args` maps function → comma-separated path argument indexes (`copy: 0,1`);
 * `include_sinks` lists the include/require keywords that are sinks.
 */
class UnsafePathCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }
    private val includes = rule.params.strings("include_sinks").map { it.lowercase() }.toSet()

    override fun sinkArguments(node: AstNode): List<AstNode> = when (node.kind) {
        AstKind.CALL -> sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
        AstKind.INCLUDE -> if (node.name?.lowercase() in includes) node.arguments else emptyList()
        else -> emptyList()
    }

    override fun message(sink: AstNode, taint: Taint): String {
        val what = if (sink.kind == AstKind.INCLUDE) sink.name else "${sink.name}()"
        return "File path built from request data (${taint.label}) reaches $what without sanitize_file_name(), " +
            "basename() or a realpath()/validate_file() check"
    }
}
