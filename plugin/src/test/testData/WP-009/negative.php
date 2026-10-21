<?php
function n_meta($post_id) { return maybe_unserialize(get_post_meta($post_id, 'n', true)); }
function n_param($data) { return unserialize($data); }
function n_option() { return unserialize(get_option('n_cache')); }
