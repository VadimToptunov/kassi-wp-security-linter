<?php
function s_allowlist() {
    $role = sanitize_key($_POST['default_role']);
    if (!empty($role) && in_array($role, array('subscriber', 'customer'), true) && get_role($role)) {
        $user = new WP_User($user_id);
        $user->set_role($role);
    }
}
function s_fallback() {
    if (isset($_POST['user_role_set'])) {
        $role = sanitize_text_field(wp_unslash($_POST['user_role_set']));
        if (!in_array($role, $allowed_roles, true)) {
            $role = 'wholesaler';
        }
    } else {
        $role = 'wholesaler';
    }
    $u = new WP_User(1);
    $u->add_role($role);
}
function s_fixed() { wp_insert_user(array('user_login' => sanitize_user($_POST['login']), 'role' => 'subscriber')); }
