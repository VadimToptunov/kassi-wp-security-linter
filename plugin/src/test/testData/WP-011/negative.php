<?php
// Fixed literal extension: the uploader cannot choose the file type.
function n_fixed_ext() { move_uploaded_file($_FILES['f']['tmp_name'], $dir . '/' . uniqid() . '.pdf'); }
function n_fixed_name() { move_uploaded_file($_FILES['f']['tmp_name'], WP_CONTENT_DIR . '/n/import.csv'); }
function n_handled() { $r = wp_handle_upload($_FILES['f'], array('test_form' => false)); }
