package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-001 (CWE-862 handler changes state without current_user_can()). Logic: core `HookHandlerCheck`. */
class WP001MissingCapabilityCheckInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-001"
    }
}
