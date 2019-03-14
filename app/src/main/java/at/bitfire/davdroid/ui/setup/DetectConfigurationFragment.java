/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * Copyright © 2015 – 2015 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.zui.davdroid.R;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.dav4android.exception.UnauthorizedException;
import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.DavService;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.model.ServiceDB.HomeSets;
import at.bitfire.davdroid.model.ServiceDB.OpenHelper;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.davdroid.ui.setup.DavResourceFinder.Configuration;
import at.bitfire.ical4android.TaskProvider;
import at.bitfire.vcard4android.GroupMethod;
import lombok.Cleanup;

public class DetectConfigurationFragment extends DialogFragment implements LoaderManager.LoaderCallbacks<Configuration> {
    private static final String TAG = DetectConfigurationFragment.class.getSimpleName();
    private static final int MAX_RETRY_TIMES = 3;

    private static Map<String, Integer> mRetryTimes = new HashMap<String, Integer>();

    protected static final String ARG_LOGIN_CREDENTIALS = "credentials";

    public static DetectConfigurationFragment newInstance(LoginCredentials credentials) {
        DetectConfigurationFragment frag = new DetectConfigurationFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(ARG_LOGIN_CREDENTIALS, credentials);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LoginCredentials credentials = (LoginCredentials)getArguments().getParcelable(ARG_LOGIN_CREDENTIALS);
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setMessage(getString(Constants.ICLOUD_ACCOUNT_TYPE.equals(credentials.accountType)
                ? R.string.setup_querying_server_icloud
                : R.string.setup_querying_server_carddav));
        progress.setIndeterminate(true);
        progress.setCanceledOnTouchOutside(false);
        setCancelable(false);
        return progress;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(0, getArguments(), this);
    }

    @Override
    public Loader<Configuration> onCreateLoader(int id, Bundle args) {
        return new ServerConfigurationLoader(getContext(), (LoginCredentials)args.getParcelable(ARG_LOGIN_CREDENTIALS));
    }

    @Override
    public void onLoadFinished(Loader<Configuration> loader, Configuration config) {
        if (config != null) {
            if (config.calDAV == null && config.cardDAV == null) {
                // no service found: show error message
                getFragmentManager().beginTransaction()
                        .add(NothingDetectedFragment.newInstance(config), null)
                        .commitAllowingStateLoss();
            } else
                // service found: continue
                createAccount(config);
        } else
            App.log.severe("Configuration detection failed");

        dismissAllowingStateLoss();
    }

    @Override
    public void onLoaderReset(Loader<Configuration> arg0) {
    }

