package com.github.koriel50000.yoohootea;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpUtils {

    public static final String TAG = HttpUtils.class.getName();

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static OkHttpClient client;

    static {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public static void requestToHello(String name) {
        try {
            JsonRequest jsonRequest = new JsonRequest.Builder()
                    .api("hello")
                    .addProperty("name", name)
                    .build();

            SyncCall syncCall = new SyncCall(client);
            JsonResponse jsonResponse = syncCall.call(jsonRequest);

            String message = jsonResponse.getString("message");
            Log.d(TAG, "message: " + message);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static String requestToRegister(final long userId) {
        try {
            JsonRequest jsonRequest = new JsonRequest.Builder()
                    .api("register")
                    .addProperty("id_str", userId)
                    .build();

            SyncCall syncCall = new SyncCall(client);
            JsonResponse jsonResponse = syncCall.call(jsonRequest);

            boolean result = jsonResponse.getBoolean("result");
            String url = jsonResponse.getString("profile_image_url");
            return result ? url : "";
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return "";
        }
    }

    public static boolean requestToUnregister(final long userId) {
        try {
            JsonRequest jsonRequest = new JsonRequest.Builder()
                    .api("unregister")
                    .addProperty("id_str", userId)
                    .build();

            SyncCall syncCall = new SyncCall(client);
            JsonResponse jsonResponse = syncCall.call(jsonRequest);

            boolean result = jsonResponse.getBoolean("result");
            return result;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
    }

    public static boolean requestToRetweet(final long tweetId) {
        try {
            JsonRequest jsonRequest = new JsonRequest.Builder()
                    .api("retweet")
                    .addProperty("id_str", tweetId)
                    .build();

            SyncCall syncCall = new SyncCall(client);
            JsonResponse jsonResponse = syncCall.call(jsonRequest);

            boolean result = jsonResponse.getBoolean("result");
            return result;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
    }

    public static boolean requestToReview(final String speech) {
        try {
            JsonRequest jsonRequest = new JsonRequest.Builder()
                    .api("review")
                    .addProperty("text", speech)
                    .build();

            SyncCall syncCall = new SyncCall(client);
            JsonResponse jsonResponse = syncCall.call(jsonRequest);

            boolean result = jsonResponse.getBoolean("result");
            return result;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            return false;
        }
    }

    private static class SyncCall extends AsyncTask<Void, Void, Void> implements Callback {

        private OkHttpClient client;
        private CountDownLatch latch;

        private JsonRequest jsonRequest;
        private volatile Object result;

        SyncCall(OkHttpClient client) {
            this.client = client;
            latch = new CountDownLatch(1);
        }

        public JsonResponse call(JsonRequest jsonRequest) throws Exception {
            this.jsonRequest = jsonRequest;
            execute();

            boolean success = latch.await(20, TimeUnit.SECONDS);
            if (!success) {
                throw new TimeoutException("timed out.");
            }

            if (result instanceof JsonResponse) {
                return (JsonResponse) result;
            } else {
                throw (Exception) result;
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                client.newCall(jsonRequest.request()).enqueue(this);
            } catch (Exception e) {
                result = e;
                latch.countDown();
            }
            return null;
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            result = new JsonResponse(response.body().string());
            latch.countDown();
        }

        @Override
        public void onFailure(Call call, IOException e) {
            result = e;
            latch.countDown();
        }
    }

    private static class JsonRequest {

        private static String credential = Credentials.basic(
                Constants.BASIC_AUTH_USERNAME,
                Constants.BASIC_AUTH_PASSWORD);

        private String api;
        private JsonObject jsonObject;

        private JsonRequest(Builder builder) {
            this.api = builder.api;
            this.jsonObject = builder.jsonObject;
        }

        Request request() {
            Request request = new Request.Builder()
                    .header("Authorization", credential)
                    .url(Constants.MEDIATOR_API_URL + "/" + api)
                    .post(RequestBody.create(JSON, new Gson().toJson(jsonObject)))
                    .build();
            Log.d(TAG, "request: " + request.toString());
            return request;
        }

        static class Builder {

            private String api;
            private JsonObject jsonObject;

            Builder() {
                this.jsonObject = new JsonObject();
            }

            Builder api(String api) {
                this.api = api;
                return this;
            }

            Builder addProperty(String property, String value) {
                jsonObject.addProperty(property, value);
                return this;
            }

            Builder addProperty(String property, long value) {
                jsonObject.addProperty(property, String.valueOf(value));
                return this;
            }

            Builder addProperty(String property, boolean value) {
                jsonObject.addProperty(property, String.valueOf(value));
                return this;
            }

            JsonRequest build() {
                return new JsonRequest(this);
            }
        }
    }

    private static class JsonResponse {

        private JsonObject jsonObject;

        JsonResponse(String body) {
            Log.d(TAG, "response: " + body);
            jsonObject = new Gson().fromJson(body, JsonElement.class).getAsJsonObject();
        }

        String getString(String property) {
            return jsonObject.get(property).getAsString();
        }

        long getLong(String property) {
            return jsonObject.get(property).getAsLong();
        }

        boolean getBoolean(String property) {
            return jsonObject.get(property).getAsBoolean();
        }
    }
}
