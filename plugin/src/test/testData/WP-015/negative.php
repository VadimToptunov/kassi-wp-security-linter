<?php
function n_param($cmd) { system($cmd); }
function n_literal() { exec('php -v'); }
function n_option() { shell_exec(get_option('mp_backup_cmd')); }
function n_constant() { passthru('ls ' . ABSPATH); }
