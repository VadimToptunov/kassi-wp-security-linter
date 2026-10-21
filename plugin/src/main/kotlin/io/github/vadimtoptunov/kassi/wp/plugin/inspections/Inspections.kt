package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool

/** Rule id → inspection. Used by the report and by tests that tie rules, corpus and plugin.xml together. */
object Inspections {
    val byRuleId: Map<String, LocalInspectionTool>
        get() = mapOf(
            WP001MissingCapabilityCheckInspection.RULE_ID to WP001MissingCapabilityCheckInspection(),
            WP002MissingNonceVerificationInspection.RULE_ID to WP002MissingNonceVerificationInspection(),
            WP003NoprivPrivilegedActionInspection.RULE_ID to WP003NoprivPrivilegedActionInspection(),
            WP004UnprotectedRestRouteInspection.RULE_ID to WP004UnprotectedRestRouteInspection(),
            WP005UnpreparedSqlInspection.RULE_ID to WP005UnpreparedSqlInspection(),
            WP006UnescapedOutputInspection.RULE_ID to WP006UnescapedOutputInspection(),
            WP007UnsafeFilePathInspection.RULE_ID to WP007UnsafeFilePathInspection(),
            WP008UnsafeRedirectInspection.RULE_ID to WP008UnsafeRedirectInspection(),
            WP009ObjectInjectionInspection.RULE_ID to WP009ObjectInjectionInspection(),
            WP010ServerSideRequestForgeryInspection.RULE_ID to WP010ServerSideRequestForgeryInspection(),
            WP011UnsafeFileUploadInspection.RULE_ID to WP011UnsafeFileUploadInspection(),
            WP012PrivilegeEscalationInspection.RULE_ID to WP012PrivilegeEscalationInspection(),
            WP013ShortcodeAttributeXssInspection.RULE_ID to WP013ShortcodeAttributeXssInspection(),
        )
}
