package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-018: request input reaches the argument of PHP `header()`, so an attacker who can place a CR/LF in
 * that value injects extra HTTP response headers — HTTP response splitting / header injection (CWE-113).
 *
 * Params: `sink_args` maps function → the comma-separated header argument indexes (`header: 0`). The
 * clean paths are `sanitize_text_field()` / `sanitize_key()` / an `(int)` cast (all drop line breaks)
 * and the non-request values the taint engine already treats as safe. `esc_url_raw()`, `esc_html()` and
 * `str_replace()` do NOT, on their own, remove an interior CR/LF and so pass the taint through.
 */
class HttpHeaderInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL) sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
        else emptyList()

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) reaches ${sink.name}() without CR/LF removal (HTTP response header injection)"
}
