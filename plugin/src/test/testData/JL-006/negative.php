<?php
// JL-006 negatives: every echo here is output-safe, so nothing is flagged.

function jl006_htmlspecialchars($input) {
    echo htmlspecialchars($input->getRaw('q'), ENT_QUOTES); // escaped -> safe
}

// $this->escape() (receiver-agnostic ->escape) wraps htmlspecialchars in a Joomla view.
function jl006_view_escape($view, $input) {
    echo $view->escape($input->getRaw('x')); // escaped -> safe
}

function jl006_cmd_filter($input) {
    echo $input->get('page');    // default 'cmd' filter strips <>"' -> safe
    echo $input->getCmd('view'); // cmd filter -> safe
}

function jl006_numeric($input) {
    echo $input->getInt('id');   // numeric -> safe
}
