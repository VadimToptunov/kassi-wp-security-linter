<?php
add_action('admin_post_s_delete', 's_delete');
function s_delete() {
    check_admin_referer('s_delete');
    if (!current_user_can('manage_options')) {
        wp_die();
    }
    delete_option('s_settings');
}

add_action('wp_ajax_s_save', 's_save');
function s_save() {
    if (!isset($_POST['_wpnonce']) || !wp_verify_nonce($_POST['_wpnonce'], 's_save')) {
        wp_send_json_error();
    }
    update_option('s_settings', $_POST['settings']);
}

add_action('wp_ajax_s_import', 's_import');
function s_import() {
    s_verify();
    wp_insert_post(array('post_title' => 'x'));
}
function s_verify() {
    check_ajax_referer('s_import', 'nonce');
}

// The registering constructor verifies the nonce for every AJAX request before adding handlers.
class S_Bootstrap {
    public function __construct() {
        if (wp_doing_ajax() && !wp_verify_nonce($_REQUEST['_nonce'] ?? '', 's_admin')) {
            wp_send_json_error();
        }
        add_action('wp_ajax_s_bootstrap_save', array($this, 'save'));
    }
    public function save() { update_option('s_bootstrap', $_POST['v']); }
}
