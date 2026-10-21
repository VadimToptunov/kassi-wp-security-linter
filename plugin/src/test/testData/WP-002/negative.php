<?php
// Read-only.
add_action('wp_ajax_n_get', 'n_get');
function n_get() {
    wp_send_json(get_option('n_settings'));
}

// Own data.
add_action('wp_ajax_n_dismiss', 'n_dismiss');
function n_dismiss() {
    update_user_meta(get_current_user_id(), 'n_dismissed', 1);
}

// Public endpoint (also nopriv): no CSRF report.
add_action('wp_ajax_n_vote', 'n_vote');
add_action('wp_ajax_nopriv_n_vote', 'n_vote');
function n_vote() {
    global $wpdb;
    $wpdb->insert($wpdb->prefix . 'n_votes', array('v' => 1));
}

// Callback in another file.
add_action('admin_post_n_elsewhere', 'n_defined_elsewhere');
