package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.reach.JoomlaReachability
import io.github.vadimtoptunov.kassi.wp.plugin.psi.PhpPsiAstNode
import io.github.vadimtoptunov.kassi.wp.plugin.rules.BuiltInRules
import java.io.File

/**
 * Not a regular test: the Joomla reachability research run (skipped unless -Pkassi.jreach). It proves
 * the FIRST half of a file-inclusion chain — request input reaching a file sink (include/require, a
 * file read/write, or a PHAR-triggering filesystem call) — and then layers [JoomlaReachability] on top
 * to say HOW an unauthenticated attacker reaches the sink's scope. Rows are ranked unauth-first,
 * RCE-first, and written to build/reports/joomla-reachability.md for manual triage.
 *
 * The taint semantics come from the JL-007 rule (Joomla file-traversal sources + sanitizers); the sink
 * set here is wider than JL-007 on purpose, to include the PHAR deserialization vectors.
 */
class JoomlaReachabilityRun : BasePlatformTestCase() {

    private companion object {
        const val MAX_ROWS = 80
        // include/require is RCE; these read/write the file contents; the PHAR set triggers
        // deserialization when handed a phar:// path (RCE only with a reachable gadget).
        val READ = setOf("readfile", "file_get_contents", "file", "show_source", "highlight_file", "fpassthru", "readgzfile")
        val WRITE = setOf("file_put_contents", "fopen")
        val PHAR = setOf(
            "file_exists", "is_file", "is_dir", "is_readable", "is_writable", "filesize", "filemtime",
            "fileatime", "filectime", "fileperms", "getimagesize", "exif_read_data", "exif_imagetype",
            "md5_file", "sha1_file", "hash_file", "stat", "lstat", "touch", "copy", "unlink", "mime_content_type",
        )
    }

    private data class Row(
        val unauth: Boolean,
        val impactRank: Int,
        val entry: String,
        val impact: String,
        val sink: String,
        val taint: String,
        val location: String,
    )

    private fun impactOf(node: AstNode): Pair<String, Int> = when {
        node.kind == AstKind.INCLUDE -> "RCE (include/require)" to 3
        node.name?.lowercase() in PHAR -> "PHAR deserialization vector" to 2
        node.name?.lowercase() in WRITE -> "file write/open" to 2
        else -> "arbitrary file read" to 1
    }

    private fun sinkArguments(node: AstNode): List<AstNode> = when {
        node.kind == AstKind.INCLUDE -> node.arguments
        node.kind == AstKind.CALL && node.name?.lowercase().let { it in READ || it in WRITE || it in PHAR } ->
            listOfNotNull(node.arguments.firstOrNull())
        else -> emptyList()
    }

    fun testRankUnauthFileSinks() {
        val root = System.getProperty("kassi.jreach.dir")?.let(::File) ?: return
        assertTrue("$root missing", root.isDirectory)
        val report = File(requireNotNull(System.getProperty("kassi.jreach.report")))
        val analyzer = TaintAnalyzer(TaintSpec.of(BuiltInRules.knowledgeBase.require("JL-007")))

        val rows = mutableListOf<Row>()
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
                for ((_, psi) in inProject) {
                    if (psi !is PhpFile) continue
                    filesScanned++
                    val ast = runCatching { PhpPsiAstNode.wrap(psi) }.getOrNull() ?: continue
                    runCatching {
                        for (scope in analyzer.scopes(ast)) {
                            for (hit in analyzer.analyze(scope, ::sinkArguments)) {
                                val entry = JoomlaReachability.classify(scope, hit.sink.location.path)
                                val (impact, rank) = impactOf(hit.sink)
                                val sinkName = if (hit.sink.kind == AstKind.INCLUDE) hit.sink.name.orEmpty() else "${hit.sink.name}()"
                                rows += Row(
                                    unauth = entry.unauth,
                                    impactRank = rank,
                                    entry = entry.label,
                                    impact = impact,
                                    sink = sinkName,
                                    taint = hit.taint.label,
                                    location = "${hit.sink.location.path}:${hit.sink.location.line}",
                                )
                            }
                        }
                    }
                }
                WriteAction.runAndWait<Throwable> {
                    inProject.forEach { (_, psi) -> runCatching { psi.virtualFile?.delete(this) } }
                }
            }
        }

        val ranked = rows.sortedWith(
            compareByDescending<Row> { it.unauth }.thenByDescending { it.impactRank }.thenBy { it.location },
        )
        val unauthCount = rows.count { it.unauth }
        val top = ranked.take(MAX_ROWS)

        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("# Joomla unauthenticated file-sink reachability")
                appendLine()
                appendLine("Files scanned: $filesScanned. Request-input file sinks: ${rows.size} ($unauthCount unauth-reachable leads). Showing top ${top.size}.")
                appendLine()
                appendLine("| Unauth | Entry | Impact | Sink | Taint | Location |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                for (r in top) {
                    appendLine("| ${if (r.unauth) "YES" else "no"} | ${r.entry} | ${r.impact} | `${r.sink}` | ${r.taint} | `${r.location}` |")
                }
            },
        )
        println("[joomla-reachability] files=$filesScanned sinks=${rows.size} unauth=$unauthCount -> ${report.absolutePath}")
    }
}
