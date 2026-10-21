package io.github.vadimtoptunov.kassi.wp.core.report

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.vadimtoptunov.kassi.wp.core.model.Reference
import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import io.github.vadimtoptunov.kassi.wp.core.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportTest {
    private val rule = Rule(
        id = "WP-005", platform = "wordpress", cwe = 89, severity = Severity.CRITICAL, summary = "SQL | built",
        source = emptyList(), sanitizers = emptyList(), sink = "s", precisionNotes = "n",
        references = listOf(Reference("CVE-2022-1006", null, "WP-005/case-02")),
    )
    private val entries = listOf(
        ReportEntry(rule, "b.php", 9, "\$wpdb->query(\"x\" . \$_GET['id']);", "msg \"quoted\"\nnext", weak = false),
        ReportEntry(rule, "a.php", 3, "tab\there | pipe `tick`", "m2", weak = true),
    )
    private val report = Report("0.1.0", listOf(rule), entries, filesScanned = 2)

    @Test
    fun `entries are sorted and output is deterministic`() {
        assertEquals(listOf("a.php", "b.php"), report.entries.map { it.path })
        val again = Report("0.1.0", listOf(rule), entries.reversed(), filesScanned = 2)
        assertEquals(report.markdown(), again.markdown())
        assertEquals(report.json(), again.json())
        assertFalse(Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(report.json()), "no timestamps")
    }

    @Test
    fun `json is valid and carries rule, cwe, location, snippet and reference links`() {
        val tree = ObjectMapper().readTree(report.json())
        assertEquals(2, tree["filesScanned"].asInt())
        val first = tree["findings"][0]
        assertEquals("WP-005", first["rule"].asText())
        assertEquals(89, first["cwe"].asInt())
        assertEquals("a.php", first["file"].asText())
        assertEquals(3, first["line"].asInt())
        assertTrue(first["weak"].asBoolean())
        assertEquals("tab\there | pipe `tick`", first["snippet"].asText())
        assertEquals("msg \"quoted\"\nnext", tree["findings"][1]["message"].asText())
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2022-1006", first["references"][0].asText())
        assertEquals("https://cwe.mitre.org/data/definitions/89.html", first["cweUrl"].asText())
    }

    @Test
    fun `empty report is valid json`() {
        val tree = ObjectMapper().readTree(Report("0.1.0", emptyList(), emptyList(), 0).json())
        assertEquals(0, tree["findings"].size())
    }

    @Test
    fun `markdown escapes table cells`() {
        val md = report.markdown()
        assertTrue("SQL \\| built" in md)
        assertTrue("`a.php:3`" in md)
        assertTrue("[CWE-89](https://cwe.mitre.org/data/definitions/89.html)" in md)
        assertTrue("1 warning(s), 1 weak" in md)
    }
}
