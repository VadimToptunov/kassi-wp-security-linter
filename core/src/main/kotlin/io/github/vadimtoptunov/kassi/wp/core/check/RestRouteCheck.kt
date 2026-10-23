package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.ancestors
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.Callables
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-004: a REST route without a real `permission_callback`.
 *
 * Missing callback: warning on write routes, weak on read-only ones. Always-true callback on a write
 * route: weak, or a warning when the same-file handler itself calls a privileged sink. Silent when the
 * endpoint shape is not statically known.
 *
 * Rule params:
 *  - `read_only_methods`        HTTP methods that cannot change state, e.g. `[GET, HEAD]`
 *  - `default_methods`          methods WordPress assumes when `methods` is omitted
 *  - `method_constants`         `Class::CONST` → comma-separated methods
 *  - `open_permission_callbacks` callback names that always allow access
 *  - `privileged_sinks` / `capability_checks` escalate an always-true callback from weak to a warning
 *    when the route handler (same file) itself calls a privileged sink with no capability check
 */
class RestRouteCheck(private val rule: Rule) {
    private val registrars = rule.source.map { it.lowercase() }.toSet()
    private val readOnly = rule.params.strings("read_only_methods").map { it.uppercase() }.toSet()
    private val defaultMethods = rule.params.strings("default_methods").map { it.uppercase() }.toSet()
    private val methodConstants = rule.params.stringMap("method_constants")
        .mapKeys { it.key.lowercase() }.mapValues { splitMethods(it.value) }
    private val openCallbacks = rule.params.strings("open_permission_callbacks").map { it.lowercase() }.toSet()
    private val privilegedSinks = CallTarget.parseAll(rule.params.strings("privileged_sinks"))
    private val capabilityChecks = CallTarget.parseAll(rule.params.strings("capability_checks"))

    init {
        require(readOnly.isNotEmpty() && defaultMethods.isNotEmpty()) {
            "${rule.id}: params.read_only_methods and params.default_methods are required"
        }
    }

    fun check(call: AstNode): List<Finding> {
        if (call.kind != AstKind.CALL || call.name?.lowercase() !in registrars) return emptyList()
        val options = call.arguments.getOrNull(2) ?: return emptyList()
        if (options.kind != AstKind.ARRAY_LITERAL) return emptyList()

        val multi = endpointsOf(options)
        return if (multi != null) {
            multi.mapNotNull { checkEndpoint(it, reportOn = it) }
        } else {
            listOfNotNull(checkEndpoint(options, reportOn = call))
        }
    }

    /**
     * `register_rest_route($ns, $route, [ [..GET..], [..POST..], 'schema' => ... ])`: the list-style
     * entries that are arrays are separate endpoints. Returns null for the single-endpoint form.
     */
    private fun endpointsOf(options: AstNode): List<AstNode>? {
        val listValues = options.arguments
            .filter { it.child(AstRole.KEY).let { k -> k == null || k.kind == AstKind.NUMBER_LITERAL } }
            .map { it.child(AstRole.VALUE) }
        if (listValues.isEmpty() || listValues.any { it?.kind != AstKind.ARRAY_LITERAL }) return null
        return listValues.filterNotNull()
    }

