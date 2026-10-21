<?php
add_action('admin_post_p_delete', 'p_delete');
function <warning descr="Handler 'p_delete' (hook admin_post_p_delete) calls delete_option() without nonce verification (check_ajax_referer, check_admin_referer, wp_verify_nonce)">p_delete</warning>() {
    if (!current_user_can('manage_options')) {
        wp_die();
    }
    delete_option('p_settings');
    wp_safe_redirect(admin_url());
    exit;
}

class P_Ajax {
    public function register() {
        add_action('wp_ajax_p_import', array($this, 'import'));
    }
    public function <warning descr="Handler 'import' (hook wp_ajax_p_import) calls wp_insert_post() without nonce verification (check_ajax_referer, check_admin_referer, wp_verify_nonce)">import</warning>() {
        if (!current_user_can('edit_posts')) {
            wp_send_json_error();
        }
        wp_insert_post(array('post_title' => sanitize_text_field($_POST['title'])));
    }
}
