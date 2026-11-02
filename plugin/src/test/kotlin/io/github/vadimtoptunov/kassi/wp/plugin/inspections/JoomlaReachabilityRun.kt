package io.github.vadimtoptunov.kassi.wp.plugin.inspections

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.PhpFile
import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.reach.JoomlaReachability
import io.github.vadimtoptunov.kassi.wp.core.reach.UploadIncludeChain
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

        // File-write CAPABILITY, independent of taint: move_uploaded_file's content is the attacker's
        // upload by definition, so a reachable write = the attacker can place a file. Paired with a
        // reachable include, that is upload -> LFI -> RCE.
        val WRITE_FUNCS = setOf("move_uploaded_file", "file_put_contents", "fwrite", "fputs", "copy", "rename")
        // Joomla static file writers: JFile::write / ::upload / ::copy / ::move.
        val WRITE_STATIC = setOf("write", "upload", "copy", "move")
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

    private fun isWriteCall(node: AstNode): Boolean = when (node.kind) {
        AstKind.CALL -> node.name?.lowercase() in WRITE_FUNCS
        AstKind.STATIC_CALL -> node.name?.lowercase() in WRITE_STATIC
        else -> false
    }

    private fun writeDetail(node: AstNode): String =
        if (node.kind == AstKind.STATIC_CALL) "${node.child(io.github.vadimtoptunov.kassi.wp.core.ast.AstRole.RECEIVER)?.name ?: "?"}::${node.name}()"
        else "${node.name}()"

    /** The nearest enclosing function/method/closure of [node], or the file root for a top-level call. */
    private fun enclosingScope(node: AstNode): AstNode {
        var n = node.parent
        while (n != null) {
            if (n.kind.isFunctionLike) return n
            n = n.parent
        }
        var root = node
        while (root.parent != null) root = root.parent!!
        return root
    }

    fun testRankUnauthFileSinks() {
        val root = System.getProperty("kassi.jreach.dir")?.let(::File) ?: return
        assertTrue("$root missing", root.isDirectory)
        val report = File(requireNotNull(System.getProperty("kassi.jreach.report")))
        val analyzer = TaintAnalyzer(TaintSpec.of(BuiltInRules.knowledgeBase.require("JL-007")))

        val rows = mutableListOf<Row>()
        val caps = mutableListOf<UploadIncludeChain.FileCapability>()
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
                                val loc = "${hit.sink.location.path}:${hit.sink.location.line}"
                                rows += Row(entry.unauth, rank, entry.label, impact, sinkName, hit.taint.label, loc)
                                // An include of request-influenced input is the executable half of a chain.
                                if (hit.sink.kind == AstKind.INCLUDE) {
                                    caps += UploadIncludeChain.FileCapability(plugin.name, UploadIncludeChain.Kind.INCLUDE, entry, sinkName, loc)
                                }
                            }
                        }
                        // Write capability (taint-independent): a reachable file write = the attacker can
                        // place a file. Collected per file, classified by the write's enclosing scope.
                        for (w in ast.walk().filter(::isWriteCall)) {
                            val sc = enclosingScope(w)
                            caps += UploadIncludeChain.FileCapability(
                                plugin.name, UploadIncludeChain.Kind.WRITE,
                                JoomlaReachability.classify(sc, w.location.path), writeDetail(w),
                                "${w.location.path}:${w.location.line}",
                            )
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

        // The combination layer: write × include pairs within one extension (upload -> LFI -> RCE).
        val chains = UploadIncludeChain.candidates(caps).take(MAX_ROWS)
        val writeCount = caps.count { it.kind == UploadIncludeChain.Kind.WRITE }

        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("# Joomla unauthenticated file-sink reachability")
                appendLine()
                appendLine("Files scanned: $filesScanned. Request-input file sinks: ${rows.size} ($unauthCount unauth-reachable leads). Showing top ${top.size}.")
                appendLine()
                appendLine("## Upload -> include chain candidates (write + include in one extension)")
                appendLine()
                appendLine("Write capabilities: $writeCount. Chain candidates: ${chains.size}.")
                appendLine()
                appendLine("| Both unauth | Extension | Write (entry) | Write loc | Include (entry) | Include loc |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                for (c in chains) {
                    appendLine(
                        "| ${if (c.unauth) "YES" else if (c.partlyUnauth) "partial" else "no"} | `${c.ext}` | " +
                            "`${c.write.detail}` (${c.write.entry.label}) | `${c.write.location}` | " +
                            "`${c.include.detail}` (${c.include.entry.label}) | `${c.include.location}` |",
                    )
                }
                appendLine()
                appendLine("## All request-input file sinks (ranked)")
                appendLine()
                appendLine("| Unauth | Entry | Impact | Sink | Taint | Location |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                for (r in top) {
                    appendLine("| ${if (r.unauth) "YES" else "no"} | ${r.entry} | ${r.impact} | `${r.sink}` | ${r.taint} | `${r.location}` |")
                }
            },
        )
        println("[joomla-reachability] files=$filesScanned sinks=${rows.size} unauth=$unauthCount writes=$writeCount chains=${chains.size} -> ${report.absolutePath}")
    }
}
