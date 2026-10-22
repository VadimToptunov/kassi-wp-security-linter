<?php
function p_font() {
    $target = $dir . '/' . $_FILES['font']['name'];
    if (<error descr="move_uploaded_file() stores an uploaded file without checking its type (wp_check_filetype_and_ext, an extension allow-list, or wp_handle_upload)">move_uploaded_file</error>($_FILES['font']['tmp_name'], $target)) {
        echo 'ok';
    }
}
class P_Upload {
    public function handle($tmp, $file) {
        $dest = wp_upload_dir()['path'] . '/' . basename($file['name']);
        <error descr="move_uploaded_file() stores an uploaded file without checking its type (wp_check_filetype_and_ext, an extension allow-list, or wp_handle_upload)">move_uploaded_file</error>($tmp, $dest);
    }
}

function p_neutralised($name, $tmp) {
    $filename = p_antiscript_file_name($name);
    <weak_warning descr="move_uploaded_file() stores an uploaded file without checking its type (wp_check_filetype_and_ext, an extension allow-list, or wp_handle_upload). Weak: the file name goes through a neutralising helper; check that it blocks executable extensions">move_uploaded_file</weak_warning>($tmp, $dir . '/' . $filename);
}
