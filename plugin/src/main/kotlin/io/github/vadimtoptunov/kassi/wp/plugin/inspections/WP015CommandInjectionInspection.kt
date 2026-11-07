package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-015 (CWE-78): request data reaches an OS-command function (exec/system/shell_exec/...) without escapeshellarg()/escapeshellcmd(). Logic: core `CommandInjectionCheck`. */
class WP015CommandInjectionInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-015"
    }
}
