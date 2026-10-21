package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Review aid, not a regular test (skipped unless Gradle runs with -Pkassi.recall). For every
 * kassi-wp-corpus case it checks the rule on the FULL source of the plugin release, not just the
 * excerpt:
 *  - recall: every line the rule flags in vulnerable.php is also flagged in the full vulnerable file;
 *  - precision: no line of the fixed excerpt is flagged in the full fixed file;
 * and counts the rule's findings over the whole vulnerable and fixed plugin for context.
 * Writes plugin/build/reports/recall-run.md.
 */
class RecallRun : BasePlatformTestCase() {

    private fun meta(dir: File, key: String): String =
        Regex("""^$key:\s*"?([^"\n]+)"?""", RegexOption.MULTILINE).find(File(dir, "meta.yml").readText())!!.groupValues[1].trim()

    private fun codeLines(f: File): Set<String> =
        f.readLines().drop(4).map { it.trim() }.filter { it.length > 3 && !it.startsWith("//") }.toSet()

    private fun pluginFindings(tool: com.intellij.codeInspection.LocalInspectionTool, root: File): Int =
        root.walkTopDown().filter { it.isFile && it.extension == "php" }.sumOf { php ->
            runCatching { runInspection(project, tool, php.name, php.readText()).size }.getOrDefault(0)
        }

    fun testRecallOnFullSources() {
        val recall = System.getProperty("kassi.recall.dir")?.let(::File) ?: return
        val corpus = File(requireNotNull(System.getProperty("kassi.corpus.dir")))
        val report = File(requireNotNull(System.getProperty("kassi.recall.report")))
        val rows = mutableListOf<String>()
        val notes = mutableListOf<String>()
        var pass = 0
        var total = 0
        for (caseDir in corpus.listFiles { f -> f.isDirectory && f.name.startsWith("WP-") }!!.sortedBy { it.name }
            .flatMap { r -> r.listFiles { f -> f.isDirectory }!!.sortedBy { it.name } }) {
            val ruleId = caseDir.parentFile.name
            val tool = Inspections.byRuleId[ruleId] ?: continue
            total++
            val slug = meta(caseDir, "plugin_slug")
            val path = meta(caseDir, "file")
            val vRoot = File(recall, "$slug@${meta(caseDir, "version_before")}")
            val fRoot = File(recall, "$slug@${meta(caseDir, "version_after")}")
            val vFile = File(vRoot, path)
            val fFile = File(fRoot, path)
            if (!vFile.isFile || !fFile.isFile) {
                rows += "| $ruleId/${caseDir.name} | $slug | — | — | — | — | missing source (run corpus/fetch-recall.sh) |"
                continue
            }
            val expected = runInspection(project, tool, "excerpt.php", File(caseDir, "vulnerable.php").readText()).map { it.lineText }.toSet()
            val vFull = runInspection(project, tool, vFile.name, vFile.readText())
            val fFull = runInspection(project, tool, fFile.name, fFile.readText())
            val found = expected.filter { e -> vFull.any { it.lineText == e } }
            val fixedRegion = codeLines(File(caseDir, "fixed.php"))
            val stillFlagged = fFull.filter { it.lineText in fixedRegion }
            val ok = found.size == expected.size && expected.isNotEmpty() && stillFlagged.isEmpty()
            if (ok) pass++
            val vPlugin = pluginFindings(tool, vRoot)
            val fPlugin = pluginFindings(tool, fRoot)
            rows += "| $ruleId/${caseDir.name} | $slug | ${found.size}/${expected.size} | ${stillFlagged.size} | " +
                "${vFull.size} → ${fFull.size} | $vPlugin → $fPlugin | ${if (ok) "OK" else "**FAIL**"} |"
            (expected - found.toSet()).forEach { notes += "- $ruleId/${caseDir.name}: not flagged in full file: `$it`" }
            stillFlagged.forEach { notes += "- $ruleId/${caseDir.name}: still flagged in full FIXED file line ${it.line}: `${it.lineText.take(120)}`" }
            (fFull.filter { it !in stillFlagged }).forEach { notes += "- $ruleId/${caseDir.name}: other finding in fixed file `$path:${it.line}`: `${it.lineText.take(120)}`" }
        }
        report.parentFile.mkdirs()
        report.writeText(
            "# Recall on full sources\n\n$pass / $total cases OK.\n\n" +
                "| Case | Plugin | CVE lines flagged in full vulnerable file | Fixed-region lines still flagged | Findings in file (vuln → fixed) | Findings in whole plugin (vuln → fixed) | Result |\n" +
                "| --- | --- | --- | --- | --- | --- | --- |\n" + rows.joinToString("\n") +
                "\n\n## Notes\n\n" + notes.joinToString("\n") + "\n",
        )
        println("Recall run: $pass/$total -> $report")
    }
}
