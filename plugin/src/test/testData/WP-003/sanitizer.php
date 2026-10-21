<?php
add_action('wp_ajax_s_settings', 's_settings');
add_action('wp_ajax_nopriv_s_settings', 's_settings');
function s_settings() {
    if (!current_user_can('manage_options')) {
        wp_send_json_error(null, 403);
    }
    update_option('s_settings', $_POST['settings']);
}

// One-time token verified with an HMAC (upgrade/connect flows): authenticated.
add_action('wp_ajax_nopriv_s_connect', 's_connect');
function s_connect() {
    $token = get_option('s_connect_token');
    if (hash_hmac('sha512', $token, wp_salt()) !== $_REQUEST['oth']) {
        wp_send_json_error();
    }
    delete_option('s_connect_token');
    activate_plugin('s-pro/s-pro.php');
}
