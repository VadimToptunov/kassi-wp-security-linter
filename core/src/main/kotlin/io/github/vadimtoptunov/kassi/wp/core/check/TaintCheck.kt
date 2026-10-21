package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * Base for the "input → sink" rules (WP-005, WP-006, WP-008): the rule supplies which node arguments
 * are sinks and how to word the finding; [TaintAnalyzer] proves the flow.
 */
abstract class TaintCheck(protected val rule: Rule) {
    protected open fun callOverride(call: AstNode, eval: (AstNode) -> Taint?): TaintAnalyzer.Override? = null

    private val analyzer by lazy { TaintAnalyzer(TaintSpec.of(rule), ::callOverride) }

    /** Argument nodes of [node] that are sinks for this rule; empty when [node] is not a sink. */
    protected abstract fun sinkArguments(node: AstNode): List<AstNode>

    protected abstract fun message(sink: AstNode, taint: Taint): String

    protected open fun quickFix(sink: AstNode): String? = null

    /** Analyses every scope of a file. */
    fun checkFile(file: AstNode): List<Finding> =
        analyzer.scopes(file).flatMap { scope ->
            analyzer.analyze(scope, ::sinkArguments).map { hit ->
                Finding(rule.id, hit.argument, message(hit.sink, hit.taint), quickFix = quickFix(hit.sink))
            }
        }
}
