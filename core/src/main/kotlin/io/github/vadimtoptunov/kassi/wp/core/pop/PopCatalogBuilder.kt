package io.github.vadimtoptunov.kassi.wp.core.pop

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * Builds the two WP-014 catalogs over one parsed file. The sink side reuses the shared
 * [TaintAnalyzer] (so request input is followed through assignments, concatenation, passthrough
 * functions and branches exactly as the other rules do); the gadget side is a structural scan of
 * class magic methods. Every function/sink/danger name comes from the rule's `params`.
 */
class PopCatalogBuilder(rule: Rule) {
    private val analyzer = TaintAnalyzer(TaintSpec.of(rule))

    private val unserializeFns = rule.params.strings("unserialize_functions").map { it.lowercase() }.toSet()
    private val pharFns = rule.params.strings("phar_file_functions").map { it.lowercase() }.toSet()
    private val magicMethods = rule.params.strings("gadget_magic_methods").map { it.lowercase() }.toSet()

    /** Danger categories, highest rank first, so the first hit in a magic method is its worst op. */
    private val dangerCatalog: List<Pair<GadgetDanger, List<CallTarget>>> = listOf(
        GadgetDanger.RCE to CallTarget.parseAll(rule.params.strings("rce_sinks")),
        GadgetDanger.SQL to CallTarget.parseAll(rule.params.strings("sql_sinks")),
        GadgetDanger.HTTP to CallTarget.parseAll(rule.params.strings("http_sinks")),
        GadgetDanger.OPTION_WRITE to CallTarget.parseAll(rule.params.strings("option_write_sinks")),
        GadgetDanger.INFO to CallTarget.parseAll(rule.params.strings("info_sinks")),
    ).sortedByDescending { it.first.rank }

    private val reachability = ReachabilityClassifier(
        hookRegistrars = rule.params.strings("hook_registrars"),
        restRegistrars = rule.params.strings("rest_registrars"),
        noprivMarkers = rule.params.strings("nopriv_markers"),
        lowPrivPrefixes = rule.params.strings("low_priv_hook_prefixes"),
        capabilityChecks = rule.params.strings("capability_checks"),
        openPermissionCallbacks = rule.params.strings("open_permission_callbacks"),
    )

    /** Request-reachable deserialization sinks in [file], attributed to [plugin]. */
    fun sinksIn(file: AstNode, plugin: String): List<DeserializationSink> =
        analyzer.scopes(file).flatMap { scope ->
            analyzer.analyze(scope, ::sinkArguments).map { hit ->
                val fn = hit.sink.name!!.lowercase()
                DeserializationSink(
                    plugin = plugin,
                    kind = if (fn in unserializeFns) SinkKind.UNSERIALIZE else SinkKind.PHAR,
                    function = fn,
                    source = hit.taint.label,
                    reachability = reachability.classify(file, hit.sink),
                    node = hit.sink,
                )
            }
        }.distinctBy { it.location.startOffset }

    private fun sinkArguments(node: AstNode): List<AstNode> {
        if (node.kind != AstKind.CALL) return emptyList()
        val fn = node.name?.lowercase() ?: return emptyList()
        return when (fn) {
            in unserializeFns -> if (disallowsClasses(node.arguments.getOrNull(1))) emptyList()
            else listOfNotNull(node.arguments.firstOrNull())
            in pharFns -> listOfNotNull(node.arguments.firstOrNull())
            else -> emptyList()
        }
    }

    /** `unserialize($x, ['allowed_classes' => false])`: no object is built, so no chain. */
    private fun disallowsClasses(options: AstNode?): Boolean =
        options?.kind == AstKind.ARRAY_LITERAL && options.arguments.any { e ->
            e.child(AstRole.KEY)?.stringValue == "allowed_classes" &&
                e.child(AstRole.VALUE)?.let { it.kind == AstKind.CONSTANT && it.name.equals("false", ignoreCase = true) } == true
        }

    /** Gadget classes in [file], attributed to [plugin]: one entry per dangerous magic method. */
    fun gadgetsIn(file: AstNode, plugin: String): List<GadgetClass> =
        file.walk().filter { it.kind == AstKind.CLASS && it.name != null }.flatMap { cls ->
            cls.walk().filter { it.kind == AstKind.METHOD && it.name?.lowercase() in magicMethods }
                .mapNotNull { method ->
                    worstDanger(method)?.let { (danger, call) ->
                        GadgetClass(plugin, cls.name!!, method.name!!, danger, call, method)
                    }
                }
        }.toList()

    private fun worstDanger(method: AstNode): Pair<GadgetDanger, String>? {
        val calls = HandlerFacts.callsIn(method)
        val hasInclude = method.walk().any { it != method && it.kind == AstKind.INCLUDE }
        for ((danger, targets) in dangerCatalog) {
            calls.firstOrNull { c -> targets.any { it.matches(c) } }
                ?.let { hit -> return danger to (targets.first { it.matches(hit) }.label) }
            if (danger == GadgetDanger.RCE && hasInclude) return GadgetDanger.RCE to "include/require"
        }
        return null
    }
}
