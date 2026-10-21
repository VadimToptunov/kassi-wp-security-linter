<?php
add_action('wp_ajax_s_save', 's_save');
function s_save() {
    check_ajax_referer('s_save');
    if (!current_user_can('manage_options')) {
        wp_send_json_error(null, 403);
    }
    update_option('s_settings', $_POST['settings']);
}

class S_Admin {
    function __construct() {
        add_action('admin_post_s_delete', array($this, 'delete'));
    }
    function delete() {
        $this->authorize();
        delete_option('s_settings');
    }
    private function authorize() {
        if (!current_user_can('manage_options')) {
            wp_die('nope');
        }
    }
}

// Own data only: any logged-in user may dismiss their own notice.
add_action('wp_ajax_s_dismiss', 's_dismiss');
function s_dismiss() {
    update_user_meta(get_current_user_id(), 's_dismissed', 1);
}
add_action('wp_ajax_s_dismiss2', 's_dismiss2');
function s_dismiss2() {
    $uid = get_current_user_id();
    delete_user_meta($uid, 's_dismissed');
}

// Read-only handler.
add_action('wp_ajax_s_get', 's_get');
function s_get() {
    wp_send_json(get_option('s_settings'));
}

// Capability wrapper defined in another file (name matches *user_can*).
add_action('wp_ajax_s_wrapped', 's_wrapped');
function s_wrapped() {
    if (!myplugin_current_user_can('manage')) {
        wp_send_json_error();
    }
    update_option('s_settings', $_POST['settings']);
}
add_action('wp_ajax_s_static', 's_static');
function s_static() {
    if (!MyPlugin_Options::user_can_manage()) {
        die();
    }
    delete_option('s_settings');
}
