package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode

/**
 * One proven problem.
 *
 * @property node     where to report it (consumers decide the exact highlight range).
 * @property weak     true for hygiene findings that are not exploitable on their own
 *                    (reported at a lower level than the rule's severity).
 * @property quickFix the quick-fix id to offer for this finding, or null. Only set where the fix is
 *                    semantically safe (it never changes who can reach the code).
 */
data class Finding(
    val ruleId: String,
    val node: AstNode,
    val message: String,
    val weak: Boolean = false,
    val quickFix: String? = null,
)
