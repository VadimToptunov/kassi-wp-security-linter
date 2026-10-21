<?php
function p_register() {
    $role = sanitize_text_field($_POST['default_role']);
    if (!empty($role) && get_role($role)) {
        $user = new WP_User($user_id);
        $user->set_role(<error descr="Request data ($_POST) decides a user's role or capabilities in set_role(): privilege escalation. Use a fixed role or an allow-list">$role</error>);
    }
}
function p_branches() {
    if (isset($_POST['user_role_set'])) {
        $role = sanitize_text_field(wp_unslash($_POST['user_role_set']));
    } else {
        $role = 'wholesaler';
    }
    $u = new WP_User(1);
    $u->add_role(<error descr="Request data ($_POST) decides a user's role or capabilities in add_role(): privilege escalation. Use a fixed role or an allow-list">$role</error>);
}
function p_insert($request) {
    wp_insert_user(array(
        'user_login' => 'x',
        'role' => <error descr="Request data (->get_param()) decides a user's role or capabilities in wp_insert_user(): privilege escalation. Use a fixed role or an allow-list">sanitize_text_field($request->get_param('role'))</error>,
    ));
}
function p_wholesale_update() {
    wp_update_user(<error descr="Request data ($_POST) decides a user's role or capabilities in wp_update_user(): privilege escalation. Use a fixed role or an allow-list">$_POST</error>);
}
function p_caps() {
    global $wpdb;
    update_user_meta(get_current_user_id(), $wpdb->prefix . 'capabilities', <error descr="Request data ($_POST) decides a user's role or capabilities in update_user_meta(): privilege escalation. Use a fixed role or an allow-list">$_POST['caps']</error>);
}
