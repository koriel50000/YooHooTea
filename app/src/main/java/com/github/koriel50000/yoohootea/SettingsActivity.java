package com.github.koriel50000.yoohootea;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    public static final String TAG = SettingsActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            Log.i(TAG, "onPreferenceTreeClick key=" + preference.getKey());

            SharedPreferences sharedPreferences = preference.getSharedPreferences();
            switch (preference.getKey()) {
                case "twitter_login":
                    getAccessToken(sharedPreferences);
                    break;
                case "twitter_logout":
                    removeAccessToken(sharedPreferences);
                    break;
            }
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.i(TAG, "onSharedPreferenceChanged key=" + key);
        }

        private void getAccessToken(SharedPreferences sharedPreferences) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("twitter_login", "accessToken");
            editor.commit();

            Preference loginTwitter = (Preference) findPreference("twitter_login");
            loginTwitter.setSummary("@userId");

            Preference logoutTwitter = (Preference) findPreference("twitter_logout");
            logoutTwitter.setEnabled(true);
        }

        private void removeAccessToken(SharedPreferences sharedPreferences) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("login_twitter", "");
            editor.commit();

            Preference loginTwitter = (Preference) findPreference("twitter_login");
            loginTwitter.setSummary("");

            Preference logoutTwitter = (Preference) findPreference("twitter_logout");
            logoutTwitter.setEnabled(false);
        }
    }
}