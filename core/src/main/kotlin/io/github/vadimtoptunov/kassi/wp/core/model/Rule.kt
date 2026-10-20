package io.github.vadimtoptunov.kassi.wp.core.model

/**
 * The core section of a rule (shared by every consumer of the rule base). Consumer overlays
 * (`plugin:` and any others) are not part of this type. Populated by the `kb` loader from
 * the kassi-wp-rules YAML (Stage 1); defined here so the shape is fixed from Stage 0.
 */
data class Rule(
    val id: String,
    val platform: String,
    val cwe: Int,
    val severity: Severity,
    val summary: String,
    val references: List<Reference>,
)

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

data class Reference(
    val cve: String,
    val fixCommit: String?,
    val corpus: String?,
)
