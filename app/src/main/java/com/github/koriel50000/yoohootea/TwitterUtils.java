package com.github.koriel50000.yoohootea;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

public class TwitterUtils {

    private static AccessToken accessToken; // FIXME twitterを保持したいが…
    private static String screenName;

    public static Twitter getInstance() {
        Twitter twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(
                Constants.TWITTER_CONSUMER_KEY,
                Constants.TWITTER_CONSUMER_SECRET);
        if (accessToken != null) {
            twitter.setOAuthAccessToken(accessToken);
        }
        return twitter;
    }

    public static TwitterStream getStreamInstance() {
        TwitterStream twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.setOAuthConsumer(
                Constants.TWITTER_CONSUMER_KEY,
                Constants.TWITTER_CONSUMER_KEY);
        if (accessToken != null) {
            twitterStream.setOAuthAccessToken(accessToken);
        }
        return twitterStream;
    }

    public static void initialize(String token, String tokenSecret,
                                  long userId, String initScreenName)
    {
        accessToken = new AccessToken(token, tokenSecret, userId);
        screenName = initScreenName;
    }

    public static long getUserId() {
        return accessToken.getUserId();
    }

    public static String getScreenName() {
        return screenName;
    }

    public static OAuthTask createOAuthTask(OAuthListener listener) {
        return new OAuthTask(listener);
    }

    public static class OAuthTask {

        public static final String TAG = OAuthTask.class.getName();

        private OAuthListener listener;
        private Twitter twitter;

        private OAuthTask(OAuthListener listener) {
            this.listener = listener;
            twitter = TwitterUtils.getInstance();
        }

        public void asyncRequestToken(String callbackURL) {
            Log.d(TAG, "callbackURL: " + callbackURL);
            new AsyncTask<String, Void, RequestToken>() {
                @Override
                protected RequestToken doInBackground(String... params) {
                    try {
                        RequestToken requestToken = twitter.getOAuthRequestToken(params[0]);
                        Log.d(TAG, "requetToken: " + requestToken.getToken());
                        Log.d(TAG, "requetTokenSecret: " + requestToken.getTokenSecret());
                        return requestToken;
                    } catch (TwitterException e) {
                        listener.onRequestTokenError(e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(RequestToken requestToken) {
                    if (requestToken != null) {
                        String authenticationURL = requestToken.getAuthenticationURL();
                        Log.d(TAG, "authenticationURL: " + authenticationURL);
                        if (authenticationURL != null) {
                            listener.onRequestTokenResult(authenticationURL);
                        } else {
                            listener.onRequestTokenError(new IllegalArgumentException("authenticationURL is null."));
                        }
                    }
                }
            }.execute(callbackURL);
        }

        public void asyncAccessToken(Intent intent) {
            String verifier = intent.getData().getQueryParameter("oauth_verifier");
            Log.d(TAG, "oauth_verifier: " + verifier);
            new AsyncTask<String, Void, AccessToken>() {
                @Override
                protected AccessToken doInBackground(String... params) {
                    try {
                        AccessToken accessToken = twitter.getOAuthAccessToken(params[0]);
                        Log.d(TAG, "accessToken: " + accessToken.getToken());
                        Log.d(TAG, "accessTokenSecret: " + accessToken.getTokenSecret());
                        return accessToken;
                    } catch (TwitterException e) {
                        listener.onRequestTokenError(e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(AccessToken accessToken) {
                    if (accessToken != null) {
                        listener.onAccessTokenResult(accessToken);
                    }
                }
            }.execute(verifier);
        }
    }

    public interface OAuthListener {

        void onRequestTokenResult(String url);

        void onRequestTokenError(Throwable cause);

        void onAccessTokenResult(AccessToken accessToken);

        void onAccessTokenError(Throwable cause);
    }

    public static class OAuthAdapter implements OAuthListener {
        @Override
        public void onRequestTokenResult(String url) {
        }

        @Override
        public void onRequestTokenError(Throwable cause) {
        }

        @Override
        public void onAccessTokenResult(AccessToken accessToken) {
        }

        @Override
        public void onAccessTokenError(Throwable cause) {
        }
    }
}
