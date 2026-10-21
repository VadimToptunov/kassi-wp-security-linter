package io.github.vadimtoptunov.kassi.wp.core.report

import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/** One finding as it appears in the report. [path] is project-relative; [line] is 1-based. */
data class ReportEntry(
    val rule: Rule,
    val path: String,
    val line: Int,
    val snippet: String,
    val message: String,
    val weak: Boolean,
)

/**
 * The project security report (Markdown for people, JSON for CI and compliance evidence).
 *
 * Deterministic by design: no timestamps, no machine-specific paths, entries sorted by path, line,
 * rule and message. The same plugin version over the same code gives byte-identical output.
 */
class Report(
    val toolVersion: String,
    val rulesChecked: List<Rule>,
    entries: List<ReportEntry>,
    val filesScanned: Int,
) {
    val entries: List<ReportEntry> = entries.sortedWith(compareBy({ it.path }, { it.line }, { it.rule.id }, { it.message }))

    fun markdown(): String = buildString {
        appendLine("# Kassi WP Security Report")
        appendLine()
        appendLine("Tool: Kassi Security Inspections for WordPress $toolVersion · rules: ${rulesChecked.joinToString(", ") { it.id }} · files scanned: $filesScanned")
        appendLine()
        val strong = entries.count { !it.weak }
        appendLine("**${entries.size} finding(s)**: $strong warning(s), ${entries.size - strong} weak.")
        appendLine()
        if (entries.isNotEmpty()) {
            appendLine("| Rule | CWE | Severity | Location | Finding | Code |")
            appendLine("| --- | --- | --- | --- | --- | --- |")
            for (e in entries) {
                val sev = e.rule.severity.name.lowercase() + if (e.weak) " (weak)" else ""
                appendLine(
                    "| ${e.rule.id} | [CWE-${e.rule.cwe}](${cweUrl(e.rule.cwe)}) | $sev | `${md(e.path)}:${e.line}` | ${md(e.message)} | `${md(e.snippet).replace("`", "'")}` |",
                )
            }
            appendLine()
        }
        appendLine("## Rules")
        appendLine()
        for (r in rulesChecked) {
            val refs = r.references.joinToString(", ") { "[${it.cve}](${cveUrl(it.cve)})" }
            appendLine("- **${r.id}**: ${md(r.summary)} ([CWE-${r.cwe}](${cweUrl(r.cwe)}), ${r.severity.name.lowercase()}). Examples: $refs. [Rule](${ruleUrl(r)})")
        }
    }

    fun json(): String = buildString {
        append("{\n")
        append("  \"tool\": ").append(str("Kassi Security Inspections for WordPress")).append(",\n")
        append("  \"toolVersion\": ").append(str(toolVersion)).append(",\n")
        append("  \"filesScanned\": ").append(filesScanned).append(",\n")
        append("  \"rules\": [")
        rulesChecked.forEachIndexed { i, r ->
            append(if (i == 0) "\n" else ",\n")
            append("    {\"id\": ").append(str(r.id))
                .append(", \"cwe\": ").append(r.cwe)
                .append(", \"severity\": ").append(str(r.severity.name.lowercase()))
                .append(", \"summary\": ").append(str(r.summary))
                .append(", \"url\": ").append(str(ruleUrl(r)))
                .append(", \"references\": [").append(r.references.joinToString(", ") { str(it.cve) }).append("]}")
        }
        append(if (rulesChecked.isEmpty()) "],\n" else "\n  ],\n")
        append("  \"findings\": [")
        entries.forEachIndexed { i, e ->
            append(if (i == 0) "\n" else ",\n")
            append("    {\"rule\": ").append(str(e.rule.id))
                .append(", \"cwe\": ").append(e.rule.cwe)
                .append(", \"severity\": ").append(str(e.rule.severity.name.lowercase()))
                .append(", \"weak\": ").append(e.weak)
                .append(", \"file\": ").append(str(e.path))
                .append(", \"line\": ").append(e.line)
                .append(", \"message\": ").append(str(e.message))
                .append(", \"snippet\": ").append(str(e.snippet))
                .append(", \"cweUrl\": ").append(str(cweUrl(e.rule.cwe)))
                .append(", \"references\": [").append(e.rule.references.joinToString(", ") { str(cveUrl(it.cve)) }).append("]}")
        }
        append(if (entries.isEmpty()) "]\n" else "\n  ]\n")
        append("}\n")
    }

    companion object {
        const val RULES_REPO = "https://github.com/VadimToptunov/kassi-wp-rules/blob/main/rules"

        fun cweUrl(cwe: Int) = "https://cwe.mitre.org/data/definitions/$cwe.html"
        fun cveUrl(cve: String) = "https://nvd.nist.gov/vuln/detail/$cve"
        fun ruleUrl(r: Rule) = "$RULES_REPO/${r.platform}/${r.id}.yml"

        private fun md(s: String) = s.replace("|", "\\|").replace("\n", " ")

        fun str(s: String): String = buildString {
            append('"')
            for (c in s) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
    }
}
