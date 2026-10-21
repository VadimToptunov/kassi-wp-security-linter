<?php
function n_param($user, $role) { $user->set_role($role); }
function n_option() { $u = new WP_User(1); $u->set_role(get_option('default_role')); }
function n_other_meta() { update_user_meta(get_current_user_id(), 'n_bio', $_POST['bio']); }
