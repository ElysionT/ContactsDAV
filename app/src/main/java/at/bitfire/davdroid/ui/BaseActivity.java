/*
 * Copyright © 2015 – 2016 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.zui.davdroid.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import at.bitfire.davdroid.ui.settings.AccountActivity;

public class BaseActivity extends Activity {
    private static final String TAG = BaseActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getWindow().setStatusBarColor(getResources().getColor(R.color.background_status_bar));
        setNavbarColor(this);
    }

    private static void setNavbarColor(Activity activity) {
        final Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(activity.getResources().getColor(R.color.background_primary));

        final View decorView = activity.getWindow().getDecorView();
        if (null != decorView) {
            int value = decorView.getSystemUiVisibility();
            value |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(value);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set status bar icon color
        final View decorView = getWindow().getDecorView();
        if (null != decorView) {
            int value = decorView.getSystemUiVisibility();
            value |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(value);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