    protected void createAccount(DavResourceFinder.Configuration config) {
        Account account = new Account(config.userName, config.accountType);

        // create Android account
        Bundle userData = AccountSettings.initialUserData(config.userName);
        App.log.log(Level.INFO, "Creating Android account with initial config", new Object[] { account, userData });

        AccountManager accountManager = AccountManager.get(getContext());
        if (!accountManager.addAccountExplicitly(account, config.password, userData))
            Toast.makeText(getActivity(), R.string.login_account_not_created, Toast.LENGTH_LONG).show();

        // add entries for account to service DB
        App.log.log(Level.INFO, "Writing account configuration to database", config);
        @Cleanup OpenHelper dbHelper = new OpenHelper(getContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            AccountSettings settings = new AccountSettings(getContext(), account);

            Intent refreshIntent = new Intent(getActivity(), DavService.class);
            refreshIntent.setAction(DavService.ACTION_REFRESH_COLLECTIONS);

            if (config.cardDAV != null) {
                // insert CardDAV service
                long id = insertService(db, config, Services.SERVICE_CARDDAV);

                // start CardDAV service detection (refresh collections)
                refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id);
                getActivity().startService(refreshIntent);

                // initial CardDAV account settings
                settings.setGroupMethod(GroupMethod.GROUP_VCARDS);

                // enable contact sync
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
                settings.setSyncInterval(ContactsContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL);
            }

            if (config.calDAV != null) {
                // insert CalDAV service
                long id = insertService(db, config, Services.SERVICE_CALDAV);

                // start CalDAV service detection (refresh collections)
                refreshIntent.putExtra(DavService.EXTRA_DAV_SERVICE_ID, id);
                getActivity().startService(refreshIntent);

                // enable calendar sync
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1);
                settings.setSyncInterval(CalendarContract.AUTHORITY, Constants.DEFAULT_SYNC_INTERVAL);

                // enable task sync if OpenTasks is installed
                // further changes will be handled by PackageChangedReceiver
//                if (LocalTaskList.tasksProviderAvailable(getContext())) {
//                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.authority, 1);
//                    settings.setSyncInterval(TaskProvider.ProviderName.OpenTasks.authority, Constants.DEFAULT_SYNC_INTERVAL);
//                }
            }

        } catch(InvalidAccountException e) {
            App.log.log(Level.SEVERE, "Couldn't access account settings", e);
        }

        Intent intent = new Intent(Constants.ACTION_SYNC_SETTINGS);
        intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT,
                Constants.CLASS_ACCOUNT_SYNC_SETTINGS);
        intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, true);
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.EXTRA_KEY_ACCOUNT, account);
        intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle);
        startActivity(intent);
        getActivity().finish();
    }

    protected long insertService(SQLiteDatabase db, DavResourceFinder.Configuration config, String service) {
        final DavResourceFinder.Configuration.ServiceInfo info;
        if (Services.SERVICE_CARDDAV.equals(service)) {
            info = config.cardDAV;
        } else {
            info = config.calDAV;
        }
        ContentValues values = new ContentValues();

        // insert service
        values.put(Services.ACCOUNT_NAME, config.userName);
        values.put(Services.ACCOUNT_TYPE, config.accountType);
        values.put(Services.SERVICE, service);
        if (info.principal != null)
            values.put(Services.PRINCIPAL, info.principal.toString());
        long serviceID = db.insertWithOnConflict(Services._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // insert home sets
        for (URI homeSet : info.homeSets) {
            values.clear();
            values.put(HomeSets.SERVICE_ID, serviceID);
            values.put(HomeSets.URL, homeSet.toString());
            db.insertWithOnConflict(HomeSets._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        // insert collections
        for (CollectionInfo collection : info.collections.values()) {
            values = collection.toDB();
            values.put(Collections.SERVICE_ID, serviceID);
            db.insertWithOnConflict(Collections._TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        return serviceID;
    }

    public static class NothingDetectedFragment extends DialogFragment {

        public static NothingDetectedFragment newInstance(Configuration config) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_LOGIN_CREDENTIALS, config.credentials);
            NothingDetectedFragment fragment = new NothingDetectedFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final LoginCredentials credentials = (LoginCredentials) getArguments().getParcelable(ARG_LOGIN_CREDENTIALS);
            Log.i(TAG, "Login error: " + credentials.getException().getMessage());
            int message = -1;
            if (credentials.getException() instanceof UnknownHostException) {
                message = R.string.setup_login_network_error;
            } else if (Constants.MI_ACCOUNT_TYPE.equals(credentials.accountType)) {
                message = R.string.setup_login_mi_info_error;
            } else if (credentials.getException() instanceof UnauthorizedException) {
                int count = 0;
                if (mRetryTimes.containsKey(credentials.userName)) {
                    count = mRetryTimes.get(credentials.userName);
                }
                if ((++count) >= MAX_RETRY_TIMES) {
                    message = R.string.setup_login_icloud_info_error_for_two_step;
                } else {
                    message = R.string.setup_login_icloud_info_error;
                }
                mRetryTimes.put(credentials.userName, count);
            } else {
                message = R.string.setup_login_icloud_info_error;
            }

            final View view = View.inflate(getContext(), R.layout.alert_dialog, null);
            final TextView textView = (TextView) view.findViewById(R.id.message);
            textView.setText(message);
            textView.setMovementMethod(LinkMovementMethod.getInstance());

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.setup_login_failed)
                    .setView(view)
                    .setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // dismiss
                        }
                    })
                    .setPositiveButton(R.string.setup_dialog_retry, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getFragmentManager().beginTransaction()
                                    .add(DetectConfigurationFragment.newInstance(credentials), null)
                                    .commitAllowingStateLoss();
                        }
                    }).create();
        }
    }

    static class ServerConfigurationLoader extends AsyncTaskLoader<Configuration> {
        final Context context;
        final LoginCredentials credentials;

        public ServerConfigurationLoader(Context context, LoginCredentials credentials) {
            super(context);
            this.context = context;
            this.credentials = credentials;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public Configuration loadInBackground() {
            return new DavResourceFinder(context, credentials).findInitialConfiguration();
        }
    }

}
