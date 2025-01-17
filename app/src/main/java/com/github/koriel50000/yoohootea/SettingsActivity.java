package com.github.koriel50000.yoohootea;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import twitter4j.auth.AccessToken;

public class SettingsActivity extends AppCompatActivity implements TwitterUtils.OAuthListener {

    public static final String TAG = SettingsActivity.class.getName();

    private TwitterUtils.OAuthTask oauthTask;
    private TwitterUtils.OAuthListener oauthCallback;

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

    public void startOAuth(TwitterUtils.OAuthListener callback) {
        oauthCallback = callback;
        oauthTask = TwitterUtils.createOAuthTask(this);

        String callbackURL = "example://authorize";
        oauthTask.asyncRequestToken(callbackURL);
    }

    public void onRequestTokenResult(String authenticationURL) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authenticationURL));
        startActivity(intent);
    }

    public void onRequestTokenError(Throwable cause) {
        Log.e(TAG, cause.getMessage(), cause);
        oauthCallback = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        oauthTask.asyncAccessToken(intent);
    }

    public void onAccessTokenResult(AccessToken accessToken) {
        if (oauthCallback != null) {
            oauthCallback.onAccessTokenResult(accessToken);
        }
        oauthCallback = null;
    }

    public void onAccessTokenError(Throwable cause) {
        Log.e(TAG, cause.getMessage(), cause);
        oauthCallback = null;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);

            String loginName = sharedPreferences.getString("twitter_login", "");
            toggleAccountPreference(loginName, !"".equals(loginName));
        }

        @Override
        public void onPause() {
            super.onPause();
            SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
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

        private void getAccessToken(final SharedPreferences sharedPreferences) {
            SettingsActivity settingsActivity = (SettingsActivity)getActivity();
            settingsActivity.startOAuth(new TwitterUtils.OAuthAdapter() {
                @Override
                public void onAccessTokenResult(AccessToken accessToken) {
                    String token = accessToken.getToken();
                    String tokenSecret = accessToken.getTokenSecret();
                    long userId = accessToken.getUserId();
                    String screenName = accessToken.getScreenName();
                    String loginName = "@" + screenName;

                    String imageURL = HttpUtils.requestToRegister(userId);

                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("twitter_login", loginName);
                    editor.putString("oauth_token", token);
                    editor.putString("oauth_token_secret", tokenSecret);
                    editor.putLong("user_id", userId);
                    editor.putString("screen_name", screenName);
                    editor.putLong("timeline_since_id", 1);
                    editor.putString("profile_image_url", imageURL);
                    editor.commit();

                    TwitterUtils.initialize(token, tokenSecret, userId, screenName, imageURL);

                    toggleAccountPreference(loginName, true);
                }
            });
        }

        private void removeAccessToken(SharedPreferences sharedPreferences) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove("twitter_login");
            editor.remove("oauth_token");
            editor.remove("oauth_token_secret");
            editor.remove("user_id");
            editor.remove("screen_name");
            editor.remove("timeline_since_id");
            editor.remove("profile_image_url");
            editor.commit();

            long userId = TwitterUtils.getUserId();
            HttpUtils.requestToUnregister(userId);

            TwitterUtils.initialize("", "", 0, "", "");

            toggleAccountPreference("", false);
        }

        private void toggleAccountPreference(String loginName, boolean enabled) {
            Preference loginTwitter = (Preference) findPreference("twitter_login");
            loginTwitter.setSummary(loginName);

            Preference logoutTwitter = (Preference) findPreference("twitter_logout");
            logoutTwitter.setEnabled(enabled);
        }
    }
}