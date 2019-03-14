/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.settings;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.provider.CalendarContract;
import android.provider.ContactsContract;

import java.io.File;
import java.util.logging.Level;

import com.zui.davdroid.R;
import com.zui.davdroid.RefreshCollections;
import com.zui.davdroid.common.LoggingReader;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.log.ExternalFileLogger;
import at.bitfire.davdroid.AccountSettings;
import at.bitfire.ical4android.TaskProvider;

public class AccountFragment extends PreferenceFragment {
    private static final String KEY_USERNAME = "username";
	private static final String KEY_PASSWORD = "password";
	private static final String KEY_SYNC_INTERVAL_CONTACTS ="sync_interval_contacts";
	private static final String KEY_DEBUGGING = "debugging";
	private static final String KEY_LOG_EXTERNAL_FILE = "log_external_file";
	private static final String KEY_LOG_VERBOSE = "log_verbose";
	private static final String KEY_FUNCTION_TEST = "function_test";
	private static final String KEY_FUNCTION_SYNC_TEST = "function_sync_test";
	private static final String KEY_SUB_SETTINGS_TEST = "sub_settings_test";

	Account account;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings_account_prefs);
		account = getActivity().getIntent().getParcelableExtra(Constants.EXTRA_KEY_ACCOUNT);
		refresh();
	}

	public void refresh() {
		final AccountSettings settings;

		try {
			settings = new AccountSettings(getActivity(), account);
		} catch(InvalidAccountException e) {
			App.log.log(Level.INFO, "Account is invalid or doesn't exist (anymore)", e);
			getActivity().finish();
			return;
		}

		// category: authentication
		final EditTextPreference prefUserName = (EditTextPreference)findPreference(KEY_USERNAME);
		prefUserName.setSummary(settings.username());
		prefUserName.setText(settings.username());
		prefUserName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.username((String) newValue);
                refresh(); return false;
			}
		});

		final EditTextPreference prefPassword = (EditTextPreference)findPreference(KEY_PASSWORD);
		prefPassword.setText(settings.password());
		prefPassword.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				settings.password((String) newValue);
                refresh(); return false;
			}
		});

		// category: synchronization
		final ListPreference prefSyncContacts = (ListPreference)findPreference(KEY_SYNC_INTERVAL_CONTACTS);
		final Long syncIntervalContacts = settings.getSyncInterval(ContactsContract.AUTHORITY);
		if (syncIntervalContacts != null) {
			prefSyncContacts.setValue(syncIntervalContacts.toString());
			if (syncIntervalContacts == AccountSettings.SYNC_INTERVAL_MANUALLY)
				prefSyncContacts.setSummary(R.string.settings_sync_summary_manually);
			else
				prefSyncContacts.setSummary(getString(R.string.settings_sync_summary_periodically, syncIntervalContacts / 60));
			prefSyncContacts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					settings.setSyncInterval(ContactsContract.AUTHORITY, Long.parseLong((String) newValue));
                    refresh(); return false;
				}
			});
		} else {
			prefSyncContacts.setEnabled(false);
			prefSyncContacts.setSummary(R.string.settings_sync_summary_not_available);
		}

		if (LoggingReader.DEBUGGING) {
			// category: debug info
			final SwitchPreference prefLogExternalFile = (SwitchPreference) findPreference
					(KEY_LOG_EXTERNAL_FILE);

			prefLogExternalFile.setChecked(settings.logToExternalFile());
			File logDirectory = ExternalFileLogger.getDirectory(getActivity());
			prefLogExternalFile.setSummaryOn(logDirectory != null ?
							getString(R.string.settings_log_to_external_file_on, logDirectory
									.getPath()) :
							getString(R.string.settings_log_to_external_file_no_external_storage)
			);
			prefLogExternalFile.setOnPreferenceChangeListener(new Preference
					.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Boolean external = (Boolean) newValue;
					if (external) {
						getFragmentManager().beginTransaction()
								.add(LogExternalFileDialogFragment.newInstance(account), null)
								.commit();
						return false;
					} else {
						settings.logToExternalFile(false);
						refresh();
						return false;
					}
				}
			});

			final SwitchPreference prefLogVerbose = (SwitchPreference) findPreference
					(KEY_LOG_VERBOSE);
			prefLogVerbose.setChecked(settings.logVerbose());
			prefLogVerbose.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener
					() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					settings.logVerbose((Boolean) newValue);
					refresh();
					return false;
				}
			});
		} else {
			final Preference preference = findPreference(KEY_DEBUGGING);
			if (null != preference) {
				getPreferenceScreen().removePreference(preference);
			}
		}

		if (App.FUNCTION_TEST) {
			Preference preference = findPreference(KEY_FUNCTION_SYNC_TEST);
			preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					String authorities[] = {
							ContactsContract.AUTHORITY,
							CalendarContract.AUTHORITY,
							TaskProvider.ProviderName.OpenTasks.authority
					};

					for (String authority : authorities) {
						Bundle extras = new Bundle();
						extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);        // manual sync
						extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);     // run immediately (don't queue)
						extras.putBoolean(RefreshCollections.KEY_SYNC_COLLECTION, false);
						ContentResolver.requestSync(account, authority, extras);
					}
					return true;
				}
			});
			preference = findPreference(KEY_SUB_SETTINGS_TEST);
			preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					final Intent intent = new Intent(Constants.ACTION_SYNC_SETTINGS);
					intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT,
							Constants.CLASS_ACCOUNT_SYNC_SETTINGS);
					intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT_AS_SUBSETTING, true);
					final Bundle args = new Bundle();
					args.putParcelable(Constants.EXTRA_KEY_ACCOUNT, account);
					intent.putExtra(Constants.EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
					startActivity(intent);
					return true;
				}
			});
		} else {
			Preference preference = findPreference(KEY_FUNCTION_TEST);
			if (null != preference) {
				getPreferenceScreen().removePreference(preference);
			}
			preference = findPreference(KEY_SUB_SETTINGS_TEST);
			if (null != preference) {
				getPreferenceScreen().removePreference(preference);
			}
		}
	}

	public static class LogExternalFileDialogFragment extends DialogFragment {

        public static LogExternalFileDialogFragment newInstance(Account account) {
            Bundle args = new Bundle();
            args.putParcelable(Constants.EXTRA_KEY_ACCOUNT, account);
            LogExternalFileDialogFragment fragment = new LogExternalFileDialogFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			final AccountSettings settings;

			try {
				settings = new AccountSettings(getActivity(), (Account)getArguments().getParcelable(Constants.EXTRA_KEY_ACCOUNT));
			} catch(InvalidAccountException e) {
				App.log.log(Level.INFO, "Account is invalid or doesn't exist (anymore)", e);
				getActivity().finish();
				return null;
			}

            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.settings_security_warning)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.settings_log_to_external_file_confirmation)
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            settings.logToExternalFile(false);
                            refresh();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            settings.logToExternalFile(true);
                            refresh();
                        }
                    })
                    .create();
        }

        private void refresh() {
            AccountFragment fragment = (AccountFragment)getActivity().getFragmentManager().findFragmentByTag(AccountActivity.TAG_ACCOUNT_SETTINGS);
            if (fragment != null)
                fragment.refresh();
        }
    }

}
