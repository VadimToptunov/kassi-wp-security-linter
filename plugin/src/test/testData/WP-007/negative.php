<?php
function n_param($path) { unlink($path); }
function n_option() { readfile(get_option('n_export_path')); }
function n_constant() { include __DIR__ . '/views/settings.php'; }
function n_server() { readfile($_SERVER['DOCUMENT_ROOT'] . '/robots.txt'); }
