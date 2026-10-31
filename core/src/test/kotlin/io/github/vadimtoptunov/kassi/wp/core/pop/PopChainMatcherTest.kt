package io.github.vadimtoptunov.kassi.wp.core.pop

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.SourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The join + ranking logic, over hand-built catalog entries (no parser). */
class PopChainMatcherTest {

    private class Stub(path: String, offset: Int) : AstNode {
        override val kind = AstKind.OTHER
        override val name: String? = null
        override val text = ""
        override val location = SourceLocation(path, 1, offset, offset)
        override val parent: AstNode? = null
        override val children = emptyList<AstNode>()
        override val arguments = emptyList<AstNode>()
        override val stringValue: String? = null
        override fun child(role: AstRole): AstNode? = null
    }

    private fun sink(plugin: String, reach: Reachability, offset: Int = 0) =
        DeserializationSink(plugin, SinkKind.UNSERIALIZE, "unserialize", "\$_POST", reach, Stub("$plugin/a.php", offset))

    private fun gadget(plugin: String, danger: GadgetDanger, offset: Int = 0) =
        GadgetClass(plugin, "G_$plugin", "__destruct", danger, "unlink", Stub("$plugin/g.php", offset))

    @Test
    fun `emits one chain per sink-gadget pair`() {
        val chains = PopChainMatcher.match(
            listOf(sink("a", Reachability.UNAUTH), sink("a", Reachability.ADMIN, 1)),
            listOf(gadget("b", GadgetDanger.RCE), gadget("c", GadgetDanger.SQL)),
        )
        assertEquals(4, chains.size)
    }

    @Test
    fun `cross-plugin detection and filter`() {
        val same = PopChain(sink("a", Reachability.UNAUTH), gadget("a", GadgetDanger.RCE))
        val cross = PopChain(sink("a", Reachability.UNAUTH), gadget("b", GadgetDanger.RCE))
        assertFalse(same.crossPlugin)
        assertTrue(cross.crossPlugin)

        val sinks = listOf(sink("a", Reachability.UNAUTH))
        val gadgets = listOf(gadget("a", GadgetDanger.RCE), gadget("b", GadgetDanger.RCE))
        assertEquals(1, PopChainMatcher.crossPluginOnly(sinks, gadgets).size)
    }

    @Test
    fun `ranks unauth RCE above admin info, cross-plugin breaks ties`() {
        val worst = sink("a", Reachability.UNAUTH)
        val best = sink("d", Reachability.ADMIN)
        val chains = PopChainMatcher.match(
            listOf(best, worst),
            listOf(gadget("a", GadgetDanger.INFO), gadget("b", GadgetDanger.RCE)),
        )
        // Highest score first: unauth(3) × RCE(4) + cross(1) = 13.
        val top = chains.first()
        assertEquals(Reachability.UNAUTH, top.sink.reachability)
        assertEquals(GadgetDanger.RCE, top.gadget.danger)
        assertTrue(top.crossPlugin)
        // Scores are non-increasing.
        assertEquals(chains.map { it.score }, chains.map { it.score }.sortedDescending())
    }

    @Test
    fun `message names both plugins only for cross-plugin chains`() {
        val cross = PopChain(sink("plugin-a", Reachability.UNAUTH), gadget("plugin-b", GadgetDanger.RCE)).message
        assertTrue(cross.contains("cross-plugin"))
        assertTrue(cross.contains("plugin-a"))
        assertTrue(cross.contains("plugin-b"))

        val same = PopChain(sink("plugin-a", Reachability.UNAUTH), gadget("plugin-a", GadgetDanger.RCE)).message
        assertFalse(same.contains("cross-plugin"))
        assertTrue(same.contains("the same plugin"))
    }
}
