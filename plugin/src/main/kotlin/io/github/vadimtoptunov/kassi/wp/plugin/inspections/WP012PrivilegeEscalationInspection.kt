package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-012 (CWE-269). Logic: core `PrivilegeEscalationCheck`. */
class WP012PrivilegeEscalationInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-012"
    }
}
