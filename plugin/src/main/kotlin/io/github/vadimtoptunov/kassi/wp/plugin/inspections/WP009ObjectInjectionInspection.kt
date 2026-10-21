package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-009 (CWE-502). Logic: core `ObjectInjectionCheck`. */
class WP009ObjectInjectionInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-009"
    }
}
