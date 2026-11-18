<?php

// Constant headers — nothing from the request.
function n_constant_headers() {
    $headers = "Content-Type: text/html\r\n";
    wp_mail('a@b.com', 's', 'b', $headers);
}

// Request data in the body argument (index 2), not the headers — not a header-injection sink.
function n_body_only() {
    wp_mail('a@b.com', 's', $_POST['message']);
}

// Header built from a site option, not the request.
function n_from_option() {
    $headers = 'Reply-To: ' . get_option('admin_email');
    wp_mail('a@b.com', 's', 'b', $headers);
}
