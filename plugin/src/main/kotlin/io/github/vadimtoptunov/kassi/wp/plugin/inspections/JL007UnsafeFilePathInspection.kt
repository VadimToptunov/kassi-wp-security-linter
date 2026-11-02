package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** JL-007 (CWE-22): Joomla request input reaches include/require or a file read (LFI / path traversal). Logic: core `UnsafePathCheck`. */
class JL007UnsafeFilePathInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "JL-007"
    }
}
