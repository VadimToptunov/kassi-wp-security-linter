package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-016: request input reaches the code argument of `eval()` / `assert()`, or the body argument of
 * `create_function()`, so attacker input is executed as PHP (CWE-94).
 *
 * Params: `sink_args` maps function → comma-separated code argument indexes (`create_function: 1`).
 * There is no sanitiser for this rule; the clean paths are a numeric cast, an allow-list guard, or a
 * non-request value (all handled by the taint engine).
 */
class CodeInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL) sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
        else emptyList()

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) is evaluated as PHP code by ${sink.name}()"
}
