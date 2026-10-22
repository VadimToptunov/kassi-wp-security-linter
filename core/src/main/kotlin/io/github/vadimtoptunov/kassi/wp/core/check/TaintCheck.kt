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

    /**
     * `params.nonce_checks`: for rules whose attack needs a victim to open a crafted request (reflected
     * XSS, open redirect), a nonce verified in the same function means the attacker cannot craft a
     * working link, so the finding is weak.
     */
    private val nonceChecks by lazy { CallTarget.parseAll(rule.params.strings("nonce_checks")) }

    /** Argument nodes of [node] that are sinks for this rule; empty when [node] is not a sink. */
    protected abstract fun sinkArguments(node: AstNode): List<AstNode>

    protected abstract fun message(sink: AstNode, taint: Taint): String

    protected open fun quickFix(sink: AstNode): String? = null

    /** Analyses every scope of a file. */
    override fun checkFile(file: AstNode): List<Finding> =
        analyzer.scopes(file).flatMap { scope ->
            val calls = HandlerFacts.callsIn(scope)
            val gated = adminChecks.isNotEmpty() && calls.any { c -> adminChecks.any { it.matches(c) } }
            val nonced = nonceChecks.isNotEmpty() && calls.any { c -> nonceChecks.any { it.matches(c) } }
            analyzer.analyze(scope, ::sinkArguments).map { hit ->
                val suffix = when {
                    gated -> ". Weak: the function checks a capability, so only privileged users reach it"
                    nonced -> ". Weak: the function verifies a nonce, so a crafted link alone cannot trigger it"
                    else -> ""
                }
                Finding(rule.id, hit.argument, message(hit.sink, hit.taint) + suffix, weak = gated || nonced, quickFix = quickFix(hit.sink))
            }
        }
}
