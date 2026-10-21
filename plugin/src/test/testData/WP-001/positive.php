<?php
// No check at all: warning.
add_action('wp_ajax_p_save', 'p_save');
function <warning descr="Handler 'p_save' (hook wp_ajax_p_save) calls update_option() without a capability check (current_user_can)">p_save</warning>() {
    update_option('p_settings', $_POST['settings']);
    wp_send_json_success();
}

class P_Admin {
    function __construct() {
        add_action('admin_post_p_delete', array($this, 'delete'));
        add_action('wp_ajax_p_reset', [$this, 'reset']);
    }

    function <warning descr="Handler 'delete' (hook admin_post_p_delete) calls $wpdb->delete() without a capability check (current_user_can)">delete</warning>() {
        global $wpdb;
        $wpdb->delete($wpdb->prefix . 'p_items', array('id' => (int) $_POST['id']));
        exit;
    }

    // Nonce only: weak (safe only while the nonce is never shown to lower roles).
    function <weak_warning descr="Handler 'reset' (hook wp_ajax_p_reset) calls delete_option() without a capability check (current_user_can). Low impact: it only writes notice/onboarding-style state, or verifies a nonce that may never reach lower-privileged users">reset</weak_warning>() {
        check_ajax_referer('p_reset');
        $this->do_reset();
    }

    private function do_reset() {
        delete_option('p_settings');
    }
}

add_action('wp_ajax_p_purge', <weak_warning descr="Handler closure (hook wp_ajax_p_purge) calls unlink() without a capability check (current_user_can). Low impact: it only writes notice/onboarding-style state, or verifies a nonce that may never reach lower-privileged users">function</weak_warning> () {
    check_ajax_referer('p_purge');
    unlink(WP_CONTENT_DIR . '/p.log');
});

add_action('wp_ajax_' . 'p_role', 'p_role');
function <warning descr="Handler 'p_role' (hook wp_ajax_p_role) calls ->set_role() without a capability check (current_user_can)">p_role</warning>() {
    $user = new WP_User((int) $_POST['user']);
    $user->set_role('editor');
}

// Low impact: only a notice-dismissal flag.
add_action('wp_ajax_p_dismiss', 'p_dismiss');
function <weak_warning descr="Handler 'p_dismiss' (hook wp_ajax_p_dismiss) calls update_option() without a capability check (current_user_can). Low impact: it only writes notice/onboarding-style state, or verifies a nonce that may never reach lower-privileged users">p_dismiss</weak_warning>() {
    update_option('p_review_notice_dismissed', 1);
}
