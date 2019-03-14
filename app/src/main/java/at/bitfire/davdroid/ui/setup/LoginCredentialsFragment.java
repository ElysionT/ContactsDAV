/*
 * Copyright © 2015 – 2016 ZUK.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.ui.setup;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.zui.davdroid.R;

import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.Constants;
import at.bitfire.davdroid.Constants.AccountType;

public class LoginCredentialsFragment extends Fragment implements View.OnClickListener, TextWatcher {
    private static final String TAG = LoginCredentialsFragment.class.getSimpleName();

    private EditText mHostPath;
    private EditText mUserName;
    private EditText mPassword;
    private EditText mDescription;
    private Button mShowPassword;
    private Button mLogin;

    private AccountType mType;
    private String mAccountType;
    private boolean isShowPassword = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAccountType = getActivity().getIntent().getStringExtra(Constants.EXTRA_ACCOUNT_TYPE);
        switch(mAccountType){
            case Constants.DEFAULT_ACCOUNT_TYPE:
                mType =  AccountType.DEFAULT;
                break;
            case Constants.ICLOUD_ACCOUNT_TYPE:
                mType =  AccountType.ICLOUD;
                break;
            case Constants.MI_ACCOUNT_TYPE:
                mType =  AccountType.MI;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported acccount type: " +
                        mAccountType);

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        View view = inflater.inflate(R.layout.setup_login_account, container, false);

        View layout;
        if (AccountType.ICLOUD == mType) {
            layout = view.findViewById(R.id.login_icloud);
            mUserName = (EditText) layout.findViewById(R.id.apple_id);
            mPassword = (EditText) layout.findViewById(R.id.icloud_password);
        } else if (AccountType.MI == mType) {
            layout = view.findViewById(R.id.login_mi);
            mUserName = (EditText) layout.findViewById(R.id.mi_id);
            mPassword = (EditText) layout.findViewById(R.id.mi_password);
        } else {
            layout = view.findViewById(R.id.login_url);
            mHostPath = (EditText) layout.findViewById(R.id.login_host_path);
            mHostPath.addTextChangedListener(this);
            mUserName = (EditText) layout.findViewById(R.id.userName);
            mPassword = (EditText) layout.findViewById(R.id.password);
            mDescription = (EditText) layout.findViewById(R.id.description);
        }
        mUserName.addTextChangedListener(this);
        mPassword.addTextChangedListener(this);
        mShowPassword = (Button) layout.findViewById(R.id.show_password);
        mShowPassword.setOnClickListener(this);
        mLogin = (Button) view.findViewById(R.id.login);
        mLogin.setOnClickListener(this);

        layout.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final View customView = getActivity().getLayoutInflater().inflate(R.layout.actionbar_title,
                null, false);
        int title;
        switch(mType){
            case ICLOUD:
                title = R.string.icloud_account_name;
                break;
            case MI:
                title = R.string.mi_account_name;
                break;
            default:
                title = R.string.default_carddev_account_name;
                break;
        }

        ((TextView)customView.findViewById(R.id.title)).setText(title);

        final ActionBar actionBar = getActivity().getActionBar();
        final ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ActionBar
                .LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        actionBar.setCustomView(customView, layoutParams);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.login:
                String host_path;
                switch(mType){
                    case ICLOUD:
                        host_path = Constants.ICLOUD_HOST_PATH;
                        break;
                    case MI:
                        host_path = Constants.MI_HOST_PATH;
                        break;
                    default:
                        host_path = mHostPath.getText().toString();
                        break;
                }
                URI baseURI = null;
                try {
                    if (!TextUtils.isEmpty(host_path)) {
                        baseURI = new URI(Constants.HTTPS_SCHEME + host_path);
                    }
                } catch (URISyntaxException e) {
                    Log.e(TAG, e.getMessage());
                }

                String userName = mUserName.getText().toString();
                String password = mPassword.getText().toString();

                LoginCredentials credentials = new LoginCredentials(baseURI, userName, password, mAccountType);
                DetectConfigurationFragment.newInstance(credentials).show(getFragmentManager(), null);
                break;
            case R.id.show_password:
                int index = mPassword.getSelectionEnd();
                if (isShowPassword) {
                    mShowPassword.setBackgroundResource(R.drawable.ic_show_password_disable);
                    mPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType
                            .TYPE_TEXT_VARIATION_PASSWORD);
                } else {
                    mShowPassword.setBackgroundResource(R.drawable.ic_show_password_enable);
                    mPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType
                            .TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

                }
                mPassword.setSelection(index);
                isShowPassword = !isShowPassword;

                break;
            default:
                break;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        boolean enable = checkStatus();
        mLogin.setEnabled(enable);
        mLogin.setBackgroundResource(enable ? R.drawable.bg_login : R.drawable.ic_login_disable);
        mLogin.setTextColor(getResources().getColor(enable ? R.color.color_primary_button_text :
                R.color.color_primary_button_text_disable));
        checkDebug();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    private boolean checkStatus() {
        if (AccountType.DEFAULT == mType) {
            String host = mHostPath.getText().toString();
            if (TextUtils.isEmpty(host)) {
                return false;
            }
        }
        String name = mUserName.getText().toString();
        if (TextUtils.isEmpty(name)) {
            return false;
        }

        String password = mPassword.getText().toString();
        if (TextUtils.isEmpty(password)) {
            return false;
        }

        return true;
    }

    private void checkDebug() {
        String debug = mUserName.getText().toString();
        if (Constants.OPEN_DEBUGGING_TEXT.equals(debug) && !App.debugging()) {
            App.setDebugging(true);
            Toast.makeText(getContext(), Constants.OPEN_DEBUGGING_TEXT, Toast.LENGTH_SHORT).show();
        } else if (Constants.CLOSE_DEBUGGING_TEXT.equals(debug) && App.debugging()) {
            App.setDebugging(false);
            Toast.makeText(getContext(), Constants.CLOSE_DEBUGGING_TEXT, Toast.LENGTH_SHORT).show();
        } else if (Constants.VERSION_TEXT.equals(debug)) {
            try {
                PackageManager manager = getActivity().getPackageManager();
                PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);
                Toast.makeText(getContext(), info.versionName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        } else if (Constants.FUNCTION_TEST.equals(debug)) {
            App.openFunctionTest();
            Toast.makeText(getContext(), Constants.FUNCTION_TEST, Toast.LENGTH_SHORT).show();
        }
    }
}
