<?php
function p_ping() {
    $host = $_GET['host'];
    system(<error descr="Request data ($_GET) reaches system() as an OS command without escapeshellarg()/escapeshellcmd()">'ping -c 1 ' . $host</error>);
}

function p_exec_post() {
    exec(<error descr="Request data ($_POST) reaches exec() as an OS command without escapeshellarg()/escapeshellcmd()">'convert ' . $_POST['file'] . ' /tmp/out.png'</error>);
}

function p_shell_rest($req) {
    $cmd = $req->get_param('cmd');
    $out = shell_exec(<error descr="Request data (->get_param()) reaches shell_exec() as an OS command without escapeshellarg()/escapeshellcmd()">"/usr/bin/tool $cmd"</error>);
}

function p_popen() {
    $h = popen(<error descr="Request data ($_REQUEST) reaches popen() as an OS command without escapeshellarg()/escapeshellcmd()">'gzip ' . $_REQUEST['f']</error>, 'r');
}

// A capability check in the function: still reported, but weak (admin-only).
function p_admin_backup() {
    if (!current_user_can('manage_options')) { wp_die(); }
    passthru(<weak_warning descr="Request data ($_POST) reaches passthru() as an OS command without escapeshellarg()/escapeshellcmd(). Weak: the function checks a capability, so only privileged users reach it">'tar czf /tmp/b.tgz ' . $_POST['dir']</weak_warning>);
}
