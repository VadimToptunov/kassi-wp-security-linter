package io.github.vadimtoptunov.kassi.wp.core.reach

/**
 * A true two-link file-inclusion chain within one Joomla extension: an attacker who can WRITE a file
 * (upload, or a request-influenced file write) and, somewhere in the same extension, make the code
 * INCLUDE a request-influenced path, can place a PHP file and then execute it — remote code execution.
 *
 * Neither half is reportable alone (a write may land in a known dir, an include may look picked-over),
 * but their co-existence in one installable unit is the classic Joomla upload→LFI→RCE shape. This is
 * the "combination" layer: it pairs the two capabilities and ranks the pairs by reachability.
 */
object UploadIncludeChain {

    enum class Kind { WRITE, INCLUDE }

    /**
     * One file capability found in an extension.
     * @param ext      the extension (installable unit) the capability lives in — the grouping key.
     * @param entry    how an unauthenticated attacker reaches the capability's scope.
     * @param detail   the sink, e.g. `move_uploaded_file()` or `require_once`.
     */
    data class FileCapability(
        val ext: String,
        val kind: Kind,
        val entry: JoomlaReachability.Entry,
        val detail: String,
        val location: String,
    )

    /** A write paired with an include in the same extension: place a file, then execute it. */
    data class Candidate(val write: FileCapability, val include: FileCapability) {
        val ext: String get() = write.ext
        /** Both halves reachable without authentication = the dangerous case. */
        val unauth: Boolean get() = write.entry.unauth && include.entry.unauth
        /** Either half reachable unauth — still worth a look (the other half may be weakly gated). */
        val partlyUnauth: Boolean get() = write.entry.unauth || include.entry.unauth
    }

    /** Every write×include pair within the same extension, ranked fully-unauth first. */
    fun candidates(caps: List<FileCapability>): List<Candidate> =
        caps.groupBy { it.ext }.values.flatMap { group ->
            val writes = group.filter { it.kind == Kind.WRITE }
            val includes = group.filter { it.kind == Kind.INCLUDE }
            writes.flatMap { w -> includes.map { i -> Candidate(w, i) } }
        }.sortedWith(
            compareByDescending<Candidate> { it.unauth }
                .thenByDescending { it.partlyUnauth }
                .thenBy { it.ext },
        )
}
