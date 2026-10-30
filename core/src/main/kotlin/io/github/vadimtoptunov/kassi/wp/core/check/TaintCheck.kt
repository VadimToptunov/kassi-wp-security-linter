package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
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

    /**
     * `params.weak_if_guarded` (bool) + `params.guard_functions`: when a sink argument's own variable is
     * also passed as the first argument to an allow-list guard (`in_array($role, $allowed)`) somewhere in
     * the same scope, the value is validated against an allow-list even though the intra-procedural flow
     * couldn't connect the guard to the sink (a separate `if`-chain, a compound `&&` condition, a fallback
     * elsewhere). The finding is kept but weak: likely intentional, low confidence. WP-012 (privilege
     * escalation) opts in — the dominant false positive there is a role checked against an admin-defined
     * allow-list before `set_role`/`add_role`.
     */
    private val weakIfGuarded by lazy { rule.params.bool("weak_if_guarded") }
    private val guardFunctions by lazy { rule.params.strings("guard_functions").map { it.lowercase() }.toSet() }

    /** First-argument variable names passed to a guard call anywhere in [scope]. */
    private fun guardedVariables(scope: AstNode): Set<String> =
        HandlerFacts.callsIn(scope)
            .filter { it.kind == AstKind.CALL && it.name?.lowercase() in guardFunctions }
            .mapNotNull { it.arguments.firstOrNull() }
            .flatMap { arg -> arg.walk().filter { it.kind == AstKind.VARIABLE }.mapNotNull { it.name } }
            .toSet()

    /** Variable names referenced inside a sink argument expression. */
    private fun argumentVariables(arg: AstNode): Set<String> =
        arg.walk().filter { it.kind == AstKind.VARIABLE }.mapNotNull { it.name }.toSet()

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
            val guardedVars = if (weakIfGuarded && guardFunctions.isNotEmpty()) guardedVariables(scope) else emptySet()
            analyzer.analyze(scope, ::sinkArguments).map { hit ->
                val guarded = guardedVars.isNotEmpty() && argumentVariables(hit.argument).any { it in guardedVars }
                val suffix = when {
                    gated -> ". Weak: the function checks a capability, so only privileged users reach it"
                    nonced -> ". Weak: the function verifies a nonce, so a crafted link alone cannot trigger it"
                    guarded -> ". Weak: the value is checked against an allow-list in this function, so this is likely intentional"
                    else -> ""
                }
                Finding(rule.id, hit.argument, message(hit.sink, hit.taint) + suffix, weak = gated || nonced || guarded, quickFix = quickFix(hit.sink))
            }
        }
}
