<?php
function s_safe() { $response = wp_safe_remote_get($_GET['url']); }
function s_validated() {
    $url = wp_http_validate_url(wp_unslash($_POST['image_url']));
    if (!$url) { die(); }
    $response = wp_remote_get($url);
}
function s_own_api() { wp_remote_get(rest_url('s/v1/ping?x=' . $_GET['x'])); }
function s_allowlist() {
    $target = $_GET['target'];
    if (!in_array($target, array('https://api.example.com/a', 'https://api.example.com/b'), true)) {
        wp_die();
    }
    wp_remote_get($target);
}
// Input after a fixed scheme and host cannot change the destination.
function s_fixed_host($request) {
    $url = 'https://www.goodreads.com/author/show/' . $request->get_param('id');
    wp_remote_get(esc_url_raw($url));
}
function s_constant_base($request) {
    wp_remote_get(add_query_arg(array('q' => $request['q']), S_API_BASE . '/search'));
}
function s_sprintf_base($req) {
    wp_remote_get(sprintf('%s/rest/v1.1/sites/%d/posts?%s', S_API_BASE, 7, http_build_query($req->get_params())));
}
