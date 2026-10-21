package io.github.vadimtoptunov.kassi.wp.core.kb

import io.github.vadimtoptunov.kassi.wp.core.model.PluginOverlay
import io.github.vadimtoptunov.kassi.wp.core.model.Reference
import io.github.vadimtoptunov.kassi.wp.core.model.Rule
import io.github.vadimtoptunov.kassi.wp.core.model.RuleParams
import io.github.vadimtoptunov.kassi.wp.core.model.Severity
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.exceptions.YamlEngineException

class RuleValidationException(val origin: String, val errors: List<String>) :
    RuntimeException("Invalid rule $origin:\n" + errors.joinToString("\n") { "  - $it" })

/**
 * Parses and validates one rule YAML file (the unified rule format).
 *
 * Validated: every core key, and the `plugin:` overlay. Any other top-level key must be a mapping
 * (another consumer's overlay) and is skipped without inspection; a non-mapping unknown key is
 * rejected, which catches typos in core keys (`severty: high`).
 *
 * All errors of a file are reported together.
 */
object RuleParser {
    private val ID = Regex("""^WP-\d{3}$""")
    private val CVE = Regex("""^CVE-\d{4}-\d{4,}$""")
    private val CORPUS = Regex("""^WP-\d{3}/case-\d{2}$""")
    private val URL = Regex("""^https://\S+$""")

    val SUPPORTED_PLATFORMS = setOf("wordpress")
    private val QUICK_FIX_NONE = "none"

    private val CORE_KEYS = setOf(
        "id", "platform", "cwe", "severity", "summary", "source", "sanitizers", "sink",
        "precision_notes", "references", "params",
    )
    private const val PLUGIN_KEY = "plugin"
    private val PLUGIN_KEYS = setOf("description_html", "quick_fix")
    private val REFERENCE_KEYS = setOf("cve", "fix_commit", "corpus")

    fun parse(yaml: String, origin: String): Rule {
        val root = try {
            Load(LoadSettings.builder().setLabel(origin).build()).loadFromString(yaml)
        } catch (e: YamlEngineException) {
            throw RuleValidationException(origin, listOf("YAML syntax: ${e.message}"))
        }
        val map = root as? Map<*, *> ?: throw RuleValidationException(origin, listOf("top level must be a mapping"))
        val validator = Validator(map)
        return validator.build() ?: throw RuleValidationException(origin, validator.errors)
    }

    private class Validator(val m: Map<*, *>) {
        val errors = mutableListOf<String>()

        fun build(): Rule? {
            for (key in m.keys) {
                val k = key.toString()
                if (k in CORE_KEYS || k == PLUGIN_KEY) continue
                if (m[key] !is Map<*, *>) errors += "unknown key '$k' (overlays must be mappings; is it a typo?)"
            }

            val id = string("id")?.also { if (!ID.matches(it)) errors += "id '$it' must match WP-NNN" }
            val platform = string("platform")?.also {
                if (it !in SUPPORTED_PLATFORMS) errors += "platform '$it' is not supported (expected one of $SUPPORTED_PLATFORMS)"
            }
            val cwe = when (val v = m["cwe"]) {
                null -> null.also { errors += "missing 'cwe'" }
                is Int -> v.takeIf { it > 0 } ?: null.also { errors += "cwe must be a positive integer" }
                is Long -> v.toInt().takeIf { it > 0 } ?: null.also { errors += "cwe must be a positive integer" }
                else -> null.also { errors += "cwe must be an integer (CWE number without the 'CWE-' prefix)" }
            }
            val severity = string("severity")?.let { s ->
                Severity.entries.firstOrNull { it.name.equals(s, ignoreCase = true) }
                    ?: null.also { errors += "severity '$s' must be one of ${Severity.entries.map { it.name.lowercase() }}" }
            }
            val summary = string("summary")
            val source = stringList("source", required = true)
            val sanitizers = stringList("sanitizers", required = true)
            val sink = string("sink")
            val precisionNotes = string("precision_notes")
            val references = references()
            val params = when (val p = m["params"]) {
                null -> RuleParams(emptyMap())
                is Map<*, *> -> RuleParams(p.entries.associate { (k, v) -> k.toString() to v })
                else -> null.also { errors += "params must be a mapping" }
            }
            val plugin = pluginOverlay()

            if (errors.isNotEmpty()) return null
            return Rule(
                id = id!!, platform = platform!!, cwe = cwe!!, severity = severity!!, summary = summary!!,
                source = source!!, sanitizers = sanitizers!!, sink = sink!!, precisionNotes = precisionNotes!!,
                references = references!!, params = params!!, plugin = plugin,
            )
        }

        private fun string(key: String, map: Map<*, *> = m, path: String = key): String? {
            val v = map[key]
            return when {
                v == null -> null.also { errors += "missing '$path'" }
                v !is String -> null.also { errors += "'$path' must be a string" }
                v.isBlank() -> null.also { errors += "'$path' must not be blank" }
                else -> v.trim()
            }
        }

        private fun optionalString(key: String, map: Map<*, *>, path: String): String? =
            if (map[key] == null) null else string(key, map, path)

        private fun stringList(key: String, required: Boolean): List<String>? {
            val v = m[key]
            return when {
                v == null -> if (required) null.also { errors += "missing '$key' (use [] when empty)" } else emptyList()
                v !is List<*> -> null.also { errors += "'$key' must be a list" }
                v.any { it !is String || it.isBlank() } -> null.also { errors += "'$key' must contain only non-blank strings" }
                else -> v.map { (it as String).trim() }
            }
        }

        private fun references(): List<Reference>? {
            val v = m["references"]
            if (v !is List<*> || v.isEmpty()) {
                errors += "'references' must be a non-empty list (every rule cites at least one real CVE)"
                return null
            }
            val before = errors.size
            val refs = v.mapIndexedNotNull { i, item ->
                val path = "references[$i]"
                if (item !is Map<*, *>) {
                    errors += "$path must be a mapping"
                    return@mapIndexedNotNull null
                }
                item.keys.map { it.toString() }.filter { it !in REFERENCE_KEYS }.forEach { errors += "$path: unknown key '$it'" }
                val cve = string("cve", item, "$path.cve")?.also { if (!CVE.matches(it)) errors += "$path.cve '$it' must match CVE-YYYY-NNNN" }
                val fix = optionalString("fix_commit", item, "$path.fix_commit")
                    ?.also { if (!URL.matches(it)) errors += "$path.fix_commit must be an https URL" }
                val corpus = optionalString("corpus", item, "$path.corpus")
                    ?.also { if (!CORPUS.matches(it)) errors += "$path.corpus '$it' must look like WP-NNN/case-NN" }
                cve?.let { Reference(it, fix, corpus) }
            }
            return refs.takeIf { errors.size == before }
        }

        private fun pluginOverlay(): PluginOverlay? {
            val v = m[PLUGIN_KEY] ?: return null
            if (v !is Map<*, *>) {
                errors += "'plugin' must be a mapping"
                return null
            }
            v.keys.map { it.toString() }.filter { it !in PLUGIN_KEYS }.forEach { errors += "plugin: unknown key '$it'" }
            val html = string("description_html", v, "plugin.description_html")
                ?.also { if (!it.startsWith("inspectionDescriptions/") || !it.endsWith(".html")) errors += "plugin.description_html must be inspectionDescriptions/<Name>.html" }
            val fix = string("quick_fix", v, "plugin.quick_fix")
            return if (html != null && fix != null) PluginOverlay(html, fix.takeUnless { it == QUICK_FIX_NONE }) else null
        }
    }
}
