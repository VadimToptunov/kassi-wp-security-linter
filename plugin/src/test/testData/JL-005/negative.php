<?php
// JL-005 negatives: every setQuery() here is SQL-safe, so nothing is flagged.

function jl005_int_cast($db, $input) {
    $id = $input->getInt('id');                 // int cast -> safe
    $db->setQuery("SELECT * FROM #__zoo_item WHERE id = " . $id);
    return $db->loadObject();
}

function jl005_quoted($db, $input) {
    $alias = $input->getString('alias');
    $db->setQuery("SELECT id FROM #__content WHERE alias = " . $db->quote($alias)); // quoted -> safe
    return $db->loadColumn();
}

function jl005_constant($db) {
    $db->setQuery("SELECT COUNT(*) FROM #__users"); // no input -> safe
    return $db->loadResult();
}

// Receiver-qualification guard: ->get() on a params/Registry object is config, not request input.
// MUST stay silent — otherwise `->get` would be a blanket source and flood false positives.
function jl005_params_get($db, $params) {
    $limit = $params->get('list_limit');
    $db->setQuery("SELECT * FROM #__content LIMIT " . $limit);
    return $db->loadObjectList();
}
