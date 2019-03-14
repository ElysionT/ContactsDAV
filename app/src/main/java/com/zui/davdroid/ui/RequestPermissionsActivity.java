/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zui.davdroid.ui;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Trace;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Activity that asks the user for all {@link #getPermissions} if any are missing.
 *
 * NOTE: As a result of b/22095159, this can behave oddly in the case where the final permission
 * you are requesting causes an application restart.
 */
public class RequestPermissionsActivity extends Activity
        implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final int PERMISSIONS_REQUEST_ALL_PERMISSIONS = 1;

    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            // Contacts group
            permission.READ_CONTACTS,
            permission.WRITE_CONTACTS,
            // Calendar group
            permission.READ_CALENDAR,
            permission.WRITE_CALENDAR,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Only start a requestPermissions() flow when first starting this activity the first time.
        // The process is likely to be restarted during the permission flow (necessary to enable
        // permissions) so this is important to track.
        if (savedInstanceState == null) {
            requestPermissions();
        }
    }

    public static boolean startPermissionActivity(Context context) {
        if (!hasPermissions(context, REQUIRED_PERMISSIONS)) {
            final Intent intent = new Intent(context,  RequestPermissionsActivity.class);
            context.startActivity(intent);
//            activity.finish();
            return true;
        }

        return false;
    }

    protected boolean isAllGranted(String permissions[], int[] grantResult) {
        for (int i = 0; i < permissions.length; i++) {
            if (grantResult[i] != PackageManager.PERMISSION_GRANTED
                    && isPermissionRequired(permissions[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean isPermissionRequired(String p) {
        return Arrays.asList(REQUIRED_PERMISSIONS).contains(p);
    }

    private void requestPermissions() {
        Trace.beginSection("requestPermissions");
        try {
            // Construct a list of missing permissions
            final ArrayList<String> unsatisfiedPermissions = new ArrayList<>();
            for (String permission : REQUIRED_PERMISSIONS) {
                if (!hasPermission(this, permission)) {
                    unsatisfiedPermissions.add(permission);
                }
            }
            if (unsatisfiedPermissions.size() == 0) {
                throw new RuntimeException("Request permission activity was called even"
                        + " though all permissions are satisfied.");
            }
            ActivityCompat.requestPermissions(
                    this,
                    unsatisfiedPermissions.toArray(new String[unsatisfiedPermissions.size()]),
                    PERMISSIONS_REQUEST_ALL_PERMISSIONS);
        } finally {
            Trace.endSection();
        }
    }

    protected static boolean hasPermissions(Context context, String[] permissions) {
        Trace.beginSection("hasPermission");
        try {
            for (String permission : permissions) {
                if (!hasPermission(context, permission)) {
                    return false;
                }
            }
            return true;
        } finally {
            Trace.endSection();
        }
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        if (permissions != null && permissions.length > 0
                && isAllGranted(permissions, grantResults)) {
//            mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//            if (mIsCallerSelf) {
//                startActivityForResult(mPreviousActivityIntent, 0);
//            } else {
//                startActivity(mPreviousActivityIntent);
//            }
            finish();
            overridePendingTransition(0, 0);

//            LocalBroadcastManager.getInstance(this).sendBroadcast(
//                    new Intent(BROADCAST_PERMISSIONS_GRANTED));
        } else {
            Toast.makeText(this, "R.string.missing_required_permission", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
