package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.SourceLocation
import io.github.vadimtoptunov.kassi.wp.core.pop.DeserializationSink
import io.github.vadimtoptunov.kassi.wp.core.pop.GadgetClass
import io.github.vadimtoptunov.kassi.wp.core.pop.PopCatalogBuilder
import io.github.vadimtoptunov.kassi.wp.core.pop.PopChainMatcher
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules
import java.io.File

/**
 * Not a regular test: the WP-014 cross-plugin research run (skipped unless -Pkassi.popchain). It
 * builds the deserialization-sink and gadget catalogs over every plugin in the corpus (the
 * co-install universe), joins them with [PopChainMatcher], and writes the ranked candidate chains to
 * build/reports/pop-chain-run.md.
 *
 * Each plugin is indexed on its own (so hook/callback resolution works per plugin, like
 * [FalsePositiveRun]); catalog entries are copied onto detached nodes that keep only their source
 * location, so no PSI is retained once a plugin has been scanned and the full corpus fits in memory.
 */
class PopChainRun : BasePlatformTestCase() {

    /** A node that survives PSI teardown, carrying only the location the report needs. */
    private class Detached(loc: SourceLocation) : AstNode {
        override val kind = AstKind.OTHER
        override val name: String? = null
        override val text = ""
        override val location = loc
        override val parent: AstNode? = null
        override val children = emptyList<AstNode>()
        override val arguments = emptyList<AstNode>()
        override val stringValue: String? = null
        override fun child(role: AstRole): AstNode? = null
    }

    private fun detach(s: DeserializationSink) = s.copy(node = Detached(s.location))
    private fun detach(g: GadgetClass) = g.copy(node = Detached(g.location))

    fun testRankCrossPluginChains() {
        val root = System.getProperty("kassi.popchain.dir")?.let(::File) ?: return
        assertTrue("$root missing. Run corpus/fetch-popular.sh", root.isDirectory)
        val report = File(requireNotNull(System.getProperty("kassi.popchain.report")))
        val builder = PopCatalogBuilder(BuiltInRules.knowledgeBase.require("WP-014"))

        val allSinks = mutableListOf<DeserializationSink>()
        val allGadgets = mutableListOf<GadgetClass>()
        var filesScanned = 0

        LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
                if (message.contains("stub tree", ignoreCase = true) || message.contains("Failed to build stub", ignoreCase = true)) emptySet()
                else super.processError(category, message, details, t)
        }) {
            for (plugin in root.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }) {
                val phpFiles = plugin.walkTopDown().filter { it.isFile && it.extension == "php" }.toList()
                val inProject: List<Pair<File, PsiFile>> = phpFiles.mapNotNull { php ->
                    val rel = php.relativeTo(plugin).path
                    runCatching { myFixture.addFileToProject("${plugin.name}/$rel", php.readText()) }.getOrNull()?.let { php to it }
                }
                for ((php, psi) in inProject) {
                    if (psi !is PhpFile) continue
                    filesScanned++
                    val rel = "${plugin.name}/${php.relativeTo(plugin).path}"
                    val ast = runCatching { PhpPsiAstNode.wrap(psi) }.getOrNull() ?: continue
                    runCatching {
                        builder.sinksIn(ast, plugin.name).forEach { allSinks += detach(relocate(it, rel)) }
                        builder.gadgetsIn(ast, plugin.name).forEach { allGadgets += detach(relocate(it, rel)) }
                    }
                }
                WriteAction.runAndWait<Throwable> {
                    inProject.forEach { (_, psi) -> runCatching { psi.virtualFile?.delete(this) } }
                }
            }
        }

        val chains = PopChainMatcher.match(allSinks, allGadgets)
        val cross = chains.filter { it.crossPlugin }
        val top = (cross + chains.filterNot { it.crossPlugin }).take(MAX_ROWS)

        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("# Cross-plugin POP gadget chains (WP-014)")
                appendLine()
                appendLine("Files scanned: $filesScanned. Sinks: ${allSinks.size}. Gadgets: ${allGadgets.size}.")
                appendLine("Candidate chains: ${chains.size} (${cross.size} cross-plugin). Showing top ${top.size}.")
                appendLine()
                appendLine("| Score | Cross-plugin | Sink (reachability) | Sink location | Gadget | Gadget location |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                for (c in top) {
                    appendLine(
                        "| ${c.score} | ${if (c.crossPlugin) "yes" else "no"} | " +
                            "${c.sink.function}() ${c.sink.kind.name} / ${c.sink.reachability.label} | " +
                            "`${c.sink.location.path}:${c.sink.location.line}` | " +
                            "${c.gadget.className}::${c.gadget.method} (${c.gadget.danger.name}: ${c.gadget.dangerCall}) | " +
                            "`${c.gadget.location.path}:${c.gadget.location.line}` |",
                    )
                }
                appendLine()
            },
        )
        println("POP-chain run: $filesScanned files, ${allSinks.size} sinks, ${allGadgets.size} gadgets, ${chains.size} chains (${cross.size} cross-plugin) -> $report")
    }

    private fun relocate(s: DeserializationSink, path: String): DeserializationSink =
        s.copy(node = Detached(s.location.copy(path = path)))

    private fun relocate(g: GadgetClass, path: String): GadgetClass =
        g.copy(node = Detached(g.location.copy(path = path)))

    private companion object {
        const val MAX_ROWS = 200
    }
}
