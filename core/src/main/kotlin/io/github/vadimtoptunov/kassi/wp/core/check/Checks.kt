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
        "WP-008" to ::UnsafeRedirectCheck,
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
