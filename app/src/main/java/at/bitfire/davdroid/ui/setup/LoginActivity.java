/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * Copyright © 2015 – 2016 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import com.zui.davdroid.R;
import com.zui.davdroid.ui.RequestPermissionsActivity;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.ui.BaseActivity;

public class LoginActivity extends BaseActivity {

    /**
     * When set, "login by URL" will be activated by default, and the URL field will be set to this value.
     * When not set, "login by email" will be activated by default.
     */
    public static final String EXTRA_URL = "url";

    /**
     * When set, and {@link #EXTRA_PASSWORD} is set too, the user name field will be set to this value.
     * When set, and {@link #EXTRA_URL} is not set, the email address field will be set to this value.
     */
    public static final String EXTRA_USERNAME = "username";

    /**
     * When set, the password field will be set to this value.
     */
    public static final String EXTRA_PASSWORD = "password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_add_account);

        RequestPermissionsActivity.startPermissionActivity(this);

        if (savedInstanceState == null) {	// first call
            getFragmentManager().beginTransaction()
                    .add(R.id.right_pane, new LoginCredentialsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        App app = (App)getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();

        App app = (App)getApplicationContext();
        if (app.getCertManager() != null)
            app.getCertManager().appInForeground = false;
    }
}
