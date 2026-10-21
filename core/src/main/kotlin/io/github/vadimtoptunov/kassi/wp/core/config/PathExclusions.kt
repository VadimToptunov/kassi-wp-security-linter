package io.github.vadimtoptunov.kassi.wp.core.config

/**
 * Path exclusions for inspections and the report. Paths are project-relative and `/`-separated.
 *  - `vendor/`       a directory name: excludes every `vendor` directory at any depth
 *  - `src/legacy`    a prefix of the relative path
 *  - `*.min.php`     a glob (`*` within a segment, `**` across segments) over the whole path
 */
class PathExclusions(patterns: List<String>) {
    private val dirNames = mutableSetOf<String>()
    private val prefixes = mutableListOf<String>()
    private val globs = mutableListOf<Regex>()

    init {
        for (raw in patterns.map { it.trim().replace('\\', '/') }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            when {
                '*' in raw -> globs += glob(raw.removePrefix("/"))
                raw.endsWith("/") && raw.trimEnd('/').none { it == '/' } -> dirNames += raw.trimEnd('/')
                else -> prefixes += raw.removePrefix("/").trimEnd('/')
            }
        }
    }

    fun isExcluded(relativePath: String): Boolean {
        val p = relativePath.replace('\\', '/').removePrefix("/")
        val segments = p.split('/').dropLast(1)
        return segments.any { it in dirNames } ||
            prefixes.any { p == it || p.startsWith("$it/") } ||
            globs.any { it.matches(p) || it.matches(p.substringAfterLast('/')) }
    }

    companion object {
        val DEFAULT = listOf("vendor/", "node_modules/", "tests/")

        private fun glob(g: String): Regex {
            val sb = StringBuilder()
            var i = 0
            while (i < g.length) {
                when {
                    g.startsWith("**", i) -> { sb.append(".*"); i += 2 }
                    g[i] == '*' -> { sb.append("[^/]*"); i++ }
                    else -> { sb.append(Regex.escape(g[i].toString())); i++ }
                }
            }
            return Regex(sb.toString())
        }
    }
}
