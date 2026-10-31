package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.pop.GadgetDanger
import io.github.vadimtoptunov.kassi.wp.core.pop.PopCatalogBuilder
import io.github.vadimtoptunov.kassi.wp.core.pop.PopChainMatcher
import io.github.vadimtoptunov.kassi.wp.core.pop.Reachability
import io.github.vadimtoptunov.kassi.wp.core.pop.SinkKind
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules

/**
 * The cross-plugin matcher end-to-end over real parsed PHP: a sink in one plugin and a gadget in a
 * separate plugin must produce a cross-plugin chain, and FP controls (a sink with
 * allowed_classes => false, or a harmless magic method) must produce none.
 */
class PopChainCrossPluginTest : BasePlatformTestCase() {

    private val builder by lazy { PopCatalogBuilder(BuiltInRules.knowledgeBase.require("WP-014")) }

    private fun ast(name: String, php: String): PhpPsiAstNode {
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText(name, PhpFileType.INSTANCE, php, System.currentTimeMillis(), true) as PhpFile
        return PhpPsiAstNode.wrap(file)
    }

    private val sinkPluginVulnerable = """
        <?php
        add_action( 'wp_ajax_nopriv_a_imp', 'a_imp' );
        function a_imp() {
            return unserialize( base64_decode( ${'$'}_POST['blob'] ) );
        }
    """.trimIndent()

    private val gadgetPlugin = """
        <?php
        class B_Cleanup {
            public ${'$'}path;
            public function __destruct() {
                unlink( ${'$'}this->path );
            }
        }
    """.trimIndent()

    fun testCrossPluginChainIsReported() {
        val sinks = builder.sinksIn(ast("a.php", sinkPluginVulnerable), "plugin-a")
        val gadgets = builder.gadgetsIn(ast("b.php", gadgetPlugin), "plugin-b")

        assertEquals(1, sinks.size)
        assertEquals(SinkKind.UNSERIALIZE, sinks[0].kind)
        assertEquals(Reachability.UNAUTH, sinks[0].reachability)
        assertEquals("\$_POST", sinks[0].source)

        assertEquals(1, gadgets.size)
        assertEquals(GadgetDanger.RCE, gadgets[0].danger)

        val chains = PopChainMatcher.crossPluginOnly(sinks, gadgets)
        assertEquals(1, chains.size)
        val chain = chains.single()
        assertTrue(chain.crossPlugin)
        assertEquals("plugin-a", chain.sink.plugin)
        assertEquals("plugin-b", chain.gadget.plugin)
        assertTrue(chain.message, chain.message.contains("cross-plugin POP gadget chain"))
        assertTrue(chain.message, chain.message.contains("B_Cleanup::__destruct"))
    }

    fun testSinkWithAllowedClassesFalseDoesNotFire() {
        val fixed = """
            <?php
            add_action( 'wp_ajax_nopriv_a_imp', 'a_imp' );
            function a_imp() {
                return unserialize( ${'$'}_POST['blob'], array( 'allowed_classes' => false ) );
            }
        """.trimIndent()
        val sinks = builder.sinksIn(ast("a.php", fixed), "plugin-a")
        val gadgets = builder.gadgetsIn(ast("b.php", gadgetPlugin), "plugin-b")
        assertTrue(sinks.isEmpty())
        assertTrue(PopChainMatcher.crossPluginOnly(sinks, gadgets).isEmpty())
    }

    fun testHarmlessMagicMethodIsNotAGadget() {
        val harmless = """
            <?php
            class H {
                public ${'$'}v;
                public function __destruct() { ${'$'}this->v = 1; }
                public function __wakeup() { ${'$'}this->v = 2; }
            }
        """.trimIndent()
        val sinks = builder.sinksIn(ast("a.php", sinkPluginVulnerable), "plugin-a")
        val gadgets = builder.gadgetsIn(ast("h.php", harmless), "plugin-b")
        assertEquals(1, sinks.size)
        assertTrue(gadgets.isEmpty())
        assertTrue(PopChainMatcher.crossPluginOnly(sinks, gadgets).isEmpty())
    }
}
