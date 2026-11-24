package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/**
 * WP-020 (CWE-79): cross-role stored XSS where request input is persisted to options / meta /
 * wpdb tables and later echoed on an admin page without an escaper. The real engine is pipeline-
 * level (`StoredXssCheck`) because it needs every file of one plugin version at once; the IDE
 * inspection stays registered so the plugin's rule catalog is complete but produces no in-IDE
 * findings.
 */
class WP020CrossRoleStoredXssInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-020"
    }
}
