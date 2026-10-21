<?php
// What public forms legitimately do: not privileged.
add_action('wp_ajax_nopriv_n_contact', 'n_contact');
function n_contact() {
    global $wpdb;
    $wpdb->insert($wpdb->prefix . 'n_messages', array('body' => sanitize_textarea_field($_POST['body'])));
    wp_insert_post(array('post_type' => 'n_entry', 'post_title' => sanitize_text_field($_POST['name'])));
    wp_mail(get_option('admin_email'), 'New message', 'x');
}

add_action('wp_ajax_nopriv_n_register', 'n_register');
function n_register() {
    wp_create_user(sanitize_user($_POST['login']), $_POST['pass'], sanitize_email($_POST['email']));
}

// Callback in another file.
add_action('wp_ajax_nopriv_n_elsewhere', 'n_defined_elsewhere');
