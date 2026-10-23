package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.hooks.CallTarget
import io.github.vadimtoptunov.kassi.wp.core.hooks.HandlerFacts
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-011: an uploaded file is stored (`params.sink_functions`, e.g. move_uploaded_file) and no file
 * type check (`sanitizers:` wp_check_filetype(_and_ext), wp_handle_upload, getimagesize, finfo, an
 * extension read via pathinfo, ...) appears anywhere in the same function.
 *
 * Silent when the destination's extension is fixed by a string literal (`… . '.pdf'`), because the
 * uploader cannot choose the file type then.
 */
class UnsafeUploadCheck(private val rule: Rule) : FileCheck {
    private val sinks = rule.params.strings("sink_functions").map { it.lowercase() }.toSet()
    private val destArg = rule.params.strings("destination_argument").firstOrNull()?.toInt() ?: 1
    private val checks = CallTarget.parseAll(rule.sanitizers)
    private val adminChecks = CallTarget.parseAll(rule.params.strings("admin_checks"))
    /** `params.filename_neutralisers`: helpers that defang dangerous names (e.g. `*antiscript*`). */
    private val neutralisers = CallTarget.parseAll(rule.params.strings("filename_neutralisers"))
    private val scopes = TaintAnalyzer(TaintSpec(emptyList(), emptySet(), emptySet(), emptySet(), emptySet()))

    override fun checkFile(file: AstNode): List<Finding> = scopes.scopes(file).flatMap { scope ->
        val calls = HandlerFacts.callsIn(scope)
        if (calls.any { c -> checks.any { it.matches(c) } }) return@flatMap emptyList()
        val gated = calls.any { c -> adminChecks.any { it.matches(c) } }
        val neutralised = calls.any { c -> neutralisers.any { it.matches(c) } }
        calls.filter { it.kind == AstKind.CALL && it.name?.lowercase() in sinks }
            .filterNot { fixedExtension(it.arguments.getOrNull(destArg)) }
            .map { call ->
                Finding(
                    rule.id, call,
                    "${call.name}() stores an uploaded file without checking its type " +
                        "(wp_check_filetype_and_ext, an extension allow-list, or wp_handle_upload)" +
                        when {
                            gated -> ". Weak: the function checks a capability, so only privileged users reach it"
                            neutralised -> ". Weak: the file name goes through a neutralising helper; check that it blocks executable extensions"
                            else -> ""
                        },
                    weak = gated || neutralised,
                )
            }
    }

    private fun fixedExtension(dest: AstNode?): Boolean {
        val last = when (dest?.kind) {
            AstKind.STRING_LITERAL, AstKind.INTERPOLATED_STRING -> dest
            AstKind.CONCAT -> generateSequence(dest) { it.takeIf { n -> n.kind == AstKind.CONCAT }?.child(AstRole.RIGHT) }.last()
            else -> null
        } ?: return false
        val tail = last.stringValue ?: return false
        return EXT.containsMatchIn(tail) && (last.kind == AstKind.STRING_LITERAL || !tail.substringAfterLast('.').contains('$'))
    }

    companion object {
        private val EXT = Regex("""\.[A-Za-z0-9]{1,5}$""")
    }
}
