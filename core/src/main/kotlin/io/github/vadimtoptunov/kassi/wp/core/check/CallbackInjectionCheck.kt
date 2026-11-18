package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-019: request input reaches the callable argument of `call_user_func()` / `call_user_func_array()`,
 * so an attacker chooses which PHP function runs — arbitrary function invocation, often RCE (CWE-94).
 *
 * Params: `sink_args` maps function → the comma-separated callable argument indexes
 * (`call_user_func: 0`, `call_user_func_array: 0`). There is NO string sanitiser for this rule:
 * `sanitize_text_field()` / `sanitize_key()` clean the *string* but do not stop it from naming a
 * dangerous function, so they are passthrough. The only clean paths are an allow-list guard
 * (`in_array` of the callable against known callbacks) or a non-request value — both handled by the
 * taint engine via `guard_functions`.
 */
class CallbackInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL) {
            sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
                .filterNot { hasFixedPrefix(it) }
        } else {
            emptyList()
        }

    /**
     * A callable built as `'fixed_prefix' . $request[. …]` cannot name an arbitrary function — the literal
     * prefix pins it to one namespace (e.g. `'wordfence::ajax_' . $f . '_callback'` can only reach
     * `wordfence::ajax_*` methods). Such a prefixed dispatch is a common safe idiom, so it is not reported
     * (silence over noise). A bare request variable, index or an empty prefix is still a sink.
     */
    private fun hasFixedPrefix(arg: AstNode): Boolean {
        var n: AstNode? = arg
        while (n?.kind == AstKind.CONCAT) n = n.child(AstRole.LEFT)
        return !(n?.takeIf { it.kind == AstKind.STRING_LITERAL }?.stringValue).isNullOrEmpty()
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) is used as the callable in ${sink.name}() without an allow-list (arbitrary function invocation)"
}
