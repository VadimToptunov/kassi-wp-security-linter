package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import io.github.vadimtoptunov.kassi.wp.core.pop.PopCatalogBuilder
import io.github.vadimtoptunov.kassi.wp.core.pop.PopChain
import io.github.vadimtoptunov.kassi.wp.core.pop.Reachability

/**
 * WP-014 as a per-file inspection: the same-plugin slice of the cross-plugin POP-chain analysis.
 *
 * It fires only when ONE file holds both halves of a working chain — a deserialization sink that
 * attacker input reaches ([PopCatalogBuilder.sinksIn]) **and** a gadget class whose magic method
 * performs a dangerous operation ([PopCatalogBuilder.gadgetsIn]). A sink without a reachable gadget
 * is WP-009's concern; a harmless magic method is not a gadget. Requiring both in view keeps the
 * per-file finding high-signal.
 *
 * The cross-plugin pairing — a sink in plugin A with a gadget in plugin B — is produced by the
 * project report and the headless corpus run, which build the catalogs across every file and call
 * `PopChainMatcher`. This check is the single-file, same-plugin projection of that.
 */
class PopChainCheck(private val rule: Rule) : FileCheck {
    private val builder = PopCatalogBuilder(rule)

    override fun checkFile(file: AstNode): List<Finding> {
        val plugin = pluginOf(file)
        val sinks = builder.sinksIn(file, plugin)
        if (sinks.isEmpty()) return emptyList()
        val gadgets = builder.gadgetsIn(file, plugin)
        if (gadgets.isEmpty()) return emptyList()
        val gadget = gadgets.maxByOrNull { it.danger.rank }!!
        return sinks.map { sink ->
            val chain = PopChain(sink, gadget)
            // A sink whose exposure can't be tied to a hook/route is still a real chain, but reported
            // weak: we can't prove an unauthenticated attacker reaches it.
            Finding(rule.id, sink.node, chain.message, weak = sink.reachability == Reachability.UNKNOWN)
        }
    }

    /** The slug the file belongs to: the first path segment (`my-plugin/includes/x.php`), else the file. */
    private fun pluginOf(file: AstNode): String =
        file.location.path.substringBefore('/').ifEmpty { file.location.path }
}
