package io.github.vadimtoptunov.kassi.wp.core.model

/**
 * The core section of a rule (shared by every consumer of the rule base), loaded from one YAML file
 * of the kassi-wp-rules repo by [io.github.vadimtoptunov.kassi.wp.core.kb.RuleParser].
 *
 * @property params rule-specific detection knobs (e.g. which HTTP methods count as read-only).
 *   Shape is defined per rule and read through [RuleParams]; the validator only checks it is a mapping.
 */
data class Rule(
    val id: String,
    val platform: String,
    val cwe: Int,
    val severity: Severity,
    val summary: String,
    val source: List<String>,
    val sanitizers: List<String>,
    val sink: String,
    val precisionNotes: String,
    val references: List<Reference>,
    val params: RuleParams = RuleParams(emptyMap()),
    /** The `plugin:` overlay; null when the rule has none (other consumers' overlays are skipped). */
    val plugin: PluginOverlay? = null,
)

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

data class Reference(
    val cve: String,
    val fixCommit: String?,
    val corpus: String?,
)

data class PluginOverlay(
    /** Resource path of the inspection description, e.g. `inspectionDescriptions/WP004.html`. */
    val descriptionHtml: String,
    /** Quick-fix id, or null for `none`. */
    val quickFix: String?,
)

/** Typed read access to a rule's `params:` mapping. Missing keys yield empty values, never errors. */
class RuleParams(private val raw: Map<String, Any?>) {
    fun strings(key: String): List<String> = (raw[key] as? List<*>)?.map { it.toString() } ?: emptyList()

    fun stringMap(key: String): Map<String, String> =
        (raw[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()

    val keys: Set<String> get() = raw.keys

    override fun equals(other: Any?): Boolean = other is RuleParams && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()
    override fun toString(): String = "RuleParams($raw)"
}
