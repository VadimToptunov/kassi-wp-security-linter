<?php
function n_option() { wp_remote_get(get_option('n_endpoint')); }
function n_param($url) { wp_remote_get($url); }
function n_constant() { wp_remote_post(N_API_URL, array('body' => $_POST)); }
