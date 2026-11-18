<?php

// sanitize_email() strips line breaks from the recipient value.
function s_sanitize_email() {
    $headers = 'Reply-To: ' . sanitize_email($_POST['email']);
    wp_mail('a@b.com', 's', 'b', $headers);
}

// sanitize_text_field() removes CR/LF — the documented fix.
function s_sanitize_text_field() {
    $reply = sanitize_text_field($_POST['reply']);
    $headers = "Reply-To: $reply\n";
    wp_mail('a@b.com', 's', 'b', $headers);
}
