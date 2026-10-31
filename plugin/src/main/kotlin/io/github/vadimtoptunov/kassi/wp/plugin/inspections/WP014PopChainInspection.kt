package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-014 (CWE-502). Logic: core `PopChainCheck` — object-injection sink paired with a POP gadget. */
class WP014PopChainInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-014"
    }
}
