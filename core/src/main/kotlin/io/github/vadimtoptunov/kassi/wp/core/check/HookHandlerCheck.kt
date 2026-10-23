package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.ancestors
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.Callables
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.hooks.HookRegistration
import io.github.vadimtoptunov.kassi.wp.core.hooks.HookRegistrations
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-001 / WP-002 / WP-003: an AJAX or admin-post handler, registered with `add_action` and
 * resolved in the same file, that calls a listed sink (`params.sinks`) with no listed check
 * (`sanitizers:`) anywhere in its body or in the same-file helpers it calls (one level).
 *
 * Rule params:
 *  - `hook_prefixes`        hooks this rule covers, e.g. `[wp_ajax_, admin_post_]`
 *  - `excluded_prefixes`    hooks that start with a covered prefix but are excluded (`wp_ajax_nopriv_`)
 *  - `skip_if_also_hooked`  skip handlers that are also registered on these hooks (another rule owns them)
 *  - `sinks`                state-changing / privileged calls (CallTarget notation)
 *  - `endpoint_prefixes`    request-endpoint hooks (`wp_ajax_`, `admin_post_`); when set, a handler also
 *                           registered on any other hook (an event reactor such as `after_switch_theme`) is skipped
 *  - `weak_if_called`       calls that make a finding weak rather than absent (e.g. a nonce check for WP-001)
 *  - `low_impact_keys`      regex; a sink call whose literal first or key argument matches it (dismissed
 *                           notices, review prompts, onboarding/tracking flags) makes the finding weak
 *  - `self_scoped_sinks`    sinks that only touch the current user's own data when their first
 *                           argument comes from `current_user_functions` (e.g. update_user_meta(get_current_user_id(), ...))
 */
