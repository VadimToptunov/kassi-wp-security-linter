<?php
function n_option() { wp_remote_get(get_option('n_endpoint')); }
function n_param($url) { wp_remote_get($url); }
function n_constant() { wp_remote_post(N_API_URL, array('body' => $_POST)); }
// download_url() fetches with wp_safe_remote_get() internally.
function n_download($request) { $tmp = download_url($request->get_param('src')); }
