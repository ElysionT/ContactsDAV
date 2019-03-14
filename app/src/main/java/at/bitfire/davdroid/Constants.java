/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * Copyright © 2015 – 2016 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import android.net.Uri;

public class Constants {

    public static enum AccountType {DEFAULT, ICLOUD, MI}

    public static final String DEFAULT_ACCOUNT_TYPE = "com.zui.davdroid.account";
    public static final String ICLOUD_ACCOUNT_TYPE = "com.zui.davdroid.account.icloud";
    public static final String MI_ACCOUNT_TYPE = "com.zui.davdroid.account.mi";
    public static final String EXTRA_ACCOUNT_TYPE = "extra_account_type";

    public final static java.util.logging.Logger log = java.util.logging.Logger.getLogger("ContactsDAV");

    // notification IDs
    public final static int
            NOTIFICATION_ACCOUNT_SETTINGS_UPDATED = 0,
            NOTIFICATION_EXTERNAL_FILE_LOGGING = 1,
            NOTIFICATION_REFRESH_COLLECTIONS = 2,
            NOTIFICATION_CONTACTS_SYNC = 10,
            NOTIFICATION_CALENDAR_SYNC = 11,
            NOTIFICATION_TASK_SYNC = 12,
            NOTIFICATION_PERMISSIONS = 20;

    public static final int DEFAULT_SYNC_INTERVAL = 4 * 3600;  // 4 hours


    public static final String HTTPS_SCHEME = "https://";
    public static final String ICLOUD_HOST_PATH = "icloud.com";
    public static final String ICLOUD_CARDDAV_URI = "https://contacts.icloud.com";
    public static final String ICLOUD_CALDAV_URI = "https://caldav.icloud.com";
    public static final String MI_HOST_PATH = "dav.mi.com";

    // The action to start AccountSyncSettings from XuiSettings.
    public static final String ACTION_SYNC_SETTINGS = "android.settings.ACCOUNT_SYNC_SETTINGS";
    // XuiSettings com.android.settings.accounts.AccountSyncSettings#ACCOUNT_KEY.
    public static final String CLASS_ACCOUNT_SYNC_SETTINGS = "AccountSyncSettings";

    public static final String EXTRA_KEY_ACCOUNT = "account";

    /**
     * When starting this activity, the invoking Intent can contain this extra
     * string to specify which fragment should be initially displayed.
     * <p/>Starting from Key Lime Pie, when this argument is passed in, the activity
     * will call isValidFragment() to confirm that the fragment class name is valid for this
     * activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT = ":settings:show_fragment";

    /**
     * When starting this activity and using {@link #EXTRA_SHOW_FRAGMENT},
     * this extra can also be specified to supply a Bundle of arguments to pass
     * to that fragment when it is instantiated during the initial creation
     * of the activity.
     */
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args";

    public static final String EXTRA_SHOW_FRAGMENT_AS_SUBSETTING = ":settings:show_fragment_as_subsetting";

    public static final String OPEN_DEBUGGING_TEXT = "open debug";

    public static final String CLOSE_DEBUGGING_TEXT = "close debug";

    public static final String VERSION_TEXT = "version";

    public static final String FUNCTION_TEST = "function test";
}
