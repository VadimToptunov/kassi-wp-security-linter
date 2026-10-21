package io.github.vadimtoptunov.kassi.wp.plugin.inspections

/** WP-013 (CWE-79). Logic: core `ShortcodeOutputCheck`. */
class WP013ShortcodeAttributeXssInspection : RuleInspection(RULE_ID) {
    companion object {
        const val RULE_ID = "WP-013"
    }
}
