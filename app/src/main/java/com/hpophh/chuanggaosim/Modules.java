package com.hpophh.chuanggaosim;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class Modules {
    static final String PREFS_NAME = "LoginPrefs";

    private Modules() {
    }

    static class LoginPrefs {
        private static final String KEY_COOKIES = "cookies";
        private static final String KEY_JSESSION_ID = "jsessionId";
        private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
        private static final String KEY_JWT = "jwt";
        private static final String KEY_SECRET = "secret";

        private final Context context;

        LoginPrefs(Context context) {
            this.context = context.getApplicationContext();
        }

        private SharedPreferences prefs() {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        void saveSession(String cookies, String jsessionId) {
            prefs().edit()
                    .putString(KEY_COOKIES, cookies)
                    .putString(KEY_JSESSION_ID, jsessionId)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply();
        }

        String getSavedJSessionId() {
            return prefs().getString(KEY_JSESSION_ID, null);
        }

        String getSavedCookies() {
            return prefs().getString(KEY_COOKIES, "");
        }

        boolean isLoggedInFlag() {
            return prefs().getBoolean(KEY_IS_LOGGED_IN, false);
        }

        void setLoggedIn(boolean isLoggedIn) {
            prefs().edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply();
        }

        void saveUserBody(login.UserBody userBody) {
            prefs().edit()
                    .putString(KEY_JWT, userBody.jwt)
                    .putString(KEY_SECRET, userBody.secret)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply();
        }

        login.UserBody loadUserBody() {
            String jwt = prefs().getString(KEY_JWT, null);
            String secret = prefs().getString(KEY_SECRET, null);
            if (jwt == null || secret == null) {
                return null;
            }

            login.UserBody userBody = new login.UserBody();
            userBody.jwt = jwt;
            userBody.secret = secret;
            return userBody;
        }

        void clearAuth() {
            prefs().edit()
                    .remove(KEY_COOKIES)
                    .remove(KEY_JSESSION_ID)
                    .remove(KEY_JWT)
                    .remove(KEY_SECRET)
                    .putBoolean(KEY_IS_LOGGED_IN, false)
                    .apply();
        }
    }

    static class LoginParser {
        private static final String TAG = "login";

        static String extractJSessionId(String cookies) {
            if (cookies == null || cookies.isEmpty()) {
                return null;
            }

            String[] cookieArray = cookies.split(";");
            for (String cookie : cookieArray) {
                String trimmed = cookie.trim();
                if (trimmed.startsWith("JSESSIONID=")) {
                    return trimmed.substring("JSESSIONID=".length());
                }
            }

            return null;
        }

        static String extractJwtToken(String html) {
            if (html == null || html.isEmpty()) {
                Log.e(TAG, "GetJwtToken failed: empty HTML");
                return null;
            }

            String marker = "'jwtToken': '";
            int start = html.indexOf(marker);
            if (start == -1) {
                Log.e(TAG, "GetJwtToken failed: jwtToken marker not found");
                return null;
            }
            start += marker.length();

            int end = html.indexOf("'", start);
            if (end == -1) {
                Log.e(TAG, "GetJwtToken failed: closing quote not found");
                return null;
            }

            String jwtToken = html.substring(start, end);
            Log.d(TAG, "Extracted jwtToken: " + jwtToken);
            return jwtToken;
        }

        static login.UserBody buildUserBody(String jwtToken) {
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                Log.e(TAG, "buildUserBody failed: empty jwtToken");
                return null;
            }

            String[] parts = jwtToken.trim().split("\\.");
            if (parts.length < 5) {
                Log.e(TAG, "buildUserBody failed: invalid jwtToken format");
                return null;
            }

            login.UserBody userBody = new login.UserBody();
            userBody.jwt = parts[0] + "." + parts[1] + "." + parts[2];
            userBody.secret = parts[3];

            if (userBody.jwt.isEmpty() || userBody.secret.isEmpty()) {
                Log.e(TAG, "buildUserBody failed: jwt or secret is empty");
                return null;
            }

            Log.d(TAG, "buildUserBody success");
            return userBody;
        }

        static boolean isJwtExpired(String jwt) {
            return isJwtExpired(jwt, System.currentTimeMillis());
        }

        static boolean isJwtExpired(String jwt, long nowMillis) {
            Long expSeconds = extractJwtExpSeconds(jwt);
            return expSeconds == null || nowMillis >= expSeconds * 1000L;
        }

        static Long extractJwtExpSeconds(String jwt) {
            if (jwt == null || jwt.trim().isEmpty()) {
                return null;
            }

            String[] parts = jwt.trim().split("\\.");
            if (parts.length < 2) {
                Log.e(TAG, "extractJwtExpSeconds failed: invalid jwt format");
                return null;
            }

            try {
                byte[] decoded = Base64.decode(normalizeBase64Url(parts[1]), Base64.URL_SAFE);
                String payload = new String(decoded, StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(payload);
                if (!jsonObject.has("exp")) {
                    Log.e(TAG, "extractJwtExpSeconds failed: exp not found");
                    return null;
                }

                long exp = jsonObject.optLong("exp", 0L);
                return exp > 0L ? exp : null;
            } catch (Exception e) {
                Log.e(TAG, "extractJwtExpSeconds failed", e);
                return null;
            }
        }

        private static String normalizeBase64Url(String value) {
            int padding = value.length() % 4;
            if (padding == 0) {
                return value;
            }
            StringBuilder builder = new StringBuilder(value);
            for (int i = padding; i < 4; i++) {
                builder.append('=');
            }
            return builder.toString();
        }
    }

    static class LoginHttp {
        private static final String TAG = "login";

        static String getLoginData(String callbackUrl, String jsessionId) {
            if (jsessionId == null || jsessionId.trim().isEmpty()) {
                Log.e(TAG, "getLoginData failed: jsessionId is empty");
                return null;
            }

            HttpURLConnection connection = null;
            try {
                URL url = new URL(callbackUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Cookie", "JSESSIONID=" + jsessionId.trim());
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                connection.setInstanceFollowRedirects(true);

                int code = connection.getResponseCode();
                InputStream inputStream = (code >= 200 && code < 400)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                if (inputStream == null) {
                    Log.e(TAG, "getLoginData failed: empty response stream, code=" + code);
                    return null;
                }

                StringBuilder html = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        html.append(line).append('\n');
                    }
                }

                return html.toString();
            } catch (Exception e) {
                Log.e(TAG, "getLoginData error", e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    static class WebViewConfig {
        interface PageFinishedListener {
            void onPageFinished(String url);
        }

        static void configure(WebView webView, PageFinishedListener listener) {
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setDatabaseEnabled(true);
            webSettings.setSupportZoom(true);
            webSettings.setBuiltInZoomControls(true);
            webSettings.setDisplayZoomControls(false);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setUseWideViewPort(true);
            webSettings.setAllowFileAccess(true);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            // Remote debugging for chrome://inspect during CTF device testing.
            WebView.setWebContentsDebuggingEnabled(true);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    view.loadUrl(url);
                    return true;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (listener != null) {
                        listener.onPageFinished(url);
                    }
                }
            });
        }
    }
}


