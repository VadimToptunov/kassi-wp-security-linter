package io.github.vadimtoptunov.kassi.wp.core.pop

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.enclosingFunction
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.Callables
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.hooks.HookRegistrations

/**
 * Classifies how exposed a sink is, reusing the same hook / REST / capability analysis the other
 * rules use. A sink's enclosing function is tied back to its registration:
 *
 *  - an `add_action`/`add_filter` on a `*nopriv*` hook → [Reachability.UNAUTH];
 *  - a `wp_ajax_` / `admin_post_` (logged-in) hook → [Reachability.LOW_PRIV];
 *  - a REST route whose `permission_callback` always allows access → [Reachability.UNAUTH], a real
 *    one → [Reachability.ADMIN];
 *  - a capability check (`current_user_can`, ...) anywhere in the handler → [Reachability.ADMIN]
 *    (only privileged users reach the sink), which overrides a nopriv/ajax classification;
 *  - nothing proven → [Reachability.UNKNOWN].
 *
 * All the function/hook/callback names come from the rule's `params`, so the policy stays data.
 */
class ReachabilityClassifier(
    hookRegistrars: List<String>,
    restRegistrars: List<String>,
    noprivMarkers: List<String>,
    private val lowPrivPrefixes: List<String>,
    capabilityChecks: List<String>,
    openPermissionCallbacks: List<String>,
) {
    private val hookRegistrars = hookRegistrars.map { it.lowercase() }.toSet()
    private val restRegistrars = restRegistrars.map { it.lowercase() }.toSet()
    private val noprivMarkers = noprivMarkers.map { it.lowercase() }
    private val capabilityChecks = CallTarget.parseAll(capabilityChecks)
    private val openCallbacks = openPermissionCallbacks.map { it.lowercase() }.toSet()

    fun classify(file: AstNode, sink: AstNode): Reachability {
        val scope = sink.enclosingFunction() ?: return Reachability.UNKNOWN
        val callables = Callables(file)

        if (capabilityGated(scope, callables)) return Reachability.ADMIN

        val hook = hookReachability(file, callables, scope)
        val rest = restReachability(file, callables, scope)
        // Most-exposed wins across the ways the same function can be registered.
        return listOfNotNull(hook, rest).minByOrNull { it.rank } ?: Reachability.UNKNOWN
    }

    private fun capabilityGated(scope: AstNode, callables: Callables): Boolean =
        capabilityChecks.isNotEmpty() && HandlerFacts(scope, callables).anyMatch(capabilityChecks)

    private fun hookReachability(file: AstNode, callables: Callables, scope: AstNode): Reachability? {
        val regs = HookRegistrations.of(file, hookRegistrars, callables)
            .filter { it.handler != null && handlerContains(it.handler!!, scope) }
        if (regs.isEmpty()) return null
        val hooks = regs.map { it.hook.lowercase() }
        return when {
            hooks.any { h -> noprivMarkers.any { h.contains(it) } } -> Reachability.UNAUTH
            hooks.any { h -> lowPrivPrefixes.any { h.startsWith(it) } } -> Reachability.LOW_PRIV
            else -> Reachability.UNKNOWN
        }
    }

    private fun restReachability(file: AstNode, callables: Callables, scope: AstNode): Reachability? {
        val routes = file.walk().filter { it.kind == AstKind.CALL && it.name?.lowercase() in restRegistrars }.toList()
        var result: Reachability? = null
        for (route in routes) {
            val options = route.arguments.getOrNull(2)?.takeIf { it.kind == AstKind.ARRAY_LITERAL } ?: continue
            val handlerRef = entryValue(options, "callback") ?: continue
            val handler = callables.resolve(handlerRef, route) ?: continue
            if (!handlerContains(handler, scope)) continue
            val callback = entryValue(options, "permission_callback")
            val r = if (callback == null || isOpen(callback)) Reachability.UNAUTH else Reachability.ADMIN
            if (result == null || r.rank < result!!.rank) result = r
        }
        return result
    }

    private fun entryValue(options: AstNode, key: String): AstNode? = options.arguments
        .firstOrNull { it.child(AstRole.KEY)?.stringValue.equals(key, ignoreCase = true) }
        ?.child(AstRole.VALUE)

    private fun isOpen(callback: AstNode): Boolean = when (callback.kind) {
        AstKind.STRING_LITERAL -> callback.stringValue?.trimStart('\\')?.lowercase() in openCallbacks
        AstKind.CONSTANT -> callback.name.equals("null", ignoreCase = true)
        else -> false
    }

    /** The registered handler is, or textually contains, the sink's enclosing function. */
    private fun handlerContains(handler: AstNode, scope: AstNode): Boolean =
        handler == scope || handler.walk().any { it == scope }
}
