<?php
// Callback defined in another file: not resolvable here.
add_action('wp_ajax_n_elsewhere', 'n_defined_elsewhere');
add_action('wp_ajax_n_static', array('N_Other_Class', 'save'));

// Also on a nopriv hook: WP-003's territory.
add_action('wp_ajax_n_public', 'n_public');
add_action('wp_ajax_nopriv_n_public', 'n_public');
function n_public() {
    update_option('n_counter', 1);
}

// Not an AJAX / admin-post hook.
add_action('init', 'n_init');
function n_init() {
    update_option('n_version', '1.0');
}

// Dynamic callback.
add_action('wp_ajax_n_dyn', $callback);

// Other user's meta is privileged (reported), but this one is a filter, not an action.
add_filter('wp_ajax_n_filter', 'n_filter');
function n_filter() {
    update_option('n_filtered', 1);
}

// Also an event reactor on a non-AJAX hook: not a request endpoint.
add_action('wp_ajax_n_sync', 'n_sync');
add_action('after_switch_theme', 'n_sync');
function n_sync() {
    update_option('n_synced', time());
}
