<?php
function n_param($code) { eval($code); }
function n_literal() { eval('$x = 1 + 2;'); }
function n_option() { eval(get_option('mp_snippet')); }
