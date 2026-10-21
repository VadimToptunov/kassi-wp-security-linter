package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-006: request input is echoed/printed without a YAML-listed escaping function. v0.1 proves
 * reflected flows only (input → output in one scope); stored values from the database are not tracked.
 */
class UnescapedOutputCheck(rule: Rule) : TaintCheck(rule) {
    override fun sinkArguments(node: AstNode): List<AstNode> = when (node.kind) {
        AstKind.ECHO, AstKind.PRINT -> node.arguments
        else -> emptyList()
    }

    override fun message(sink: AstNode, taint: Taint): String =
        "Request data (${taint.label}) is output without escaping (esc_html, esc_attr, esc_url, wp_kses)"
}
