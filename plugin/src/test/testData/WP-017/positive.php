<?php

function p_reply_to_header() {
    $headers = 'Reply-To: ' . $_POST['email'];
    wp_mail('admin@example.com', 'Subject', 'Body', <error descr="Request data ($_POST) reaches the headers argument of wp_mail() without CR/LF removal (email header injection)">$headers</error>);
}

function p_mail_builtin_from() {
    $h = "From: " . $_GET['from'] . "\n";
    mail('admin@example.com', 'Subject', 'Body', <error descr="Request data ($_GET) reaches the headers argument of mail() without CR/LF removal (email header injection)">$h</error>);
}

function p_interpolated() {
    $reply = $_REQUEST['reply'];
    $headers = "Reply-To: $reply\n";
    wp_mail('a@b.com', 's', 'b', <error descr="Request data ($_REQUEST) reaches the headers argument of wp_mail() without CR/LF removal (email header injection)">$headers</error>);
}

// A capability check in the function: still reported, but weak (admin-only).
function p_admin_weak() {
    if (!current_user_can('manage_options')) { wp_die(); }
    $headers = 'Cc: ' . $_POST['cc'];
    wp_mail('a@b.com', 's', 'b', <weak_warning descr="Request data ($_POST) reaches the headers argument of wp_mail() without CR/LF removal (email header injection). Weak: the function checks a capability, so only privileged users reach it">$headers</weak_warning>);
}
