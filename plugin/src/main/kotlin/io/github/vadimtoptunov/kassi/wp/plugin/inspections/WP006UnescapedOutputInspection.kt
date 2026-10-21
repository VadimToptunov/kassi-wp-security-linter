package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-006 (CWE-79): request data echoed without escaping. Logic: core `UnescapedOutputCheck`. */
class WP006UnescapedOutputInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-006"
    }
}