    private fun checkEndpoint(endpoint: AstNode, reportOn: AstNode): Finding? {
        val entries = mutableMapOf<String, AstNode?>()
        for (entry in endpoint.arguments) {
            // Unkeyed entry (spread, list value) or computed key: content unknown → not proven.
            val key = entry.child(AstRole.KEY)?.takeIf { it.kind == AstKind.STRING_LITERAL }?.stringValue
                ?: return null
            entries[key.lowercase()] = entry.child(AstRole.VALUE)
        }

        val methods = if ("methods" in entries) entries["methods"]?.let(::resolveMethods) else defaultMethods
        val readOnlyRoute = methods != null && methods.isNotEmpty() && readOnly.containsAll(methods)
        val callback = entries["permission_callback"]

        if (callback == null || (callback.kind == AstKind.CONSTANT && callback.name.equals("null", ignoreCase = true))) {
            return if (readOnlyRoute) {
                Finding(
                    rule.id, reportOn,
                    "REST route has no permission_callback. Declare it explicitly " +
                        "('__return_true' if this read-only route is meant to be public)",
                    weak = true,
                    quickFix = rule.plugin?.quickFix,
                )
            } else {
                Finding(
                    rule.id, reportOn,
                    "REST route${methodsLabel(methods)} has no permission_callback: anyone, " +
                        "including unauthenticated visitors, can call it",
                )
            }
        }

        if (methods == null || readOnlyRoute || !isAlwaysTrue(callback)) return null
        // Escalate when the same-file handler calls a privileged sink without a capability check.
        privilegedSinkOf(entries["callback"], reportOn)?.let { sink ->
            return Finding(
                rule.id, callback,
                "permission_callback always allows access on a state-changing REST route${methodsLabel(methods)} " +
                    "whose handler calls $sink() without a capability check: anyone can call it",
            )
        }
        // Otherwise weak: intentionally public write routes are declared the same way.
        return Finding(
            rule.id, callback,
            "permission_callback always allows access on a state-changing REST route${methodsLabel(methods)}. " +
                "Unless the handler authenticates the request itself, anyone can call it",
            weak = true,
        )
    }

    private fun privilegedSinkOf(handlerRef: AstNode?, site: AstNode): String? {
        if (handlerRef == null || privilegedSinks.isEmpty()) return null
        val file = site.ancestors().lastOrNull() ?: return null
        val callables = Callables(file)
        val handler = callables.resolve(handlerRef, site) ?: return null
        val facts = HandlerFacts(handler, callables)
        if (facts.anyMatch(capabilityChecks)) return null
        // Only sinks in the handler's own body escalate (secret-authenticated routes write via helpers).
        val sink = facts.ownCalls.firstOrNull { c -> privilegedSinks.any { it.matches(c) } } ?: return null
        return privilegedSinks.first { it.matches(sink) }.label
    }

    private fun methodsLabel(methods: Set<String>?): String =
        if (methods.isNullOrEmpty()) "" else " (" + methods.sorted().joinToString(", ") + ")"

    /** Null when the value is not statically known. */
    private fun resolveMethods(value: AstNode): Set<String>? = when (value.kind) {
        AstKind.STRING_LITERAL -> value.stringValue?.let(::splitMethods)
        AstKind.CLASS_CONSTANT -> {
            val cls = value.child(AstRole.RECEIVER)?.takeIf { it.kind == AstKind.CLASS_REFERENCE }?.name
            if (cls == null || value.name == null) null else methodConstants["$cls::${value.name}".lowercase()]
        }
        AstKind.ARRAY_LITERAL -> {
            val parts = value.arguments.map { it.child(AstRole.VALUE)?.let(::resolveMethods) }
            if (parts.any { it == null }) null else parts.flatMap { it!! }.toSet()
        }
        else -> null
    }

    private fun isAlwaysTrue(callback: AstNode): Boolean = when (callback.kind) {
        AstKind.STRING_LITERAL -> callback.stringValue?.trimStart('\\')?.lowercase() in openCallbacks
        AstKind.CLOSURE -> {
            val body = callback.child(AstRole.BODY)
            when {
                body == null -> false
                body.kind == AstKind.BLOCK -> body.children.singleOrNull()
                    ?.takeIf { it.kind == AstKind.RETURN }
                    ?.children?.singleOrNull()
                    ?.let(::isTrueLiteral) == true
                else -> isTrueLiteral(body) // arrow function
            }
        }
        else -> false
    }

    private fun isTrueLiteral(node: AstNode) = node.kind == AstKind.CONSTANT && node.name.equals("true", ignoreCase = true)

    private fun splitMethods(s: String): Set<String> =
        s.split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()
}
