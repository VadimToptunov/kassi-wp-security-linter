package io.github.vadimtoptunov.kassi.wp.plugin.inspections


/** WP-017 (CWE-93): request data reaches the headers argument of wp_mail()/mail() without CR/LF removal, i.e. email header injection. Logic: core `MailHeaderInjectionCheck`. */
class WP017MailHeaderInjectionInspection : RuleInspection(RULE_ID) {

    companion object {
        const val RULE_ID = "WP-017"
    }
}
