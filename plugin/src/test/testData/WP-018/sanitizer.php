<?php

// sanitize_text_field() strips line breaks — the documented fix.
function s_sanitize_text_field() {
    $status = sanitize_text_field(wp_unslash($_GET['status']));
    header("X-Status: $status");
}

// sanitize_key() keeps only [a-z0-9_-], so no CR/LF survives.
function s_sanitize_key() {
    $slug = sanitize_key($_POST['slug']);
    header('X-Slug: ' . $slug);
}
