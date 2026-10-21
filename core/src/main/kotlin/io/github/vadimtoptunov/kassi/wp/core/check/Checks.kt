package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.kb.KnowledgeBase
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** One rule's check over one parsed file. Parser-neutral: any [AstNode] adapter works. */
fun interface FileCheck {
    fun checkFile(file: AstNode): List<Finding>
}

/**
 * Registry: rule id → check implementation. This is the single entry point for running the engine
 * without an IDE. Load a [KnowledgeBase], build the checks with [forRules], and call
 * [FileCheck.checkFile] per file (or call [run]).
 *
 * A rule whose id has no implementation here is reported by [unimplemented], so a newer rule base
 * never fails silently on an older engine.
 */
object Checks {
    private val factories: Map<String, (Rule) -> FileCheck> = mapOf(
        "WP-004" to { rule -> RestRouteCheck(rule).let { c -> FileCheck { f -> f.walk().filter { it.kind == AstKind.CALL }.flatMap(c::check).toList() } } },
        "WP-005" to ::SqlInjectionCheck,
        "WP-006" to ::UnescapedOutputCheck,
        "WP-007" to ::UnsafePathCheck,
        "WP-008" to ::UnsafeRedirectCheck,
        "WP-009" to ::ObjectInjectionCheck,
        "WP-010" to ::SsrfCheck,
        "WP-011" to ::UnsafeUploadCheck,
        "WP-012" to ::PrivilegeEscalationCheck,
        "WP-013" to ::ShortcodeOutputCheck,
        "WP-001" to { rule ->
            HookHandlerCheck(rule) { h, hook, sink, weak ->
                "Handler ${HookHandlerCheck.handlerLabel(h)} (hook $hook) calls $sink() without a capability check (current_user_can)" +
                    if (weak) ". It verifies a nonce, so it is exploitable only if that nonce reaches lower-privileged users" else ""
            }
        },
        "WP-002" to { rule ->
            HookHandlerCheck(rule) { h, hook, sink, _ ->
                "Handler ${HookHandlerCheck.handlerLabel(h)} (hook $hook) calls $sink() without nonce verification " +
                    "(check_ajax_referer, check_admin_referer, wp_verify_nonce)"
            }
        },
        "WP-003" to { rule ->
            HookHandlerCheck(rule) { h, hook, sink, _ ->
                "Unauthenticated handler ${HookHandlerCheck.handlerLabel(h)} (hook $hook) calls $sink() with no capability check: anyone can trigger it"
            }
        },
    )

    val implementedRuleIds: Set<String> get() = factories.keys

    fun forRule(rule: Rule): FileCheck? = factories[rule.id]?.invoke(rule)

    /** Checks for every rule in [kb] that this engine implements. */
    fun forRules(kb: KnowledgeBase): Map<Rule, FileCheck> =
        kb.rules.mapNotNull { rule -> forRule(rule)?.let { rule to it } }.toMap()

    fun unimplemented(kb: KnowledgeBase): List<String> = kb.rules.map { it.id }.filterNot { it in factories }

    /** Runs every implemented rule of [kb] over [file]. */
    fun run(kb: KnowledgeBase, file: AstNode): List<Finding> = forRules(kb).values.flatMap { it.checkFile(file) }
}
