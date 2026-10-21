package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-003 (CWE-862 privileged action on a nopriv hook). Logic: core `HookHandlerCheck`. */
class WP003NoprivPrivilegedActionInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-003"
    }
}
