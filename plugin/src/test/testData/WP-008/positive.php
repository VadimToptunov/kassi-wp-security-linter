<?php
wp_redirect(<warning descr="wp_redirect() to a URL from request data ($_GET): open redirect. Use wp_safe_redirect()">$_GET['redirect_to']</warning>);

function p_esc_url_raw_is_not_enough() {
    $url = esc_url_raw(wp_unslash($_REQUEST['url']));
    wp_redirect(<warning descr="wp_redirect() to a URL from request data ($_REQUEST): open redirect. Use wp_safe_redirect()">$url</warning>);
    exit;
}

function p_query_arg_on_input_base() {
    wp_redirect(<warning descr="wp_redirect() to a URL from request data ($_POST): open redirect. Use wp_safe_redirect()">add_query_arg('done', 1, $_POST['back'])</warning>);
}

function p_coalesce() {
    $to = $_GET['to'] ?? home_url();
    wp_redirect(<warning descr="wp_redirect() to a URL from request data ($_GET): open redirect. Use wp_safe_redirect()">$to</warning>, 302);
}

function p_nonce_gated() {
    if (!wp_verify_nonce($_GET['_n'], 'p_connect')) { wp_die(); }
    wp_redirect(<weak_warning descr="wp_redirect() to a URL from request data ($_GET): open redirect. Use wp_safe_redirect(). Weak: the function verifies a nonce, so a crafted link alone cannot trigger it">wp_unslash($_GET['install_url'])</weak_warning>);
    exit;
}
