<?php
function n_param($user, $role) { $user->set_role($role); }
function n_option() { $u = new WP_User(1); $u->set_role(get_option('default_role')); }
function n_other_meta() { update_user_meta(get_current_user_id(), 'n_bio', $_POST['bio']); }

// Built-up userdata array: `role` comes from a fixed option, request data only lands in another
// entry (user_url). The `role` key is not attacker-controlled, so this must NOT flag.
function n_builtup_role_from_option() {
    $info = array('user_login' => 'x');
    $info['user_url'] = isset($_GET['website']) ? sanitize_text_field(wp_unslash($_GET['website'])) : '';
    $info['role'] = get_option('default_role');
    wp_insert_user($info);
}

// Self password/profile update: the args array carries request data (names/password) but has NO
// `role`/caps entry at all, and the target ID is the current user. Must NOT flag.
function n_update_self_no_role() {
    $user = wp_get_current_user();
    $update = array('ID' => $user->ID, 'user_pass' => $_POST['password']);
    $update['first_name'] = sanitize_text_field(wp_unslash($_POST['first_name']));
    wp_update_user($update);
}
