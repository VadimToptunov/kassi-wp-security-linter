package io.github.vadimtoptunov.kassi.wp.core.reach

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.walk

/**
 * The "reachability" half of a chain: how an UNAUTHENTICATED attacker can reach the scope that holds
 * a dangerous sink in a Joomla extension. A sink only matters if a request reaches it without logging
 * in, so this classifies the enclosing scope from Joomla's entry-point conventions:
 *
 *  - `onAjax<Name>()` plugin methods           → index.php?option=com_ajax (unauthenticated by default)
 *  - site / frontend component code, no ACL     → index.php?option=com_xxx&task=/view= (public)
 *  - a scope that calls authorise()/getUser()   → the handler gates itself (privileged)
 *  - administrator/ or admin/ code              → requires a back-end login (privileged)
 *  - a _JEXEC-guarded top-level script          → reachable only if a route includes it (lower odds)
 *
 * Deliberately conservative and convention-driven: it RANKS leads for manual triage, it does not prove
 * exploitability. Treat [Entry.unauth] = a lead worth a look, not a confirmed unauthenticated path.
 */
object JoomlaReachability {

    enum class Entry(val unauth: Boolean, val label: String) {
        UNAUTH_AJAX(true, "unauth com_ajax (onAjax*)"),
        UNAUTH_FRONTEND(true, "unauth site/frontend (no ACL in scope)"),
        DIRECT_FILE(false, "_JEXEC-guarded script (only if a route includes it)"),
        ADMIN(false, "administrator/back-end (login required)"),
        GATED(false, "ACL-checked in scope (authorise/getUser/checkToken)"),
        UNKNOWN(false, "entry not recognised"),
    }

    // ACL / CSRF calls that gate a handler. getUser() alone is a weak signal, but combined with the
    // frontend conventions it is the usual shape of an access check in Joomla controllers.
    private val ACL = setOf("authorise", "authorize", "checktoken", "getformtoken", "allowaction")
    private val AJAX = Regex("^onajax", RegexOption.IGNORE_CASE)

    /**
     * @param scope    the sink's enclosing scope (a FUNCTION/METHOD/CLOSURE, or the FILE for a
     *                 top-level sink), as produced by `TaintAnalyzer.scopes`.
     * @param filePath the sink file's path (`SourceLocation.path`), `/`-separated.
     */
    fun classify(scope: AstNode, filePath: String): Entry {
        val path = filePath.lowercase()

        // An onAjax* method (this scope or the method enclosing a closure) is the com_ajax entry.
        if (AJAX.containsMatchIn(enclosingName(scope))) return Entry.UNAUTH_AJAX

        // An explicit ACL/CSRF check anywhere in the scope gates it, whatever the path.
        if (callsAcl(scope)) return Entry.GATED

        // Back-end code requires a login. Match the `administrator/` segment with or without a leading
        // slash (corpus paths carry a flattened `owner_repo/administrator/...` prefix).
        if ("administrator/" in path || "/admin/" in path || path.startsWith("admin/") || "controllers/admin" in path) {
            return Entry.ADMIN
        }

        // Not runtime-routed: a Joomla install/update script runs only during (admin) install, and
        // third-party vendored libraries are reached only through whatever calls them — neither is an
        // unauthenticated entry point on its own, so they must not inflate the unauth shortlist.
        val base = path.substringAfterLast('/')
        if (base == "script.php" || base.endsWith("script.install.php") || "/vendor/" in path || "/libraries/" in path) {
            return Entry.UNKNOWN
        }

        // A sink sitting at a file's top level, behind the _JEXEC guard, is only reached if some
        // route includes the file (e.g. the Admiror Gallery download.php pattern) — a weaker lead.
        if (scope.kind == AstKind.FILE && hasJexecGuard(scope)) return Entry.DIRECT_FILE

        // Frontend component / plugin / module code, reachable over the public site.
        if ("/site/" in path || "com_" in path || "/plugins/" in path || "/modules/" in path) {
            return Entry.UNAUTH_FRONTEND
        }

        return Entry.UNKNOWN
    }

    /** The name of [scope] if it is a named function/method, else the nearest enclosing one. */
    private fun enclosingName(scope: AstNode): String {
        var n: AstNode? = scope
        while (n != null) {
            if (n.kind == AstKind.FUNCTION || n.kind == AstKind.METHOD) return n.name?.lowercase().orEmpty()
            n = n.parent
        }
        return ""
    }

    private fun callsAcl(scope: AstNode): Boolean = scope.walk().any { n ->
        (n.kind == AstKind.CALL || n.kind == AstKind.METHOD_CALL || n.kind == AstKind.STATIC_CALL) &&
            n.name?.lowercase() in ACL
    }

    /** A `defined('_JEXEC')` guard somewhere in the sink's file. */
    private fun hasJexecGuard(scope: AstNode): Boolean {
        var root: AstNode = scope
        while (root.parent != null) root = root.parent!!
        return root.walk().any { n ->
            n.kind == AstKind.CALL && n.name?.lowercase() == "defined" &&
                n.arguments.firstOrNull()?.stringValue == "_JEXEC"
        }
    }
}
