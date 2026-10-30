<?php
// Gated by Helpers::verify_ajax_call() in another file -> no CSRF report.
add_action('wp_ajax_cf_save', 'cf_save');
function cf_save() {
    Helpers::verify_ajax_call();
    delete_option('cf_settings');
}

// Calls a cross-file helper that does NOT verify a nonce -> still reported.
add_action('wp_ajax_cf_bad', 'cf_bad');
function <warning descr="Handler 'cf_bad' (hook wp_ajax_cf_bad) calls delete_option() without nonce verification (check_ajax_referer, check_admin_referer, wp_verify_nonce)">cf_bad</warning>() {
    Helpers::just_log();
    delete_option('cf_other');
}
