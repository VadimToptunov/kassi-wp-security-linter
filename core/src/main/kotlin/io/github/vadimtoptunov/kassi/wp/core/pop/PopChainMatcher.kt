package io.github.vadimtoptunov.kassi.wp.core.pop

/**
 * Joins the sink and gadget catalogs into candidate [PopChain]s.
 *
 * Every (sink, gadget) pair is a candidate — a sink in plugin A reachable with attacker input paired
 * with a gadget in any plugin B (B may equal A). The result is ranked by exploit value
 * (reachability × gadget danger, cross-plugin chains breaking ties upward) and is deterministic:
 * equal-score chains are ordered by sink location, then gadget location, so the same corpus always
 * produces byte-identical output.
 */
object PopChainMatcher {

    fun match(sinks: List<DeserializationSink>, gadgets: List<GadgetClass>): List<PopChain> =
        sinks.flatMap { sink -> gadgets.map { gadget -> PopChain(sink, gadget) } }.sortedWith(ORDER)

    /** Only the chains whose sink and gadget live in different plugins. */
    fun crossPluginOnly(sinks: List<DeserializationSink>, gadgets: List<GadgetClass>): List<PopChain> =
        match(sinks, gadgets).filter { it.crossPlugin }

    private val ORDER: Comparator<PopChain> = compareByDescending<PopChain> { it.score }
        .thenByDescending { it.crossPlugin }
        .thenBy { it.sink.plugin }
        .thenBy { it.sink.location.path }
        .thenBy { it.sink.location.startOffset }
        .thenBy { it.gadget.plugin }
        .thenBy { it.gadget.location.path }
        .thenBy { it.gadget.location.startOffset }
}
