package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-007 (CWE-22): request data in a file path. Logic: core `UnsafePathCheck`. */
class WP007UnsafeFilePathInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-007"
    }
}
