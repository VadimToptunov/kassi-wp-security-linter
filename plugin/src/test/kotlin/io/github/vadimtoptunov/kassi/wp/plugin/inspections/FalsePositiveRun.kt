package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules
import java.io.File

/**
 * Not a regular test: a review aid. Runs every inspection over the popular-plugin corpus
 * (corpus/fetch-popular.sh) and writes every finding to build/reports/fp-run.md for manual triage
 * into FALSE_POSITIVES.md. Skipped unless Gradle is run with -Pkassi.fprun.
 */
class FalsePositiveRun : BasePlatformTestCase() {

    fun testRunOverPopularPlugins() {
        val root = System.getProperty("kassi.fprun.dir")?.let(::File) ?: return
        assertTrue("$root missing. Run corpus/fetch-popular.sh", root.isDirectory)
        val report = File(requireNotNull(System.getProperty("kassi.fprun.report")))

        val lines = mutableListOf<String>()
        var scanned = 0
        for (rule in BuiltInRules.knowledgeBase.rules) {
            val tool = Inspections.byRuleId[rule.id] ?: continue
            // Cheap text prefilter: only files that mention one of the rule's sources can match.
            val needles = rule.source.map { it.lowercase() }
            for (plugin in root.listFiles()!!.filter { it.isDirectory }.sortedBy { it.name }) {
                plugin.walkTopDown().filter { it.isFile && it.extension == "php" }.forEach { php ->
                    val text = php.readText()
                    val lower = text.lowercase()
                    if (needles.none { it in lower }) return@forEach
                    scanned++
                    // Parse without indexing and run the inspection's own visitor (see runInspection).
                    runInspection(project, tool, php.name, text).forEach { f ->
                        val snippet = f.lineText.take(140).replace("|", "\\|")
                        lines += "| ${rule.id} | ${plugin.name} | `${php.relativeTo(plugin).path}:${f.line}` | ${f.message} | `$snippet` |"
                    }
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
