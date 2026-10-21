package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-010 (CWE-918). Logic: core `SsrfCheck`. */
class WP010ServerSideRequestForgeryInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-010"
    }
}
