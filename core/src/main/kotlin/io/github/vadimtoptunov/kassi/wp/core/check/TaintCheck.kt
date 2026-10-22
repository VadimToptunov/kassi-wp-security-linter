package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * Base for the "input → sink" rules (WP-005, WP-006, WP-008): the rule supplies which node arguments
 * are sinks and how to word the finding; [TaintAnalyzer] proves the flow.
 */
abstract class TaintCheck(protected val rule: Rule) : FileCheck {
    protected open fun callOverride(call: AstNode, eval: (AstNode) -> Taint?): TaintAnalyzer.Override? = null

    private val analyzer by lazy { TaintAnalyzer(TaintSpec.of(rule), ::callOverride) }

    /**
     * `params.admin_checks`: when the scope itself calls one of these (current_user_can, ...), only a
     * privileged user can reach the sink, so the finding is weak. Rules whose victim is the privileged
     * user (reflected XSS, open redirect) leave this empty.
     */
    private val adminChecks by lazy { CallTarget.parseAll(rule.params.strings("admin_checks")) }

    /** Argument nodes of [node] that are sinks for this rule; empty when [node] is not a sink. */
    protected abstract fun sinkArguments(node: AstNode): List<AstNode>

    protected abstract fun message(sink: AstNode, taint: Taint): String

    protected open fun quickFix(sink: AstNode): String? = null

    /** Analyses every scope of a file. */
    override fun checkFile(file: AstNode): List<Finding> =
        analyzer.scopes(file).flatMap { scope ->
            val gated = adminChecks.isNotEmpty() && HandlerFacts.callsIn(scope).any { c -> adminChecks.any { it.matches(c) } }
            analyzer.analyze(scope, ::sinkArguments).map { hit ->
                val msg = message(hit.sink, hit.taint) + if (gated) ". Weak: the function checks a capability, so only privileged users reach it" else ""
                Finding(rule.id, hit.argument, msg, weak = gated, quickFix = quickFix(hit.sink))
            }
        }
}
