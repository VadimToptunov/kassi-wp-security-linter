<?php
// JL-005 (CWE-89) positives: Joomla request input reaches $db->setQuery() unquoted/uncast.

function jl005_getstring($db, $input) {
    $alias = $input->getString('alias');
    $db->setQuery(<error descr="SQL built from request data (->getstring()) reaches $db->setQuery() without $db->quote()/escape() or an (int) cast">"SELECT * FROM #__zoo_item WHERE alias = '" . $alias . "'"</error>);
    return $db->loadObject();
}

function jl005_superglobal($db) {
    $uid = $_REQUEST['user'];
    $db->setQuery(<error descr="SQL built from request data ($_REQUEST) reaches $db->setQuery() without $db->quote()/escape() or an (int) cast">"SELECT name FROM #__users WHERE id = '$uid'"</error>);
    return $db->loadResult();
}

// The dominant real pattern: bare ->get() on the request-input object ($input).
function jl005_bare_input_get($db, $input) {
    $cat = $input->get('catid');
    $db->setQuery(<error descr="SQL built from request data ($input->get()) reaches $db->setQuery() without $db->quote()/escape() or an (int) cast">"SELECT * FROM #__content WHERE catid = '" . $cat . "'"</error>);
    return $db->loadObjectList();
}

// Chained form: $app->input->get() — receiver is the property `input`.
function jl005_chained_input_get($db, $app) {
    $sort = $app->input->get('sort');
    $db->setQuery(<error descr="SQL built from request data ($input->get()) reaches $db->setQuery() without $db->quote()/escape() or an (int) cast">"SELECT * FROM #__content ORDER BY $sort"</error>);
    return $db->loadColumn();
}
