package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-017: request input reaches the `$headers` argument of `wp_mail()` (or PHP `mail()`), so an
 * attacker who can place a CR/LF in that value injects extra email headers — a `Bcc:`/`Cc:` to an
 * arbitrary recipient, a forged `From:`, and so on (CWE-93, email header / CRLF injection).
 *
 * Params: `sink_args` maps function → the comma-separated header argument indexes (`wp_mail: 3`).
 * The clean paths are `sanitize_text_field()` / `sanitize_email()` (both drop line breaks) and the
 * non-request / numeric-cast values the taint engine already treats as safe. `wp_strip_all_tags()`,
 * `trim()` and `str_replace()` do NOT remove an interior CR/LF and so pass the taint through.
 */
class MailHeaderInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL) sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
        else emptyList()

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) reaches the headers argument of ${sink.name}() without CR/LF removal (email header injection)"
}
