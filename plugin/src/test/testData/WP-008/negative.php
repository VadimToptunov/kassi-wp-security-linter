<?php
function n_param($url) { wp_redirect($url); }
wp_redirect(get_permalink($_GET['id']));
wp_redirect(home_url('/'));
header('Location: ' . $_GET['to']); // header() is not in this rule's sinks
