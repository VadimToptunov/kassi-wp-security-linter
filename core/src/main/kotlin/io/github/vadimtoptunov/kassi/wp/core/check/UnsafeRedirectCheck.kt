package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-008: `wp_redirect()` (params `sink_functions`) to a URL that request input controls.
 *
 * `add_query_arg()` / `remove_query_arg()` (params `query_arg_functions`) only append to a base URL:
 * the result is tainted only when the base URL argument is. Without one they use the current
 * request URL, which keeps the host.
 */
class UnsafeRedirectCheck(rule: Rule) : TaintCheck(rule) {
    private val sinks = rule.params.strings("sink_functions").map { it.lowercase() }.toSet()
    private val queryArgFunctions = rule.params.strings("query_arg_functions").map { it.lowercase() }.toSet()

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL && node.name?.lowercase() in sinks) listOfNotNull(node.arguments.firstOrNull()) else emptyList()

    override fun callOverride(call: AstNode, eval: (AstNode) -> Taint?): TaintAnalyzer.Override? {
        val name = call.name?.lowercase() ?: return null
        if (name !in queryArgFunctions) return null
        val args = call.arguments
        val url = when {
            name == "remove_query_arg" -> args.getOrNull(1)
            args.firstOrNull()?.kind == AstKind.ARRAY_LITERAL -> args.getOrNull(1) // add_query_arg(array, url)
            else -> args.getOrNull(2) // add_query_arg(key, value, url)
        }
        return TaintAnalyzer.Override(url?.let(eval))
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "${sink.name}() to a URL from request data (${taint.label}): open redirect. Use wp_safe_redirect()"

    override fun quickFix(sink: AstNode): String? = rule.plugin?.quickFix
}
