<?php
add_action('wp_ajax_p_settings', 'p_settings');
add_action('wp_ajax_nopriv_p_settings', 'p_settings');
function <error descr="Unauthenticated handler 'p_settings' (hook wp_ajax_nopriv_p_settings) calls update_option() with no capability check: anyone can trigger it">p_settings</error>() {
    update_option('p_settings', $_POST['settings']);
    wp_send_json_success();
}

// A nonce does not authenticate a logged-out visitor.
add_action('wp_ajax_nopriv_p_cleanup', 'p_cleanup');
function <error descr="Unauthenticated handler 'p_cleanup' (hook wp_ajax_nopriv_p_cleanup) calls unlink() with no capability check: anyone can trigger it">p_cleanup</error>() {
    check_ajax_referer('p_cleanup');
    unlink(WP_CONTENT_DIR . '/uploads/p/' . basename($_POST['file']));
}

class P_Public {
    function __construct() {
        add_action('admin_post_nopriv_p_promote', array($this, 'promote'));
    }
    function <error descr="Unauthenticated handler 'promote' (hook admin_post_nopriv_p_promote) calls ->set_role() with no capability check: anyone can trigger it">promote</error>() {
        $this->apply_role((int) $_POST['user']);
    }
    private function apply_role($id) {
        $user = new WP_User($id);
        $user->set_role('administrator');
    }
}

// A capability check in an unrelated branch of the registering function does not gate the handler.
class P_Invoice {
    public function init() {
        if (current_user_can('manage_options') && isset($_GET['reset'])) {
            $this->reset_settings();
        }
        add_action('wp_ajax_nopriv_p_invoice', array($this, 'handle'));
    }
    public function <error descr="Unauthenticated handler 'handle' (hook wp_ajax_nopriv_p_invoice) calls update_option() with no capability check: anyone can trigger it">handle</error>() {
        check_ajax_referer('p_invoice');
        update_option('p_invoice_settings', $_POST['settings']);
    }
}
