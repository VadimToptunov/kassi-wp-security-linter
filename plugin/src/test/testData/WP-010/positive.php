<?php
function p_proxy() {
    $url = get_query_var('url');
    if (empty($url) && isset($_GET['url'])) {
        $url = sanitize_text_field($_GET['url']);
    }
    $response = wp_remote_get(<warning descr="wp_remote_get() to a URL from request data ($_GET): server-side request forgery. Use wp_safe_remote_*() or validate the URL with wp_http_validate_url()">urldecode($url)</warning>);
}
function p_fetch() {
    wp_remote_post(<warning descr="wp_remote_post() to a URL from request data ($_POST): server-side request forgery. Use wp_safe_remote_*() or validate the URL with wp_http_validate_url()">esc_url_raw($_POST['webhook'])</warning>, array('body' => 'x'));
}
function p_host_injection() {
    // 'https://' alone does not fix the host.
    wp_remote_get(<warning descr="wp_remote_get() to a URL from request data ($_GET): server-side request forgery. Use wp_safe_remote_*() or validate the URL with wp_http_validate_url()">'https://' . $_GET['host'] . '/status'</warning>);
}