class HookHandlerCheck(
    private val rule: Rule,
    private val message: (handler: AstNode, hook: String, sink: String, weak: Boolean) -> String,
) : FileCheck {
    private val registrars = rule.source.map { it.lowercase() }.toSet()
    private val prefixes = rule.params.strings("hook_prefixes")
    private val excluded = rule.params.strings("excluded_prefixes")
    private val skipIfAlso = rule.params.strings("skip_if_also_hooked")
    private val sinks = CallTarget.parseAll(rule.params.strings("sinks"))
    private val checks = CallTarget.parseAll(rule.sanitizers)
    private val selfScoped = CallTarget.parseAll(rule.params.strings("self_scoped_sinks"))
    private val currentUser = rule.params.strings("current_user_functions").map { it.lowercase() }.toSet()
    private val endpointPrefixes = rule.params.strings("endpoint_prefixes")
    private val weakIfCalled = CallTarget.parseAll(rule.params.strings("weak_if_called"))
    private val lowImpact = rule.params.strings("low_impact_keys").firstOrNull()?.let { Regex(it, RegexOption.IGNORE_CASE) }

    init {
        require(prefixes.isNotEmpty() && sinks.isNotEmpty() && checks.isNotEmpty()) {
            "${rule.id}: hook_prefixes, sinks and sanitizers are required"
        }
    }

    private fun covered(hook: String) = prefixes.any { hook.startsWith(it) } && excluded.none { hook.startsWith(it) }

    override fun checkFile(file: AstNode): List<Finding> {
        val callables = Callables(file)
        val registrations = HookRegistrations.of(file, registrars, callables)
        val byHandler = registrations.filter { it.handler != null }.groupBy { it.handler!! }
        return byHandler.mapNotNull { (handler, regs) ->
            val hit = regs.firstOrNull { covered(it.hook) } ?: return@mapNotNull null
            if (regs.any { r -> skipIfAlso.any { r.hook.startsWith(it) } }) return@mapNotNull null
            if (endpointPrefixes.isNotEmpty() && regs.any { r -> endpointPrefixes.none { r.hook.startsWith(it) } }) {
                return@mapNotNull null
            }
            if (regs.all { r -> registeredUnderCheck(r.call) }) return@mapNotNull null
            check(handler, hit, callables)
        }
    }

    /**
     * The registration is gated by a dominating check: `add_action` inside `if (<check>)`, or an earlier
     * `if (!<check>) { return/die/... }` in the registering function. Checks in unrelated branches don't count.
     */
    private fun registeredUnderCheck(call: AstNode): Boolean {
        val underIf = call.ancestors().takeWhile { !it.kind.isFunctionLike || it.kind == AstKind.CLOSURE }
            .filter { it.kind == AstKind.IF }
            .any { iff -> iff.child(AstRole.CONDITION)?.walk()?.any { c -> checks.any { it.matches(c) } } == true }
        if (underIf) return true
        val registrar = call.ancestors().firstOrNull { it.kind == AstKind.FUNCTION || it.kind == AstKind.METHOD } ?: return false
        return registrar.walk()
            .takeWhile { it != call }
            .filter { it.kind == AstKind.IF && it.location.startOffset < call.location.startOffset }
            .any { iff -> negatedCheck(iff.child(AstRole.CONDITION)) && exits(iff.child(AstRole.BODY)) }
    }

    /** The condition contains `!<check>(...)`. */
    private fun negatedCheck(cond: AstNode?): Boolean = cond?.walk()?.any { n ->
        n.kind == AstKind.OTHER && n.text.startsWith("!") && n.walk().any { c -> checks.any { it.matches(c) } }
    } == true

    private fun exits(body: AstNode?): Boolean {
        val last = (if (body?.kind == AstKind.BLOCK) body.children.lastOrNull() else body) ?: return false
        val t = last.text.trimStart().lowercase()
        return last.kind == AstKind.RETURN || EXIT_PREFIXES.any { t.startsWith(it) }
    }

    private companion object {
        val EXIT_PREFIXES = listOf("return", "exit", "die", "wp_die", "wp_send_json_error", "wp_send_json")
    }

    private fun check(handler: AstNode, reg: HookRegistration, callables: Callables): Finding? {
        val facts = HandlerFacts(handler, callables)
        if (facts.anyMatch(checks)) return null
        val sink = facts.firstMatch(sinks) { call -> isSelfScoped(call, handler) } ?: return null
        val label = sinks.first { it.matches(sink) }.label
        val weak = (weakIfCalled.isNotEmpty() && facts.anyMatch(weakIfCalled)) || lowImpactOnly(facts, handler)
        return Finding(rule.id, handler, message(handler, reg.hook, label, weak), weak = weak)
    }

    /** Every sink the handler reaches writes a low-impact key (e.g. `update_option('x_notice_dismissed', 1)`). */
    private fun lowImpactOnly(facts: HandlerFacts, handler: AstNode): Boolean {
        val re = lowImpact ?: return false
        val hits = facts.calls.filter { c -> sinks.any { it.matches(c) } && !isSelfScoped(c, handler) }
        return hits.isNotEmpty() && hits.all { c ->
            c.arguments.take(2).any { a -> a.kind == AstKind.STRING_LITERAL && a.stringValue?.let(re::containsMatchIn) == true }
        }
    }

    /** `update_user_meta(get_current_user_id(), ...)`, directly or via a variable only ever assigned that. */
    private fun isSelfScoped(call: AstNode, handler: AstNode): Boolean {
        if (selfScoped.none { it.matches(call) }) return false
        val user = call.arguments.firstOrNull() ?: return false
        if (isCurrentUserCall(user)) return true
        if (user.kind != AstKind.VARIABLE || user.name == null) return false
        val writes = handler.walk().filter { it.kind == AstKind.ASSIGNMENT && it.child(AstRole.TARGET)?.let { t -> t.kind == AstKind.VARIABLE && t.name == user.name } == true }.toList()
        return writes.isNotEmpty() && writes.all { w -> w.child(AstRole.VALUE)?.let(::isCurrentUserCall) == true }
    }

    private fun isCurrentUserCall(n: AstNode) = n.kind == AstKind.CALL && n.name?.lowercase() in currentUser

    object Labels {
        fun handlerLabel(handler: AstNode): String = handler.name?.let { "'$it'" } ?: "closure"
    }
}
