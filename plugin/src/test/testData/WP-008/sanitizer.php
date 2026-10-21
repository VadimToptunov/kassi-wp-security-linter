<?php
wp_safe_redirect($_GET['redirect_to']);
wp_redirect(wp_validate_redirect($_GET['redirect_to'], home_url()));
wp_redirect(admin_url('admin.php?page=' . $_GET['page']));
wp_redirect(add_query_arg('msg', $_GET['m']));
wp_redirect(add_query_arg(['a' => $_GET['a']], admin_url('options.php')));
