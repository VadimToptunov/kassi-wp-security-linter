package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import io.github.vadimtoptunov.kassi.wp.core.check.UnescapedOutputCheck
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** WP-006 (CWE-79): request data echoed without escaping. Logic: [UnescapedOutputCheck]. */
class WP006UnescapedOutputInspection : TaintInspection(RULE_ID) {
    override fun createCheck(rule: Rule) = UnescapedOutputCheck(rule)

    companion object {
        const val RULE_ID = "WP-006"
    }
}
