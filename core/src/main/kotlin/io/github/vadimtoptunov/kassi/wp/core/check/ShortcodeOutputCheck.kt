package io.github.vadimtoptunov.kassi.wp.core.check

import io.github.vadimtoptunov.kassi.wp.core.ast.AstKind
import io.github.vadimtoptunov.kassi.wp.core.ast.AstNode
import io.github.vadimtoptunov.kassi.wp.core.ast.AstRole
import io.github.vadimtoptunov.kassi.wp.core.ast.enclosingFunction
import io.github.vadimtoptunov.kassi.wp.core.ast.walk
import io.github.vadimtoptunov.kassi.wp.core.flow.Taint
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintAnalyzer
import io.github.vadimtoptunov.kassi.wp.core.flow.TaintSpec
import io.github.vadimtoptunov.kassi.wp.core.hooks.Callables
import io.github.vadimtoptunov.kassi.wp.core.hooks.HookRegistrations
import io.github.vadimtoptunov.kassi.wp.core.model.Rule

/**
 * WP-013: a shortcode callback (registered with `add_shortcode`, resolved in the same file) returns
 * or echoes its attributes without escaping. Anyone who can write posts (Contributor+) controls the
 * attributes, so the result is stored XSS.
 *
 * The callback's first parameter (`$atts`) is the taint source. It flows through `shortcode_atts()`
 * and the YAML passthrough functions. The sinks are `return` expressions and echo/print. Escapers
 * come from `sanitizers:`. `sanitize_text_field` is deliberately not one: it leaves quotes alone,
 * so attribute values can still break out.
 */
class ShortcodeOutputCheck(private val rule: Rule) : FileCheck {
    private val registrars = rule.source.map { it.lowercase() }.toSet()
    private val unregistrars = rule.params.strings("unregistrars").map { it.lowercase() }.toSet()
    private val analyzer = TaintSpec.of(rule).let { s ->
        TaintAnalyzer(TaintSpec(emptyList(), s.sanitizerFunctions, s.sanitizerMethods, s.passthrough, s.formatFunctions, s.guardFunctions, s.terminators))
    }

    override fun checkFile(file: AstNode): List<Finding> {
        val callables = Callables(file)
        // A shortcode registered and removed again in the same function only evaluates internal strings
        // (e.g. WooCommerce's [fee] in shipping costs); post content never reaches it.
        val temporary = HookRegistrations.of(file, unregistrars, callables).map { it.call.enclosingFunction() to it.hook }.toSet()
        return HookRegistrations.of(file, registrars, callables)
            .filterNot { (it.call.enclosingFunction() to it.hook) in temporary }
            .mapNotNull { it.handler }.distinct().flatMap { handler ->
            val atts = firstParameter(handler) ?: return@flatMap emptyList()
            val label = "\$$atts"
            analyzer.analyze(handler, ::sinkArguments, mapOf(atts to Taint(setOf(label)))).map { hit ->
                Finding(
                    rule.id, hit.argument,
                    "Shortcode attribute ($label) is output without escaping (esc_attr, esc_html, esc_url, wp_kses): " +
                        "stored XSS for anyone who can write posts",
                )
            }
        }
    }

    private fun sinkArguments(n: AstNode): List<AstNode> = when (n.kind) {
        AstKind.RETURN -> n.children.take(1)
        AstKind.ECHO, AstKind.PRINT -> n.arguments
        else -> emptyList()
    }

    private fun firstParameter(handler: AstNode): String? {
        val body = handler.child(AstRole.BODY)
        return handler.walk().filter { it.kind == AstKind.PARAMETER && (body == null || it !in body.walk()) }.firstOrNull()?.name
    }
}
