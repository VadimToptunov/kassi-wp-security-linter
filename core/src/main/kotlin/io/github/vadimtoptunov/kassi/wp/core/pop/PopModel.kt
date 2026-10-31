package io.github.vadimtoptunov.kassi.wp.core.pop

import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.SourceLocation

/**
 * Data model for WP-014: cross-plugin PHP object injection (POP gadget chains).
 *
 * The analysis builds two catalogs over a co-install universe (every plugin and theme in the project,
 * or the corpus) and joins them:
 *  - [DeserializationSink]: where attacker-controlled data reaches a deserialization sink
 *    (`unserialize()` of request data, or a PHAR-triggering file op on a request-derived path).
 *  - [GadgetClass]: a class whose magic method performs a dangerous operation — the gadget that
 *    completes the exploit once an attacker can inject an object.
 *
 * A (sink, gadget) pair is a candidate [PopChain]. The sink's plugin and the gadget's plugin may
 * differ: on a real WordPress install every active plugin shares one PHP process, so an object
 * injected through one plugin's sink can drive any other plugin's (or WP core's) gadget.
 */

/** How exposed a sink's enclosing code is — the first ranking axis (unauth is worst). */
enum class Reachability(val rank: Int, val label: String) {
    UNAUTH(3, "unauthenticated"),
    LOW_PRIV(2, "any logged-in user"),
    ADMIN(1, "admin-gated"),
    UNKNOWN(1, "reachability not proven"),
}

/** What a gadget's magic method does — the second ranking axis (RCE/file-write is worst). */
enum class GadgetDanger(val rank: Int, val label: String) {
    RCE(4, "code execution / file write or delete"),
    SQL(3, "SQL execution"),
    HTTP(2, "outbound HTTP request (SSRF)"),
    OPTION_WRITE(2, "option / metadata write"),
    INFO(1, "file read / information disclosure"),
}

/** The deserialization vector of a sink. */
enum class SinkKind(val label: String) {
    UNSERIALIZE("unserialize() of request data"),
    PHAR("phar:// deserialization via a file operation on a request-derived path"),
}

/**
 * One deserialization sink reachable with attacker-controlled input.
 *
 * @property plugin        the plugin/theme slug the sink lives in (the directory under the scan root).
 * @property function      the sink function as written (`unserialize`, `file_exists`, ...).
 * @property source        the taint source label (`$_POST`, `->get_param()`, ...).
 * @property reachability  how exposed the enclosing code is.
 * @property node          the sink call node (where to report the finding).
 */
data class DeserializationSink(
    val plugin: String,
    val kind: SinkKind,
    val function: String,
    val source: String,
    val reachability: Reachability,
    val node: AstNode,
) {
    val location: SourceLocation get() = node.location
}

/**
 * One gadget class: a magic method whose body performs a dangerous operation.
 *
 * @property plugin     the plugin/theme slug the gadget lives in.
 * @property className  the declaring class' short name.
 * @property method     the magic method (`__destruct`, `__wakeup`, ...).
 * @property danger     the worst operation the method performs.
 * @property dangerCall the dangerous call as written (`unlink`, `$wpdb->query`, `exec`, ...).
 * @property node       the magic-method node.
 */
data class GadgetClass(
    val plugin: String,
    val className: String,
    val method: String,
    val danger: GadgetDanger,
    val dangerCall: String,
    val node: AstNode,
) {
    val location: SourceLocation get() = node.location
}

/**
 * A candidate exploit chain: attacker input reaches [sink], and [gadget] completes it once an object
 * is injected. [crossPlugin] is true when the sink and the gadget live in different plugins — the
 * class of chain Patchstack rewards, because neither plugin is exploitable on its own.
 */
data class PopChain(val sink: DeserializationSink, val gadget: GadgetClass) {
    val crossPlugin: Boolean get() = sink.plugin != gadget.plugin

    /** Higher is worse: reachability × gadget danger, with a small bonus for a cross-plugin chain. */
    val score: Int get() = sink.reachability.rank * gadget.danger.rank + if (crossPlugin) 1 else 0

    /**
     * The finding text. Same-plugin chains omit plugin names so the per-file inspection message is
     * path-independent (and deterministic for fixtures); cross-plugin chains name both plugins,
     * because that is the whole point of the finding.
     */
    val message: String
        get() = buildString {
            append("PHP object injection → ")
            append(if (crossPlugin) "cross-plugin POP gadget chain: " else "POP gadget chain: ")
            append(sink.kind.label).append(" (").append(sink.reachability.label)
            append(", source ").append(sink.source).append(")")
            if (crossPlugin) append(" in plugin '${sink.plugin}'")
            append(" can instantiate ").append(gadget.className).append("::").append(gadget.method).append("()")
            append(if (crossPlugin) " in co-installed plugin '${gadget.plugin}'" else " in the same plugin")
            append(", which performs ").append(gadget.danger.label)
            append(" (").append(gadget.dangerCall).append("). ")
            append("Pass ['allowed_classes' => false] to unserialize(), or validate the path, to break the chain.")
        }
}
