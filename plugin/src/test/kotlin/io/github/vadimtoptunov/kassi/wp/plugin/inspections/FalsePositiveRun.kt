package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules
import java.io.File

/**
 * Not a regular test: a review aid. Runs every inspection over the popular-plugin corpus
 * (corpus/fetch-popular.sh) and writes every finding to build/reports/fp-run.md for manual triage.
 * Skipped unless Gradle is run with -Pkassi.fprun.
 *
 * Indexes ONE plugin at a time: its PHP files are added to the fixture project (so PhpIndex resolves
 * cross-file — a handler gated by a helper in another file like Helpers::verify_ajax_call is not
 * reported, matching the IDE), scanned, then deleted before the next plugin. Per-plugin indexing
 * keeps memory and PhpIndex bounded on large batches (indexing a whole 4500-file batch at once ran
 * out of headroom).
 */
class FalsePositiveRun : BasePlatformTestCase() {

    fun testRunOverPopularPlugins() {
        val root = System.getProperty("kassi.fprun.dir")?.let(::File) ?: return
        assertTrue("$root missing. Run corpus/fetch-popular.sh", root.isDirectory)
        val report = File(requireNotNull(System.getProperty("kassi.fprun.report")))

        val rules = BuiltInRules.knowledgeBase.rules.mapNotNull { r ->
            Inspections.byRuleId[r.id]?.let { tool -> Triple(r.id, tool, r.source.map(String::lowercase)) }
        }
        val lines = mutableListOf<String>()
        var scanned = 0
        // Scanning arbitrary third-party code, PhpIndex can log internal errors on pathological
        // vendored files (e.g. "Failed to build stub tree for class-freemius.php"). resolveCall
        // already catches the thrown exception, but in test mode the *logged* error alone fails the
        // run — so tolerate those PSI-internal errors here. This is a review aid, not a correctness
        // test; real behaviour is covered by the unit tests. Other errors still fail.
        LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
                if (message.contains("stub tree", ignoreCase = true) || message.contains("Failed to build stub", ignoreCase = true)) {
                    emptySet()
                } else {
                    super.processError(category, message, details, t)
                }
        }) {
            for (plugin in root.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }) {
                // Index this plugin's files together so PhpIndex can resolve across them.
                val phpFiles = plugin.walkTopDown().filter { it.isFile && it.extension == "php" }.toList()
                val inProject: List<Pair<File, PsiFile>> = phpFiles.mapNotNull { php ->
                    val rel = php.relativeTo(plugin).path
                    runCatching { myFixture.addFileToProject("${plugin.name}/$rel", php.readText()) }
                        .getOrNull()?.let { php to it }
                }
                for ((ruleId, tool, needles) in rules) {
                    for ((php, psi) in inProject) {
                        val lower = psi.text.lowercase()
                        if (needles.none { it in lower }) continue
                        scanned++
                        runInspectionOn(project, tool, psi).forEach { f ->
                            val snippet = f.lineText.take(140).replace("|", "\\|")
                            lines += "| $ruleId | ${plugin.name} | `${php.relativeTo(plugin).path}:${f.line}` | ${f.message} | `$snippet` |"
                        }
                    }
                }
                // Drop this plugin's files before indexing the next, to bound memory / PhpIndex.
                WriteAction.runAndWait<Throwable> {
                    inProject.forEach { (_, psi) -> runCatching { psi.virtualFile?.delete(this) } }
                }
            }
        }
        report.parentFile.mkdirs()
        report.writeText(
            "# False-positive run\n\nFiles scanned: $scanned. Findings: ${lines.size}.\n\n" +
                "| Rule | Plugin | Location | Message | Line |\n| --- | --- | --- | --- | --- |\n" +
                lines.joinToString("\n") + "\n",
        )
        println("FP run: scanned $scanned files, ${lines.size} findings -> $report")
    }
}
