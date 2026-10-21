package io.github.vadimtoptunov.kassi.wp.core.kb

import io.github.vadimtoptunov.kassi.wp.core.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleParserTest {

    private val valid = """
        id: WP-004
        platform: wordpress
        cwe: 862
        severity: high
        summary: REST route without permission_callback
        source: [register_rest_route]
        sanitizers: []
        sink: permission_callback_missing
        precision_notes: Silent when not proven.
        params:
          read_only_methods: [GET, HEAD]
          method_constants:
            WP_REST_Server::CREATABLE: POST
        references:
          - cve: CVE-2022-0218
            fix_commit: https://plugins.trac.wordpress.org/changeset/2656984
            corpus: WP-004/case-01
        plugin:
          description_html: inspectionDescriptions/WP004.html
          quick_fix: add_permission_callback
        other_consumer:
          anything: [goes, here]
          nested: { deeply: true }
    """.trimIndent()

    private fun errorsOf(yaml: String): List<String> =
        assertFailsWith<RuleValidationException> { RuleParser.parse(yaml, "t.yml") }.errors

    @Test
    fun `parses core, params and plugin overlay`() {
        val rule = RuleParser.parse(valid, "t.yml")
        assertEquals("WP-004", rule.id)
        assertEquals(862, rule.cwe)
        assertEquals(Severity.HIGH, rule.severity)
        assertEquals(listOf("register_rest_route"), rule.source)
        assertEquals(emptyList(), rule.sanitizers)
        assertEquals(listOf("GET", "HEAD"), rule.params.strings("read_only_methods"))
        assertEquals(mapOf("WP_REST_Server::CREATABLE" to "POST"), rule.params.stringMap("method_constants"))
        assertEquals("CVE-2022-0218", rule.references.single().cve)
        assertEquals("WP-004/case-01", rule.references.single().corpus)
        assertEquals("add_permission_callback", rule.plugin?.quickFix)
    }

    @Test
    fun `other consumers' overlays are skipped without validation`() {
        // `other_consumer` above holds arbitrary content and does not fail the parse.
        RuleParser.parse(valid, "t.yml")
    }

    @Test
    fun `quick_fix none means no fix and the plugin overlay is optional`() {
        assertNull(RuleParser.parse(valid.replace("quick_fix: add_permission_callback", "quick_fix: none"), "t").plugin?.quickFix)
        val noPlugin = valid.substringBefore("plugin:\n") + "other_consumer: {}\n"
        assertNull(RuleParser.parse(noPlugin, "t").plugin)
    }

    @Test
    fun `a typo in a core key is rejected, not silently ignored`() {
        val errors = errorsOf(valid.replace("severity: high", "severty: high"))
        assertTrue(errors.any { "unknown key 'severty'" in it }, errors.toString())
        assertTrue(errors.any { "missing 'severity'" in it }, errors.toString())
    }

    @Test
    fun `every core field is required and all errors are reported together`() {
        val dropped = listOf("platform:", "cwe:", "precision_notes:", "sanitizers:")
        val errors = errorsOf(valid.lines().filterNot { line -> dropped.any { line.startsWith(it) } }.joinToString("\n"))
        assertTrue(errors.containsAll(listOf("missing 'platform'", "missing 'cwe'", "missing 'precision_notes'", "missing 'sanitizers' (use [] when empty)")), errors.toString())
    }

    @Test
    fun `field formats are validated`() {
        assertTrue(errorsOf(valid.replace("id: WP-004", "id: WP4")).any { "WP-NNN" in it })
        assertTrue(errorsOf(valid.replace("platform: wordpress", "platform: drupal")).any { "not supported" in it })
        assertTrue(errorsOf(valid.replace("cwe: 862", "cwe: CWE-862")).any { "cwe must be an integer" in it })
        assertTrue(errorsOf(valid.replace("severity: high", "severity: urgent")).any { "severity 'urgent'" in it })
        assertTrue(errorsOf(valid.replace("CVE-2022-0218", "2022-0218")).any { "CVE-YYYY-NNNN" in it })
        assertTrue(errorsOf(valid.replace("https://plugins", "http://plugins")).any { "https URL" in it })
        assertTrue(errorsOf(valid.replace("corpus: WP-004/case-01", "corpus: case1")).any { "WP-NNN/case-NN" in it })
        assertTrue(errorsOf(valid.replace("inspectionDescriptions/WP004.html", "WP004.html")).any { "description_html" in it })
        assertTrue(errorsOf(valid.replace("  quick_fix: add_permission_callback", "  quick_fix: x\n  extra: 1")).any { "plugin: unknown key 'extra'" in it })
    }

    @Test
    fun `a rule without a CVE reference is rejected`() {
        val noRefs = valid.replace(Regex("references:\\n(  .*\\n)+"), "references: []\n")
        assertTrue(errorsOf(noRefs).any { "at least one real CVE" in it })
    }

    @Test
    fun `YAML syntax errors are reported, not thrown raw`() {
        assertTrue(errorsOf("id: [unclosed").single().startsWith("YAML syntax"))
        assertEquals(listOf("top level must be a mapping"), errorsOf("- a\n- b"))
    }

    @Test
    fun `knowledge base rejects duplicate ids across files`() {
        val e = assertFailsWith<KnowledgeBaseException> {
            KnowledgeBase.load(listOf(RuleSource("a.yml", valid), RuleSource("b.yml", valid)))
        }
        assertTrue(e.errors.single().contains("duplicate rule id WP-004"), e.errors.toString())
    }
}
