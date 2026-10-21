package io.github.vadimtoptunov.kassi.wp.core.hooks

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole

/**
 * A call pattern from rule YAML:
 *  - `update_option`   a function
 *  - `wpdb->insert`    a method called on a variable or property named `wpdb`
 *  - `->set_role`      a method on any receiver
 * Names may use `*` as a wildcard (`*user_can*` matches `wpforms_current_user_can`): helpers with
 * such names are defined in other files the analysis does not see.
 */
data class CallTarget(val function: String?, val receiver: String?, val method: String?) {
    private val functionRe = function?.let(::glob)
    private val methodRe = method?.let(::glob)

    val label: String get() = function ?: if (receiver != null) "\$$receiver->$method" else "->$method"

    fun matches(call: AstNode): Boolean {
        val name = call.name?.lowercase() ?: return false
        return when {
            functionRe != null -> call.kind == AstKind.CALL && functionRe.matches(name)
            call.kind != AstKind.METHOD_CALL && call.kind != AstKind.STATIC_CALL -> false
            methodRe?.matches(name) != true -> false
            receiver == null -> true
            else -> call.child(AstRole.RECEIVER)?.takeIf { it.kind == AstKind.VARIABLE || it.kind == AstKind.PROPERTY_ACCESS }
                ?.name?.lowercase() == receiver
        }
    }

    companion object {
        private val IDENT = Regex("""[A-Za-z_*][A-Za-z0-9_*]*""")

        private fun glob(p: String) = Regex(p.split('*').joinToString(".*") { Regex.escape(it) })

        fun parse(s: String): CallTarget {
            val i = s.indexOf("->")
            if (i < 0) {
                require(IDENT.matches(s)) { "invalid call target '$s'" }
                return CallTarget(s.lowercase(), null, null)
            }
            val recv = s.substring(0, i).ifEmpty { null }
            val method = s.substring(i + 2)
            require(IDENT.matches(method) && (recv == null || IDENT.matches(recv))) { "invalid call target '$s'" }
            return CallTarget(null, recv?.lowercase(), method.lowercase())
        }

        fun parseAll(items: List<String>): List<CallTarget> = items.map(::parse)
    }
}
