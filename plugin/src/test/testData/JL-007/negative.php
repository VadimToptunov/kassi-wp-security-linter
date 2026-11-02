<?php
// JL-007 negatives: every path here is traversal-safe, so nothing is flagged.

function jl007_basename($input) {
    $f = basename($input->getString('file')); // basename -> no directory parts
    readfile('/var/data/' . $f);
}

function jl007_cmd_filter($app) {
    $view = $app->input->getCmd('view');       // cmd filter strips '/' -> safe
    require_once JPATH_COMPONENT . '/views/' . $view . '/view.html.php';
}

function jl007_bare_get($input) {
    $tpl = $input->get('tpl');                 // default 'cmd' filter -> safe
    readfile(JPATH_ROOT . '/' . $tpl);
}

// JPath::clean() is a static call -> the engine ends the proof there (treated safe).
function jl007_jpath_clean($input) {
    $p = JPath::clean(JPATH_ROOT . '/' . $input->getString('p'));
    return file_get_contents($p);
}

function jl007_numeric($input) {
    $id = $input->getInt('id');                // numeric -> safe
    readfile('/cache/' . $id . '.dat');
}
