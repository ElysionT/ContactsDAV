/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * Copyright © 2015 – 2016 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import com.zui.davdroid.R;

import at.bitfire.davdroid.ui.BaseActivity;

public class AccountActivity extends BaseActivity {
    public final static String TAG_ACCOUNT_SETTINGS = "account_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.settings_account_activity);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getFragmentManager().beginTransaction()
                .add(R.id.account_fragment, new AccountFragment(), TAG_ACCOUNT_SETTINGS).commit();
    }
}
