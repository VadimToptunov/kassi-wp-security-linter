package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-010: request input reaches the URL of an outbound HTTP request (params `sink_functions`:
 * wp_remote_get/post/request/head, download_url). `wp_safe_remote_*` are the fix and are not sinks.
 */
class SsrfCheck(rule: Rule) : TaintCheck(rule) {
    private val sinks = rule.params.strings("sink_functions").map { it.lowercase() }.toSet()
    private val queryArgFunctions = rule.params.strings("query_arg_functions").map { it.lowercase() }.toSet()

    /** add_query_arg()/remove_query_arg() keep the base URL's host: only the base argument's taint counts. */
    override fun callOverride(call: AstNode, eval: (AstNode) -> Taint?): TaintAnalyzer.Override? {
        val name = call.name?.lowercase() ?: return null
        if (name !in queryArgFunctions) return null
        val args = call.arguments
        val url = when {
            name == "remove_query_arg" -> args.getOrNull(1)
            args.firstOrNull()?.kind == AstKind.ARRAY_LITERAL -> args.getOrNull(1)
            else -> args.getOrNull(2)
        }
        return TaintAnalyzer.Override(url?.let(eval))
    }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL && node.name?.lowercase() in sinks) listOfNotNull(node.arguments.firstOrNull()) else emptyList()

    override fun message(sink: AstNode, taint: Taint): String =
        "${sink.name}() to a URL from request data (${taint.label}): server-side request forgery. " +
            "Use wp_safe_remote_*() or validate the URL with wp_http_validate_url()"
}
