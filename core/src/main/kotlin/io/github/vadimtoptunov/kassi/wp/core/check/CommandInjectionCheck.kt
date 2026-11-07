package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-015: request input reaches the command argument of a shell-executing function
 * (`exec`, `system`, `shell_exec`, `passthru`, `popen`, `proc_open`) without
 * `escapeshellarg()` / `escapeshellcmd()`.
 *
 * Params: `sink_args` maps function → comma-separated command argument indexes (`popen: 0`).
 */
class CommandInjectionCheck(rule: Rule) : TaintCheck(rule) {
    private val sinkArgs: Map<String, List<Int>> = rule.params.stringMap("sink_args")
        .mapKeys { it.key.lowercase() }
        .mapValues { (_, v) -> v.split(',').map { it.trim().toInt() } }

    override fun sinkArguments(node: AstNode): List<AstNode> =
        if (node.kind == AstKind.CALL) sinkArgs[node.name?.lowercase()]?.mapNotNull { node.arguments.getOrNull(it) }.orEmpty()
        else emptyList()

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) reaches ${sink.name}() as an OS command without escapeshellarg()/escapeshellcmd()"
}
