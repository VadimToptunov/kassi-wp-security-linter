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

// clean-login shape: the allow-list guard sits in a separate `if` with a compound `&&`
// condition, and set_role runs in a later block — the flow can't connect the guard to the sink,
// so the finding survives, but weak_if_guarded marks it weak (the role IS checked against an
// admin-defined allow-list, so it is likely intentional, not an exploit).
function s_ladder_allowlist() {
    $role = sanitize_text_field($_POST['role']);
    $allowed = get_option('cl_newuserroles');
    $create = get_option('cl_chooserole');
    if ($create && !in_array($role, $allowed)) {
        $msg = 'failed';
    }
    $u = new WP_User(1);
    if ($create) {
        $u->set_role(<weak_warning descr="Request data ($_POST) decides a user's role or capabilities in set_role(): privilege escalation. Use a fixed role or an allow-list. Weak: the value is checked against an allow-list in this function, so this is likely intentional">$role</weak_warning>);
    }
}
