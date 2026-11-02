<?php
// JL-006 (CWE-79) positives: Joomla request input echoed/printed without escaping.

function jl006_getraw_echo($input) {
    $q = $input->getRaw('q');
    echo <warning descr="Request data (->getraw()) is output without escaping (htmlspecialchars(), $this->escape())">'<p>You searched for: ' . $q . '</p>'</warning>;
}

function jl006_gethtml_concat($input) {
    echo <warning descr="Request data (->gethtml()) is output without escaping (htmlspecialchars(), $this->escape())">'<div>' . $input->getHtml('bio') . '</div>'</warning>;
}

function jl006_superglobal_print() {
    print <warning descr="Request data ($_GET) is output without escaping (htmlspecialchars(), $this->escape())">"Hello " . $_GET['name']</warning>;
}

// Chained form: $app->input->getRaw() — receiver is the property `input`, but getRaw is receiver-agnostic.
function jl006_chained_getraw($app) {
    $u = $app->input->getRaw('uri');
    echo <warning descr="Request data (->getraw()) is output without escaping (htmlspecialchars(), $this->escape())">'<a href="' . $u . '">back</a>'</warning>;
}
